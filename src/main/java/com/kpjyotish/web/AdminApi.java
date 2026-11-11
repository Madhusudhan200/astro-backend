package com.kpjyotish.web;

import com.kpjyotish.config.ApiException;
import com.kpjyotish.config.Env;
import com.kpjyotish.service.Auth;
import com.kpjyotish.service.ConsultService;
import com.kpjyotish.service.Notifier;
import com.kpjyotish.service.Razorpay;
import com.kpjyotish.store.Entities.*;
import com.kpjyotish.store.Repos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/** Admin portal API. */
@RestController
@RequestMapping("/api/admin")
public class AdminApi {
    private final Repos db; private final Auth auth; private final ConsultService consult; private final Notifier notify; private final PublicApi pub;
    private final com.kpjyotish.config.Env env; private final Razorpay rzp;

    public AdminApi(Repos db, Auth auth, ConsultService consult, Notifier notify, PublicApi pub, com.kpjyotish.config.Env env, Razorpay rzp) {
        this.db = db; this.auth = auth; this.consult = consult; this.notify = notify; this.pub = pub; this.env = env; this.rzp = rzp;
    }

    void admin(HttpServletRequest req) { auth.require(req, "admin"); }
    String name(String userId) { return db.users.findById(userId).map(u -> u.fullName).orElse("?"); }

    @GetMapping("/stats")
    public Map<String, Object> stats(HttpServletRequest req) {
        admin(req);
        List<Payment> paid = db.payments.findByStatusOrderByPaidAtDesc("paid");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", db.users.countByRole("client"));
        m.put("usersToday", db.users.countByRoleAndCreatedAtStartingWith("client", LocalDate.now().toString()));
        m.put("reportsReady", db.reports.countByStatus("ready"));
        m.put("reportsSold", paid.stream().filter(p -> p.purpose.equals("report")).count());
        m.put("consultations", db.consultations.count());
        m.put("pendingConsultations", db.consultations.countByStatusIn(List.of("requested", "confirmed")));
        m.put("chats", db.sessions.count());
        m.put("revenue", paid.stream().mapToLong(p -> p.amount).sum() / 100);
        m.put("recent", paid.stream().limit(8).map(p -> { Map<String, Object> x = new LinkedHashMap<>(); x.put("user", name(p.userId)); x.put("purpose", p.purpose); x.put("amount", p.amount); x.put("razorpayPaymentId", p.razorpayPaymentId); x.put("paidAt", p.paidAt); return x; }).toList());
        return m;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(HttpServletRequest req) {
        admin(req);
        return db.users.findByRoleOrderByCreatedAtDesc("client").stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.id); m.put("fullName", u.fullName); m.put("username", u.username); m.put("email", u.email); m.put("phone", u.phone);
            m.put("dob", u.dob); m.put("tob", u.tob); m.put("city", u.city); m.put("createdAt", u.createdAt);
            m.put("profiles", db.profiles.countByUserId(u.id));
            m.put("reports", db.reports.findByUserIdOrderByCreatedAtDesc(u.id).size());
            return m;
        }).toList();
    }

    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable String id, HttpServletRequest req) {
        admin(req);
        User u = db.users.findById(id).filter(x -> "client".equals(x.role)).orElseThrow(() -> new ApiException(404, "Not found"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", u);
        List<Map<String, Object>> profs = new ArrayList<>();
        for (Profile p : consult.profiles(u)) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("profile", p);
            pm.put("chart", consult.chart(p));
            pm.put("memory", consult.memory(p));
            pm.put("messages", db.messages.findByProfileIdOrderByCreatedAtAsc(p.id).stream().map(x -> Map.of("role", x.role, "text", x.text, "session", x.session, "createdAt", x.createdAt)).toList());
            profs.add(pm);
        }
        m.put("profiles", profs);
        m.put("reports", db.reports.findByUserIdOrderByCreatedAtDesc(u.id).stream().map(consult::view).toList());
        m.put("payments", db.payments.findByUserIdOrderByCreatedAtDesc(u.id));
        m.put("consultations", db.consultations.findByUserIdOrderByCreatedAtDesc(u.id));
        return m;
    }

    @PostMapping("/reports/{id}/regenerate")
    public Map<String, Boolean> regenerate(@PathVariable String id, HttpServletRequest req) {
        admin(req);
        Report r = db.reports.findById(id).orElseThrow(() -> new ApiException(404, "Not found"));
        if (r.language != null && !"en".equals(r.language)) { r.translationsJson = null; db.reports.save(r); } // translate again
        consult.generate(r.id);
        return Map.of("ok", true);
    }

    @PostMapping("/reports/{id}/unlock")
    public Map<String, Boolean> unlock(@PathVariable String id, @RequestBody Map<String, Boolean> b, HttpServletRequest req) {
        admin(req);
        Report r = db.reports.findById(id).orElseThrow(() -> new ApiException(404, "Not found"));
        r.unlocked = Boolean.TRUE.equals(b.get("unlocked"));
        db.reports.save(r);
        return Map.of("ok", true);
    }

    @GetMapping("/reports/{id}/report.pdf")
    public ResponseEntity<FileSystemResource> pdf(@PathVariable String id, @RequestParam(required = false) String preview, @RequestParam(required = false) String view, HttpServletRequest req) {
        admin(req);
        db.reports.findById(id).ifPresent(consult::ensureFiles);
        return ClientApi.pdf(preview != null ? consult.previewPath(id) : consult.fullPath(id), "report-" + id + ".pdf", false, view != null);
    }

    @GetMapping("/payments")
    public List<Map<String, Object>> payments(HttpServletRequest req) {
        admin(req);
        return db.payments.findAllByOrderByCreatedAtDesc().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("user", name(p.userId)); m.put("purpose", p.purpose); m.put("amount", p.amount); m.put("status", p.status); m.put("demo", p.demo);
            m.put("orderId", p.orderId); m.put("razorpayPaymentId", p.razorpayPaymentId); m.put("createdAt", p.createdAt);
            return m;
        }).toList();
    }

    @GetMapping("/consultations")
    public List<Map<String, Object>> consultations(HttpServletRequest req) {
        admin(req);
        return db.consultations.findAllByOrderByCreatedAtDesc().stream().map(c -> {
            User u = db.users.findById(c.userId).orElse(new User());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id); m.put("user", u.fullName); m.put("email", u.email); m.put("phone", u.phone); m.put("status", c.status);
            m.put("preferredDate", c.preferredDate); m.put("preferredTime", c.preferredTime); m.put("topic", c.topic); m.put("scheduledAt", c.scheduledAt);
            m.put("meetingLink", c.meetingLink); m.put("notes", c.notes);
            return m;
        }).toList();
    }

    @PatchMapping("/consultations/{id}")
    public Consultation updateConsultation(@PathVariable String id, @RequestBody Map<String, Object> b, HttpServletRequest req) {
        admin(req);
        Consultation c = db.consultations.findById(id).orElseThrow(() -> new ApiException(404, "Not found"));
        Object st = b.get("status");
        if (st != null && List.of("requested", "confirmed", "completed", "cancelled").contains(String.valueOf(st))) c.status = String.valueOf(st);
        if (b.containsKey("scheduledAt")) c.scheduledAt = PublicApi.clean(b.get("scheduledAt"), 30);
        if (b.containsKey("meetingLink")) { String l = PublicApi.clean(b.get("meetingLink"), 300); c.meetingLink = l.matches("https?://.*") ? l : ""; }
        if (b.containsKey("notes")) c.notes = PublicApi.clean(b.get("notes"), 1000);
        db.consultations.save(c);
        if (Boolean.TRUE.equals(b.get("notify"))) db.users.findById(c.userId).ifPresent(u -> notify.consultationUpdated(u, c));
        return c;
    }

    // ------------------------------------------------------------------ integrations (keys entered from the admin panel)
    /** {key, label, group, secret, hint} */
    static final String[][] FIELDS = {
            {"RAZORPAY_KEY_ID", "Razorpay Key ID", "razorpay", "0", "Razorpay Dashboard > Account & Settings > API Keys. Starts with rzp_live_ (real money) or rzp_test_ (testing)."},
            {"RAZORPAY_KEY_SECRET", "Razorpay Key Secret", "razorpay", "1", "Shown once when you generate the key."},
            {"RAZORPAY_WEBHOOK_SECRET", "Razorpay Webhook Secret (optional)", "razorpay", "1", "Webhook URL: <your site>/api/razorpay/webhook, events payment.captured and order.paid."},
            {"SMTP_HOST", "SMTP host", "email", "0", "Gmail: smtp.gmail.com"},
            {"SMTP_PORT", "SMTP port", "email", "0", "Gmail: 587 (or 465)"},
            {"SMTP_USER", "SMTP username (email address)", "email", "0", "Your full Gmail address"},
            {"SMTP_PASS", "SMTP password / App Password", "email", "1", "Gmail needs a 16-letter App Password: Google Account > Security > 2-Step Verification > App passwords."},
            {"MAIL_FROM", "From name & address (optional)", "email", "0", "e.g. Astrology Talk <yourname@gmail.com>"},
            {"ADMIN_NOTIFY_EMAIL", "Admin alert email", "email", "0", "Where new chats, registrations, reports and payments are announced."},
            {"WHATSAPP_PROVIDER", "WhatsApp provider", "whatsapp", "0", "meta (WhatsApp Cloud API) or twilio"},
            {"META_WA_TOKEN", "Meta access token", "whatsapp", "1", "Meta for Developers > your app > WhatsApp > API Setup (use a permanent System User token)."},
            {"META_WA_PHONE_ID", "Meta phone number ID", "whatsapp", "0", "WhatsApp > API Setup > Phone number ID"},
            {"META_WA_TEMPLATE", "Meta approved template name", "whatsapp", "0", "Needed to message customers first. Template body should contain one variable {{1}}."},
            {"META_WA_TEMPLATE_LANG", "Template language code", "whatsapp", "0", "e.g. en or en_US"},
            {"TWILIO_SID", "Twilio Account SID", "whatsapp", "0", "console.twilio.com"},
            {"TWILIO_TOKEN", "Twilio Auth Token", "whatsapp", "1", ""},
            {"TWILIO_WA_FROM", "Twilio WhatsApp number", "whatsapp", "0", "e.g. +14155238886 (sandbox)"},
            {"ADMIN_WHATSAPP", "Admin WhatsApp number", "whatsapp", "0", "Your number with country code, e.g. +919876543210"},
            {"DEFAULT_COUNTRY_CODE", "Default country code", "whatsapp", "0", "91 for India"},
            {"GOOGLE_TRANSLATE_API_KEY", "Google Cloud Translation API key", "smart", "1", "For reports in Telugu, Hindi and all other languages. Google Cloud Console > APIs & Services > enable Cloud Translation API > Credentials > API key."},
            {"ANTHROPIC_API_KEY", "Smart answers key (optional)", "smart", "1", "Anthropic API key from console.anthropic.com - gives free-form expert answers to any question."},
    };

    @GetMapping("/integrations")
    public Map<String, Object> integrations(HttpServletRequest req) {
        admin(req);
        List<Map<String, Object>> fields = new ArrayList<>();
        for (String[] f : FIELDS) {
            Map<String, Object> m = new LinkedHashMap<>();
            boolean secret = f[3].equals("1");
            String v = env.get(f[0]);
            m.put("key", f[0]); m.put("label", f[1]); m.put("group", f[2]); m.put("secret", secret); m.put("hint", f[4]);
            m.put("set", !v.isEmpty()); m.put("source", env.source(f[0]));
            m.put("value", secret ? (v.isEmpty() ? "" : "••••" + v.substring(Math.max(0, v.length() - 4))) : v);
            fields.add(m);
        }
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("razorpay", rzp.live() ? (env.get("RAZORPAY_KEY_ID").startsWith("rzp_live_") ? "live" : "test") : "demo");
        st.put("email", env.has("SMTP_HOST") && env.has("SMTP_USER") && env.has("SMTP_PASS"));
        st.put("whatsapp", !env.get("WHATSAPP_PROVIDER").isEmpty());
        return Map.of("fields", fields, "status", st);
    }

    @PutMapping("/integrations")
    public Map<String, Object> saveIntegrations(@RequestBody Map<String, String> b, HttpServletRequest req) {
        admin(req);
        Set<String> allowed = new HashSet<>();
        for (String[] f : FIELDS) allowed.add(f[0]);
        for (var e : b.entrySet()) {
            if (!allowed.contains(e.getKey()) || e.getValue() == null) continue;
            String v = e.getValue().trim();
            if (v.isEmpty() || v.startsWith("••••")) continue;           // unchanged
            if (v.equals("-")) { db.settings.deleteById(Env.PREFIX + e.getKey()); continue; } // clear
            db.setSetting(Env.PREFIX + e.getKey(), v);
        }
        env.reload();
        return integrations(req);
    }

    @PostMapping("/integrations/test-email")
    public Map<String, String> testEmail(@RequestBody Map<String, String> b, HttpServletRequest req) {
        admin(req);
        String to = PublicApi.clean(b.get("to"), 120);
        if (!to.contains("@")) throw new ApiException(400, "Enter an email address to send the test to");
        return Map.of("result", notify.testEmail(to));
    }

    @PostMapping("/integrations/test-whatsapp")
    public Map<String, String> testWhatsApp(@RequestBody Map<String, String> b, HttpServletRequest req) {
        admin(req);
        String to = PublicApi.clean(b.get("to"), 20);
        if (to.replaceAll("\\D", "").length() < 10) throw new ApiException(400, "Enter a WhatsApp number with country code");
        return Map.of("result", notify.testWhatsApp(to));
    }

    @PostMapping("/integrations/test-razorpay")
    public Map<String, String> testRazorpay(HttpServletRequest req) {
        admin(req);
        if (!rzp.live()) return Map.of("result", "Razorpay keys are not set - payments are in demo mode.");
        try {
            Razorpay.Order o = rzp.createOrder(100, "keycheck_" + System.currentTimeMillis(), Map.of("purpose", "key check"));
            return Map.of("result", "ok", "detail", "Keys are valid (" + (env.get("RAZORPAY_KEY_ID").startsWith("rzp_live_") ? "LIVE - real money" : "TEST mode - no real money") + "). Test order " + o.id() + " created (not charged).");
        } catch (Exception e) {
            return Map.of("result", "Failed: " + e.getMessage() + (String.valueOf(e.getMessage()).contains("401") ? "  →  Key ID or Key Secret is wrong." : ""));
        }
    }

    // ------------------------------------------------------------------ payment gateway dashboard (Razorpay)
    @GetMapping("/gateway")
    public Map<String, Object> gateway(HttpServletRequest req) {
        admin(req);
        Map<String, Object> m = new LinkedHashMap<>();
        String mode = rzp.live() ? (env.get("RAZORPAY_KEY_ID").startsWith("rzp_live_") ? "live" : "test") : "demo";
        m.put("mode", mode);
        m.put("keyId", rzp.live() ? env.get("RAZORPAY_KEY_ID") : "");
        m.put("dashboardUrl", "https://dashboard.razorpay.com/app/dashboard");
        m.put("webhookUrl", env.siteUrl() + "/api/razorpay/webhook");
        // Totals from this website's own payment records
        List<Payment> all = db.payments.findAllByOrderByCreatedAtDesc();
        String today = LocalDate.now().toString(), month = today.substring(0, 7);
        long paidAll = 0, paidToday = 0, paidMonth = 0, reportSales = 0, consultSales = 0; int ok = 0, failed = 0, pending = 0;
        for (Payment p : all) {
            if ("paid".equals(p.status)) {
                ok++; paidAll += p.amount;
                String d = p.paidAt == null ? "" : p.paidAt.substring(0, 10);
                if (d.equals(today)) paidToday += p.amount;
                if (d.startsWith(month)) paidMonth += p.amount;
                if ("report".equals(p.purpose)) reportSales += p.amount; else consultSales += p.amount;
            } else if ("failed".equals(p.status)) failed++; else pending++;
        }
        m.put("totals", Map.of("today", paidToday / 100, "month", paidMonth / 100, "all", paidAll / 100, "reports", reportSales / 100, "consultations", consultSales / 100,
                "success", ok, "failed", failed, "pending", pending));
        // Live data straight from Razorpay (method: UPI/card/netbanking, VPA, fees, settlements)
        if (rzp.live()) {
            try {
                List<Map<String, Object>> pays = new ArrayList<>();
                for (var p : rzp.get("payments?count=25").path("items")) {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("id", p.path("id").asText()); x.put("amount", p.path("amount").asLong() / 100.0); x.put("status", p.path("status").asText());
                    x.put("method", p.path("method").asText()); x.put("vpa", p.path("vpa").asText("")); x.put("email", p.path("email").asText(""));
                    x.put("contact", p.path("contact").asText("")); x.put("fee", p.path("fee").asLong(0) / 100.0); x.put("created", p.path("created_at").asLong() * 1000);
                    x.put("description", p.path("description").asText(""));
                    pays.add(x);
                }
                m.put("razorpayPayments", pays);
                List<Map<String, Object>> sets = new ArrayList<>();
                for (var s : rzp.get("settlements?count=10").path("items")) {
                    sets.add(Map.of("id", s.path("id").asText(), "amount", s.path("amount").asLong() / 100.0, "status", s.path("status").asText(),
                            "utr", s.path("utr").asText(""), "created", s.path("created_at").asLong() * 1000));
                }
                m.put("settlements", sets);
            } catch (Exception e) {
                m.put("razorpayError", e.getMessage());
            }
        }
        return m;
    }

    @GetMapping("/notifications")
    public List<Notification> notifications(HttpServletRequest req) { admin(req); return db.notifications.findTop100ByOrderByCreatedAtDesc(); }

    @GetMapping("/settings")
    public Map<String, Integer> settings(HttpServletRequest req) { admin(req); return pub.prices(); }

    @PutMapping("/settings")
    public Map<String, Integer> saveSettings(@RequestBody Map<String, Object> b, HttpServletRequest req) {
        admin(req);
        int r = (int) Math.round(Double.parseDouble(String.valueOf(b.get("report")))), c = (int) Math.round(Double.parseDouble(String.valueOf(b.get("consultation"))));
        if (r < 1 || c < 1) throw new ApiException(400, "Prices must be at least Rs 1");
        db.setSetting("reportPrice", String.valueOf(r));
        db.setSetting("consultPrice", String.valueOf(c));
        return pub.prices();
    }
}
