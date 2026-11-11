package com.kpjyotish.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpjyotish.ai.AiTypes.*;
import com.kpjyotish.config.Env;
import com.kpjyotish.kp.Chart;
import com.kpjyotish.store.Entities.ChatMessage;
import com.kpjyotish.store.Entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * Claude-powered KP astrologer (chat with memory + report writer).
 * Falls back to the rule-based {@link FallbackAstrologer} when no API key is set or a call fails.
 */
@Component
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Env env;
    private final FallbackAstrologer fallback;
    private volatile AnthropicClient cachedClient;
    private volatile String cachedKey = "";

    public AiService(Env env, FallbackAstrologer fallback) {
        this.env = env;
        this.fallback = fallback;
    }

    /** Client for the current key (admin panel or .env); null when no key is set. */
    private AnthropicClient client() {
        String key = env.get("ANTHROPIC_API_KEY");
        if (key.isEmpty() || "true".equals(env.get("AI_DISABLED"))) return null;
        if (cachedClient == null || !key.equals(cachedKey)) {
            cachedClient = AnthropicOkHttpClient.builder().apiKey(key).build();
            cachedKey = key;
        }
        return cachedClient;
    }

    public boolean enabled() { return client() != null; }
    public String model() { return env.get("AI_MODEL", "claude-opus-5-5"); }

    // ------------------------------------------------------------------ prompts
    static String chartJson(Chart c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ascendant", c.ascendant);
        m.put("moonSign", c.moonSign); m.put("nakshatra", c.nakshatra); m.put("sunSign", c.sunSign);
        List<Map<String, Object>> pl = new ArrayList<>();
        for (Chart.Point p : c.planets) pl.add(Map.of("planet", p.name, "sign", p.sign, "deg", p.dms, "house", p.house, "retro", p.retrograde, "star", p.starLord, "sub", p.subLord));
        m.put("planets", pl);
        List<Map<String, Object>> cu = new ArrayList<>();
        for (Chart.Point p : c.cusps) cu.add(Map.of("cusp", p.house, "sign", p.sign, "deg", p.dms, "star", p.starLord, "sub", p.subLord));
        m.put("cusps", cu);
        Map<String, Object> sig = new LinkedHashMap<>();
        for (Chart.Significator s : c.significators) sig.put(s.planet, s.all);
        m.put("planetSignifiesHouses", sig);
        m.put("rulingPlanets", c.rulingPlanets);
        List<Map<String, Object>> md = new ArrayList<>();
        for (Chart.Period p : c.dasha.periods) md.add(Map.of("lord", p.lord, "start", p.start, "end", p.end));
        m.put("mahadashas", md);
        m.put("current", c.currentDasha);
        Chart.Period cur = c.dasha.periods.stream().filter(p -> p.lord.equals(c.currentDasha.mahadasha)).findFirst().orElse(null);
        if (cur != null) m.put("currentMahadashaAntardashas", cur.antardashas.stream().map(a -> a.lord + " " + a.start + ".." + a.end).toList());
        try { return JSON.writeValueAsString(m); } catch (Exception e) { return "{}"; }
    }

    static String profile(User u) {
        int age = java.time.Period.between(java.time.LocalDate.parse(u.dob), java.time.LocalDate.now()).getYears();
        return "Name: " + u.fullName + "\nGender: " + (u.gender == null || u.gender.isBlank() ? "not given" : u.gender)
                + "\nDate of birth: " + u.dob + "  Time: " + u.tob + "  (age " + age + ")\nBirth place: " + u.city
                + "\nToday: " + java.time.LocalDate.now();
    }

    static String memoryBlock(List<String> memory) {
        return memory.isEmpty() ? "(nothing yet)" : "- " + String.join("\n- ", memory);
    }

    private static final String CHAT_RULES = """
            You are the astrologer of "Astrology Talk", a warm, experienced Krishnamurti Paddhati (KP) astrologer chatting with a client. Never give yourself a personal name and never call yourself an AI, bot or assistant - you are simply "the astrologer at Astrology Talk".
            How to consult:
            1. First understand WHY they are consulting. If unclear, ask what is on their mind.
            2. ALWAYS interview before answering: ask ONE question at a time, 3-4 rounds - their current situation, two psychological questions (feelings, motivation, how they handle setbacks / pressure, what they truly want) and one astrology question (a past life event with month/year for chart verification, and whether the birth time is from a certificate).
               Example: a 10th-class student unsure between MPC and BiPC -> ask which subjects they enjoy and score best in, whether they like solving problems/building things or understanding living beings/helping people, how they handle pressure and long study hours, and family expectations.
            3. Then answer clearly: combine their answers with KP principles - judge the relevant cusp sub lords and the houses they signify via their star lords (education 4,5,9,11; career 2,6,10,11; marriage 2,7,11; property 4,11,12; foreign 3,9,12; health 1,5,11 vs 6,8,12; children 2,5,11), and time events with the running and upcoming dasha/antardasha lords. Name the actual planets from the chart.
            4. After giving the answer set readyForReport=true and invite them to generate their personalised PDF report (built from this conversation). They can keep asking questions.
            Use the client's name, remember what you know about them (memory), never re-ask things you already know. Reply in the language the client writes in.
            Keep replies under ~180 words, plain text, no markdown headings. Be encouraging and practical; never make fatalistic predictions about death, serious illness or disasters; for medical, legal or financial decisions suggest also consulting a qualified professional.
            You may answer any astrology question (KP, Vedic, nakshatras, dashas, transits, muhurta, remedies) - always relate it back to this client's chart when possible.""";

    // ------------------------------------------------------------------ chat
    public ChatTurn chat(User u, Chart c, List<String> memory, List<ChatMessage> history, FallbackAstrologer.State state) {
        AnthropicClient client = client();
        if (client == null) return fallback.reply(u, c, memory, history, state);
        String system = CHAT_RULES + "\n\n=== CLIENT PROFILE ===\n" + profile(u) + "\n\n=== WHAT YOU REMEMBER ABOUT THIS CLIENT ===\n" + memoryBlock(memory)
                + "\n\n=== CLIENT'S KP CHART (Placidus cusps, KP ayanamsa; computed precisely - do not recompute) ===\n" + chartJson(c);
        try {
            return call(ChatTurn.class, 4000L, b -> {
                b.system(system);
                addHistory(b, history);
                return b;
            });
        } catch (Exception e) {
            log.warn("AI chat failed, using rule-based reply: {}", e.getMessage());
            return fallback.reply(u, c, memory, history, state);
        }
    }

    private static void addHistory(StructuredMessageCreateParams.Builder<?> b, List<ChatMessage> history) {
        // Messages must start with a user turn and alternate; merge consecutive same-role turns.
        List<String[]> turns = new ArrayList<>();
        turns.add(new String[]{"user", "[The client opened the consultation chat.]"});
        for (ChatMessage m : history) {
            String[] last = turns.get(turns.size() - 1);
            if (last[0].equals(m.role)) last[1] = last[1] + "\n\n" + m.text;
            else turns.add(new String[]{m.role, m.text});
        }
        for (String[] t : turns) {
            if (t[0].equals("user")) b.addUserMessage(t[1]); else b.addAssistantMessage(t[1]);
        }
    }

    // ------------------------------------------------------------------ report
    public ReportResult report(User u, Chart c, List<String> memory, List<ChatMessage> transcript, String topic) {
        if (client() != null) {
            StringBuilder tx = new StringBuilder();
            for (ChatMessage m : transcript) tx.append(m.role.equals("user") ? "CLIENT: " : "ASTROLOGER: ").append(m.text).append("\n");
            String prompt = "Write a personalised KP horoscope report for this client, built around their consultation.\n\n=== CLIENT PROFILE ===\n" + profile(u)
                    + "\n\n=== MEMORY ===\n" + memoryBlock(memory)
                    + "\n\n=== CONSULTATION TOPIC ===\n" + (topic == null || topic.isBlank() ? "General life reading" : topic)
                    + "\n\n=== CONSULTATION TRANSCRIPT ===\n" + (tx.length() == 0 ? "(no chat - write a general reading)" : tx)
                    + "\n\n=== KP CHART ===\n" + chartJson(c)
                    + "\n\nRules: use KP (cusp sub lords, star lords, 4-level significators, Vimshottari dasha/antardasha with dates). The 'consultation' sections must directly answer the client's questions using both the chart and what they told you (their interests, aptitude, situation), with a clear recommendation, reasoning, timing and practical next steps."
                    + " The 'sections' are life-area chapters and must be exactly these titles, in order: " + String.join("; ", FallbackAstrologer.LIFE_AREAS)
                    + ". Each life-area section 300-450 words: the relevant cusp sub lord and its significations, significators, favourable dasha/antardasha windows with years, and practical advice that uses what the client told you."
                    + " Plain text paragraphs separated by blank lines, no markdown. Warm, specific, constructive; no fatalistic claims about death or disease."
                    + " (The rest of the 90+ page report — tables, divisional charts, planet and house chapters, dasha tables, yearly forecasts and remedies — is generated separately, so focus on these sections.)";
            try {
                ReportContent rc = call(ReportContent.class, 16000L, b -> { b.addUserMessage(prompt); return b; });
                if (rc.sections() != null && !rc.sections().isEmpty()) return new ReportResult(rc, "ai:" + model());
            } catch (Exception e) {
                log.warn("AI report failed, using rule-based report: {}", e.getMessage());
            }
        }
        return new ReportResult(fallback.report(u, c, memory, transcript, topic), "rule-based");
    }

    // ------------------------------------------------------------------ core call
    /** Structured call with server-side refusal fallback; retried without it if the API rejects the beta. */
    private <T> T call(Class<T> type, long maxTokens, Function<StructuredMessageCreateParams.Builder<T>, StructuredMessageCreateParams.Builder<T>> fill) {
        try {
            return send(type, maxTokens, fill, true);
        } catch (BadRequestException e) {
            log.info("retrying without server-side fallbacks: {}", e.getMessage());
            return send(type, maxTokens, fill, false);
        }
    }

    private <T> T send(Class<T> type, long maxTokens, Function<StructuredMessageCreateParams.Builder<T>, StructuredMessageCreateParams.Builder<T>> fill, boolean withFallback) {
        StructuredMessageCreateParams.Builder<T> b = MessageCreateParams.builder().model(model()).maxTokens(maxTokens).outputConfig(type);
        if (withFallback) {
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        StructuredMessage<T> msg;
        try {
            msg = client().messages().create(fill.apply(b).build());
        } catch (AnthropicServiceException e) {
            throw e;
        }
        if (msg.stopReason().map(r -> r.equals(StopReason.REFUSAL)).orElse(false)) throw new IllegalStateException("AI declined the request");
        if (msg.stopReason().map(r -> r.equals(StopReason.MAX_TOKENS)).orElse(false)) throw new IllegalStateException("AI output truncated");
        return msg.content().stream().flatMap(cb -> cb.text().stream()).map(t -> t.text()).findFirst()
                .orElseThrow(() -> new IllegalStateException("AI returned no content"));
    }
}
