package com.kpjyotish.web;

import com.kpjyotish.config.ApiException;
import com.kpjyotish.kp.Chart;
import com.kpjyotish.service.Auth;
import com.kpjyotish.service.ConsultService;
import com.kpjyotish.store.Entities.*;
import com.kpjyotish.store.Repos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Client portal: profiles (self + family/friends), AI astrologer chat, chart and reports. */
@RestController
@RequestMapping("/api/my")
public class ClientApi {
    private final Repos db; private final Auth auth; private final ConsultService consult; private final Birth birth; private final PublicApi pub;

    public ClientApi(Repos db, Auth auth, ConsultService consult, Birth birth, PublicApi pub) {
        this.db = db; this.auth = auth; this.consult = consult; this.birth = birth; this.pub = pub;
    }

    User me(HttpServletRequest req) { return auth.require(req, "client"); }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest req) {
        User u = me(req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", u);
        m.put("profiles", consult.profiles(u));
        m.put("relations", ConsultService.RELATIONS.subList(1, ConsultService.RELATIONS.size()));
        m.put("reports", db.reports.findByUserIdOrderByCreatedAtDesc(u.id).stream().map(consult::view).toList());
        m.put("consultations", db.consultations.findByUserIdOrderByCreatedAtDesc(u.id));
        m.put("payments", db.payments.findByUserIdOrderByCreatedAtDesc(u.id).stream().filter(p -> "paid".equals(p.status)).toList());
        m.put("prices", pub.prices());
        return m;
    }

    // ------------------------------------------------------------------ profiles
    @GetMapping("/profiles")
    public List<Profile> profiles(HttpServletRequest req) { return consult.profiles(me(req)); }

    @PostMapping("/profiles")
    public Profile addProfile(@RequestBody Map<String, Object> b, HttpServletRequest req) {
        User u = me(req);
        String relation = PublicApi.clean(b.get("relation"), 20);
        if (!ConsultService.RELATIONS.contains(relation) || relation.equals("Self")) throw new ApiException(400, "Choose who this profile is for (mother, father, spouse, son, daughter, friend...)");
        String name = PublicApi.clean(b.get("fullName"), 80);
        if (name.isEmpty()) throw new ApiException(400, "Please enter the person's full name");
        if (db.profiles.countByUserId(u.id) >= 20) throw new ApiException(400, "You can add up to 20 profiles");
        Birth.Data bd = birth.resolve(b);
        Profile p = new Profile();
        p.userId = u.id; p.relation = relation; p.fullName = name; p.gender = PublicApi.clean(b.get("gender"), 10);
        p.dob = bd.dob(); p.tob = bd.tob(); p.city = bd.city(); p.lat = bd.lat(); p.lon = bd.lon(); p.tz = bd.tz(); p.tzName = bd.tzName();
        return db.profiles.save(p);
    }

    @DeleteMapping("/profiles/{id}")
    public Map<String, Boolean> deleteProfile(@PathVariable String id, HttpServletRequest req) {
        Profile p = consult.profile(me(req), id);
        if ("Self".equals(p.relation)) throw new ApiException(400, "Your own profile cannot be deleted");
        db.profiles.delete(p);
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------ chart
    @GetMapping("/chart")
    public Chart chart(@RequestParam(required = false) String profileId, HttpServletRequest req) {
        return consult.chart(consult.profile(me(req), profileId));
    }

    // ------------------------------------------------------------------ chat
    @GetMapping("/chat")
    public Map<String, Object> chat(@RequestParam(required = false) String profileId, HttpServletRequest req) {
        User u = me(req);
        return consult.open(u, consult.profile(u, profileId));
    }

    @PostMapping("/chat")
    public Map<String, Object> send(@RequestBody Map<String, String> b, HttpServletRequest req) {
        User u = me(req);
        return consult.send(u, consult.profile(u, b.get("profileId")), b.get("message"));
    }

    @PostMapping("/chat/new")
    public Map<String, Object> newChat(@RequestBody Map<String, String> b, HttpServletRequest req) {
        User u = me(req);
        return consult.newSession(u, consult.profile(u, b.get("profileId")));
    }

    @DeleteMapping("/memory/{id}")
    public Map<String, Boolean> forget(@PathVariable String id, HttpServletRequest req) {
        consult.forget(me(req), id);
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------ reports
    @PostMapping("/reports")
    public Map<String, Object> createReport(@RequestBody Map<String, String> b, HttpServletRequest req) {
        User u = me(req);
        return consult.view(consult.createReport(u, consult.profile(u, b.get("profileId")), b.getOrDefault("language", "en")));
    }

    /** Get an existing report in another language (PDF only). */
    @PostMapping("/reports/{id}/translate")
    public Map<String, Object> translate(@PathVariable String id, @RequestBody Map<String, String> b, HttpServletRequest req) {
        return consult.view(consult.translateReport(me(req), id, b.get("language")));
    }

    /** English texts of a report that is waiting to be translated in the browser. */
    @GetMapping("/reports/{id}/texts")
    public Map<String, Object> texts(@PathVariable String id, HttpServletRequest req) throws Exception {
        Report r = myReport(me(req), id);
        if (!"translating".equals(r.status)) throw new ApiException(400, "This report is not waiting for translation");
        return Map.of("language", r.language, "texts", consult.textsToTranslate(r));
    }

    @PostMapping("/reports/{id}/translations")
    public Map<String, Object> translations(@PathVariable String id, @RequestBody Map<String, Map<String, String>> b, HttpServletRequest req) throws Exception {
        Report r = myReport(me(req), id);
        consult.acceptTranslations(r, b.get("translations"));
        return consult.view(r);
    }

    /** Website language chosen in My Profile. */
    @PostMapping("/language")
    public Map<String, Object> language(@RequestBody Map<String, String> b, HttpServletRequest req) {
        User u = me(req);
        String code = b.get("language");
        if (com.kpjyotish.i18n.Lang.get(code) == null) throw new ApiException(400, "Unknown language");
        u.language = code;
        db.users.save(u);
        return Map.of("ok", true, "language", code);
    }

    Report myReport(User u, String id) {
        return db.reports.findById(id).filter(r -> r.userId.equals(u.id)).orElseThrow(() -> new ApiException(404, "Report not found"));
    }

    @GetMapping("/reports/{id}")
    public Map<String, Object> report(@PathVariable String id, HttpServletRequest req) { return consult.view(myReport(me(req), id)); }

    @PostMapping("/reports/{id}/retry")
    public Map<String, Boolean> retry(@PathVariable String id, HttpServletRequest req) {
        Report r = myReport(me(req), id);
        if ("failed".equals(r.status)) {
            if (r.language != null && !"en".equals(r.language)) { r.translationsJson = null; db.reports.save(r); }
            consult.generate(r.id);
        }
        return Map.of("ok", true);
    }

    static ResponseEntity<FileSystemResource> pdf(Path p, String name, boolean download, boolean viewer) {
        if (!Files.exists(p)) throw new ApiException(404, "Report is still being prepared");
        return ResponseEntity.ok()
                // Served as octet-stream for the in-page viewer so download managers (e.g. IDM) don't hijack it
                .contentType(viewer ? MediaType.APPLICATION_OCTET_STREAM : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"" + name + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new FileSystemResource(p));
    }

    @GetMapping("/reports/{id}/preview.pdf")
    public ResponseEntity<FileSystemResource> preview(@PathVariable String id, @RequestParam(required = false) String view, HttpServletRequest req) {
        Report r = myReport(me(req), id);
        consult.ensureFiles(r);
        return pdf(consult.previewPath(r.id), "kp-report-preview.pdf", false, view != null);
    }

    @GetMapping("/reports/{id}/full.pdf")
    public ResponseEntity<FileSystemResource> full(@PathVariable String id, @RequestParam(required = false) String download, @RequestParam(required = false) String view, HttpServletRequest req) {
        Report r = myReport(me(req), id);
        if (!r.unlocked) throw new ApiException(402, "Please unlock the full report");
        consult.ensureFiles(r);
        return pdf(consult.fullPath(r.id), "kp-horoscope-" + r.id + ".pdf", download != null, view != null);
    }
}
