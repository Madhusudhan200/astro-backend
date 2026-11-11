package com.kpjyotish.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpjyotish.ai.AiService;
import com.kpjyotish.ai.AiTypes.*;
import com.kpjyotish.ai.FallbackAstrologer;
import com.kpjyotish.config.ApiException;
import com.kpjyotish.config.Env;
import com.kpjyotish.kp.Chart;
import com.kpjyotish.kp.KpEngine;
import com.kpjyotish.pdf.PdfRenderer;
import com.kpjyotish.pdf.PdfFonts;
import com.kpjyotish.pdf.BlockTranslator;
import com.kpjyotish.i18n.Lang;
import com.kpjyotish.i18n.Translator;
import com.kpjyotish.pdf.ReportBuilder;
import com.kpjyotish.store.Entities.*;
import com.kpjyotish.store.Repos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Profiles, AI astrologer chat (with long-term memory per profile) and report generation. */
@Service
public class ConsultService {
    private static final Logger log = LoggerFactory.getLogger(ConsultService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final Path REPORT_DIR = Paths.get("data", "reports");
    public static final List<String> RELATIONS = List.of("Self", "Mother", "Father", "Spouse", "Son", "Daughter", "Brother", "Sister", "Friend", "Other");

    private final Repos db;
    private final AiService ai;
    private final FallbackAstrologer fallback;
    private final Notifier notify;
    private final Env env;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "report"); t.setDaemon(true); return t; });
    private final Map<String, Chart> chartCache = new ConcurrentHashMap<>();

    private final Translator translator;

    public ConsultService(Repos db, AiService ai, FallbackAstrologer fallback, Notifier notify, Env env, Translator translator) throws Exception {
        this.db = db; this.ai = ai; this.fallback = fallback; this.notify = notify; this.env = env; this.translator = translator;
        Files.createDirectories(REPORT_DIR);
    }

    // ------------------------------------------------------------------ profiles
    /** The account holder's own profile (created on first use from their registration details). */
    public Profile self(User u) {
        return db.profiles.findFirstByUserIdAndRelation(u.id, "Self").orElseGet(() -> {
            Profile p = new Profile();
            p.userId = u.id; p.relation = "Self"; p.fullName = u.fullName; p.dob = u.dob; p.tob = u.tob; p.gender = u.gender;
            p.city = u.city; p.lat = u.lat; p.lon = u.lon; p.tz = u.tz; p.tzName = u.tzName;
            return db.profiles.save(p);
        });
    }

    public List<Profile> profiles(User u) {
        self(u);
        return db.profiles.findByUserIdOrderByCreatedAtAsc(u.id);
    }

    public Profile profile(User u, String profileId) {
        if (profileId == null || profileId.isBlank() || profileId.equals("self")) return self(u);
        return db.profiles.findById(profileId).filter(p -> p.userId.equals(u.id)).orElseThrow(() -> new ApiException(404, "Profile not found"));
    }

    public Chart chart(Profile p) {
        return chartCache.computeIfAbsent(p.id + "|" + p.dob + "|" + p.tob + "|" + p.lat + "|" + p.lon + "|" + p.tz,
                k -> KpEngine.compute(p.dob, p.tob, p.lat, p.lon, p.tz));
    }

    public List<String> memory(Profile p) { return db.memories.findByProfileIdOrderByCreatedAtAsc(p.id).stream().map(m -> m.fact).toList(); }

    // ------------------------------------------------------------------ chat
    ChatSession session(Profile p) {
        return db.sessions.findByProfileIdAndSession(p.id, p.chatSession).orElseGet(() -> {
            ChatSession s = new ChatSession();
            s.userId = p.userId; s.profileId = p.id; s.session = p.chatSession;
            return db.sessions.save(s);
        });
    }

    /** Opens the chat; on a new consultation the astrologer connects, greets and the admin is notified with the client's details. */
    public synchronized Map<String, Object> open(User account, Profile p) {
        ChatSession s = session(p);
        if (db.messages.findByProfileIdAndSessionOrderByCreatedAtAsc(p.id, p.chatSession).isEmpty()) {
            Chart c = chart(p);
            ChatTurn g = fallback.greeting(p.asPerson(), c, memory(p));
            String text = g.reply();
            if (!"Self".equals(p.relation)) text = text.replaceFirst("Tell me — why are you consulting", "This consultation is for " + p.fullName + " (your " + p.relation.toLowerCase(Locale.ROOT) + "). Tell me — why are you consulting");
            save(p, "assistant", text);
            notify.chatStarted(account, p, c);
        }
        return state(p, s);
    }

    Map<String, Object> state(Profile p, ChatSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", p);
        m.put("session", p.chatSession);
        m.put("messages", db.messages.findByProfileIdAndSessionOrderByCreatedAtAsc(p.id, p.chatSession).stream()
                .map(x -> Map.of("role", x.role, "text", x.text, "createdAt", x.createdAt)).toList());
        m.put("memory", db.memories.findByProfileIdOrderByCreatedAtAsc(p.id).stream().map(x -> Map.of("id", x.id, "fact", x.fact)).toList());
        m.put("topic", s.topic == null ? "" : s.topic);
        m.put("readyForReport", s.readyForReport);
        m.put("reportId", s.reportId);
        m.put("userMessages", db.messages.countByProfileIdAndSessionAndRole(p.id, p.chatSession, "user"));
        m.put("aiMode", ai.enabled() ? "ai" : "rule-based");
        return m;
    }

    void save(Profile p, String role, String text) {
        ChatMessage m = new ChatMessage();
        m.userId = p.userId; m.profileId = p.id; m.session = p.chatSession; m.role = role; m.text = text;
        m.createdAt = Instant.now().toString();
        db.messages.save(m);
    }

    public Map<String, Object> send(User account, Profile p, String text) {
        if (text == null || text.isBlank()) throw new ApiException(400, "Please type a message");
        if (text.length() > 2000) text = text.substring(0, 2000);
        open(account, p);
        ChatSession s = session(p);
        save(p, "user", text.trim());
        List<ChatMessage> history = db.messages.findByProfileIdAndSessionOrderByCreatedAtAsc(p.id, p.chatSession);
        if (history.size() > 40) history = history.subList(history.size() - 40, history.size());
        FallbackAstrologer.State st = new FallbackAstrologer.State();
        st.topicKey = s.topicKey; st.topic = s.topic; st.stage = s.stage; st.ready = s.readyForReport;
        User person = p.asPerson();
        ChatTurn turn = ai.chat(person, chart(p), memory(p), history, st);
        save(p, "assistant", turn.reply() == null || turn.reply().isBlank() ? "I'm here — could you tell me a little more?" : turn.reply());
        if (turn.newMemories() != null) for (String f : turn.newMemories()) {
            String fact = f == null ? "" : f.trim();
            if (fact.length() < 3 || fact.length() > 300 || db.memories.existsByProfileIdAndFactIgnoreCase(p.id, fact)) continue;
            MemoryFact mf = new MemoryFact(); mf.userId = p.userId; mf.profileId = p.id; mf.fact = fact; db.memories.save(mf);
        }
        s.topicKey = st.topicKey; s.stage = st.stage;
        if (turn.topic() != null && !turn.topic().isBlank()) s.topic = turn.topic(); else if (st.topic != null) s.topic = st.topic;
        s.readyForReport = s.readyForReport || turn.readyForReport() || st.ready;
        db.sessions.save(s);
        return state(p, s);
    }

    /** Start a new consultation (a new reason) for this profile. */
    public Map<String, Object> newSession(User account, Profile p) {
        p.chatSession += 1;
        db.profiles.save(p);
        return open(account, p);
    }

    public void forget(User u, String memoryId) {
        db.memories.findById(memoryId).filter(m -> m.userId.equals(u.id)).ifPresent(db.memories::delete);
    }

    // ------------------------------------------------------------------ reports
    public Path fullPath(String reportId) { return REPORT_DIR.resolve(reportId + "-full.pdf"); }
    public Path previewPath(String reportId) { return REPORT_DIR.resolve(reportId + "-preview.pdf"); }

    /** One report per consultation (reason). A new reason = new consultation = new report. */
    public Report createReport(User account, Profile p, String language) {
        String lang = Lang.get(language) == null ? "en" : language;
        ChatSession s = session(p);
        if (s.reportId != null) {
            Optional<Report> existing = db.reports.findById(s.reportId);
            if (existing.isPresent()) return lang.equals(existing.get().language) ? existing.get() : translateReport(account, existing.get().id, lang);
        }
        if (db.messages.countByProfileIdAndSessionAndRole(p.id, p.chatSession, "user") == 0)
            throw new ApiException(400, "Please chat with the astrologer first — tell them why you are consulting, then generate the report.");
        Report r = new Report();
        r.userId = account.id; r.profileId = p.id; r.session = p.chatSession; r.language = lang;
        r.topic = s.topic == null || s.topic.isBlank() ? "General life reading" : s.topic;
        r.title = r.topic + " - " + p.fullName + ("Self".equals(p.relation) ? "" : " (" + p.relation + ")");
        r.status = "generating";
        r = db.reports.save(r);
        s.reportId = r.id;
        db.sessions.save(s);
        generate(r.id);
        return r;
    }

    /** Same report in another language (only the PDF language changes; unlocked if the original is). */
    public Report translateReport(User account, String reportId, String language) {
        Report src = db.reports.findById(reportId).filter(x -> x.userId.equals(account.id)).orElseThrow(() -> new ApiException(404, "Report not found"));
        Report root = src.parentId != null ? db.reports.findById(src.parentId).orElse(src) : src;
        Lang l = Lang.get(language);
        if (l == null) throw new ApiException(400, "Unknown language");
        if (language.equals(root.language)) return root;
        for (Report t : db.reports.findByParentId(root.id)) if (language.equals(t.language)) return t;
        Report r = new Report();
        r.userId = root.userId; r.profileId = root.profileId; r.session = root.session; r.topic = root.topic;
        r.title = root.title + " (" + l.name() + ")"; r.language = language; r.parentId = root.id; r.unlocked = root.unlocked; r.status = "generating";
        r = db.reports.save(r);
        generate(r.id);
        return r;
    }

    /** English report content + blocks for a report (computed once and stored). */
    record English(User account, Profile profile, User person, Chart chart, ReportContent content, String source, List<com.kpjyotish.pdf.Doc.Block> blocks) {}

    English english(Report r) throws Exception {
        User account = db.users.findById(r.userId).orElseThrow();
        Profile p = r.profileId == null ? self(account) : db.profiles.findById(r.profileId).orElseThrow();
        User person = p.asPerson();
        Chart c = chart(p);
        Report parent = r.parentId == null ? null : db.reports.findById(r.parentId).orElse(null);
        ReportContent content; String source;
        if (parent != null && parent.contentJson != null) { content = JSON.readValue(parent.contentJson, ReportContent.class); source = parent.source; }
        else if (r.contentJson != null) { content = JSON.readValue(r.contentJson, ReportContent.class); source = r.source; }
        else {
            List<ChatMessage> tx = db.messages.findByProfileIdAndSessionOrderByCreatedAtAsc(p.id, r.session);
            ReportResult rr = ai.report(person, c, memory(p), tx, r.topic);
            content = rr.content(); source = rr.source();
            r.contentJson = JSON.writeValueAsString(content); r.source = source;
            db.reports.save(r);
        }
        return new English(account, p, person, c, content, source, ReportBuilder.build(person, c, content, r.topic));
    }

    /** Texts the browser must translate (only those containing words). */
    public synchronized List<String> textsToTranslate(Report r) throws Exception {
        English e = english(r);
        Set<String> all = new LinkedHashSet<>(PdfRenderer.LABELS);
        all.addAll(BlockTranslator.texts(e.blocks(), e.content().hook()));
        return all.stream().filter(t -> t != null && t.matches("(?s).*[A-Za-z]{2,}.*")).toList();
    }

    /** Translations produced in the visitor's browser by the free website translator. */
    public void acceptTranslations(Report r, Map<String, String> map) throws Exception {
        if (map == null || map.isEmpty()) throw new ApiException(400, "No translations received");
        // Safeguard: never build a "translated" PDF that is still mostly English
        int expected = textsToTranslate(r).size();
        if (map.size() < expected * 0.5)
            throw new ApiException(400, "The translation did not finish (" + map.size() + " of " + expected + " parts). Please keep the page open — it will try again.");
        r.translationsJson = JSON.writeValueAsString(map);
        r.status = "generating"; r.error = null;
        db.reports.save(r);
        generate(r.id);
    }

    public void generate(String reportId) {
        pool.submit(() -> {
            Report r = db.reports.findById(reportId).orElse(null);
            if (r == null) return;
            try {
                String lang = r.language == null ? "en" : r.language;
                r.status = "generating"; r.error = null; db.reports.save(r);
                English e = english(r);
                User account = e.account(), person = e.person();
                Chart c = e.chart();
                String source = e.source();
                var blocks = e.blocks();
                Hook hook = e.content().hook();
                Map<String, String> tr = null;
                if (!lang.equals("en")) {
                    if (r.translationsJson != null) tr = JSON.readValue(r.translationsJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                    else if (translator.available()) {
                        Set<String> texts = new LinkedHashSet<>(PdfRenderer.LABELS);
                        texts.addAll(BlockTranslator.texts(blocks, hook));
                        tr = translator.translate(texts, lang);
                    }
                    else { r.status = "translating"; db.reports.save(r); return; } // the visitor's browser translates it
                    var res = BlockTranslator.apply(blocks, hook, tr);
                    blocks = res.blocks(); hook = res.hook();
                }
                PdfRenderer.Result full; byte[] preview;
                try {
                    PdfFonts.use(lang);
                    PdfRenderer.labels(tr);
                    full = PdfRenderer.full(person, c, hook, blocks, env.siteName());
                    preview = PdfRenderer.preview(person, c, hook, full.pages(), env.siteName());
                } finally { PdfFonts.reset(); PdfRenderer.labels(null); }
                Files.write(fullPath(r.id), full.pdf());
                Files.write(previewPath(r.id), preview);
                r.status = "ready"; r.pages = full.pages(); r.source = source;
                r.hookJson = JSON.writeValueAsString(hook);
                r.generatedAt = Instant.now().toString();
                db.reports.save(r);
                notify.reportReady(account, r);
                log.info("report {} ready: {} pages ({})", r.id, r.pages, r.source);
            } catch (Exception e) {
                log.error("report {} failed", reportId, e);
                r.status = "failed"; r.error = String.valueOf(e.getMessage()); db.reports.save(r);
            }
        });
    }

    /** Report as JSON for the portals (with person, reason and dates). */
    public Map<String, Object> view(Report r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id); m.put("title", r.title); m.put("topic", r.topic); m.put("status", r.status); m.put("pages", r.pages);
        m.put("unlocked", r.unlocked); m.put("source", r.source); m.put("error", r.error);
        m.put("createdAt", r.createdAt); m.put("generatedAt", r.generatedAt); m.put("profileId", r.profileId);
        Lang lang = Lang.get(r.language == null ? "en" : r.language);
        m.put("language", lang == null ? "en" : lang.code()); m.put("languageName", lang == null ? "English" : lang.name()); m.put("parentId", r.parentId);
        Profile p = r.profileId == null ? null : db.profiles.findById(r.profileId).orElse(null);
        m.put("person", p == null ? null : p.fullName);
        m.put("relation", p == null ? "Self" : p.relation);
        try { m.put("hook", r.hookJson == null ? null : JSON.readValue(r.hookJson, Map.class)); } catch (Exception e) { m.put("hook", null); }
        return m;
    }
}
