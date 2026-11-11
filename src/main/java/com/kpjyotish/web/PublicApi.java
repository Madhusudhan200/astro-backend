package com.kpjyotish.web;

import com.kpjyotish.config.ApiException;
import com.kpjyotish.config.Env;
import com.kpjyotish.places.PlaceIndex;
import com.kpjyotish.service.*;
import com.kpjyotish.store.Entities.User;
import com.kpjyotish.store.Repos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api")
public class PublicApi {
    private static final Logger log = LoggerFactory.getLogger(PublicApi.class);
    private static final SecureRandom RND = new SecureRandom();
    private final Repos db; private final Auth auth; private final Env env; private final PlaceIndex places; private final Notifier notify;
    private final Razorpay rzp; private final PayApi pay; private final com.kpjyotish.ai.AiService ai; private final Birth birth; private final ConsultService consult;

    public PublicApi(Repos db, Auth auth, Env env, PlaceIndex places, Notifier notify, Razorpay rzp, PayApi pay, com.kpjyotish.ai.AiService ai, Birth birth, ConsultService consult) {
        this.db = db; this.auth = auth; this.env = env; this.places = places; this.notify = notify; this.rzp = rzp; this.pay = pay; this.ai = ai; this.birth = birth; this.consult = consult;
    }

    static String clean(Object o, int max) {
        String s = o == null ? "" : String.valueOf(o).replaceAll("[<>]", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedAdmin() {
        String email = env.get("ADMIN_EMAIL", "admin@astrologytalk.local").toLowerCase(Locale.ROOT);
        String password = env.get("ADMIN_PASSWORD", "Admin@123");
        User a = db.users.findFirstByRoleAndEmail("admin", email).orElse(null);
        if (a == null) {
            a = new User();
            a.role = "admin"; a.fullName = "Administrator"; a.email = email;
            a.passwordHash = Auth.BCRYPT.encode(password);
            log.info("created admin account {}", email);
        } else {
            // If ADMIN_PASSWORD was changed (e.g. in Render > Environment), apply the new value once.
            // A password changed later in the admin portal stays until ADMIN_PASSWORD changes again.
            String applied = db.setting("adminPasswordFromEnv", "");
            String fingerprint = Integer.toHexString((email + "|" + password).hashCode());
            if (!applied.equals(fingerprint) && !Auth.BCRYPT.matches(password, a.passwordHash)) {
                a.passwordHash = Auth.BCRYPT.encode(password);
                log.info("admin password for {} updated from ADMIN_PASSWORD", email);
            }
            db.setSetting("adminPasswordFromEnv", fingerprint);
        }
        // The main admin owns the login ID "admin"; older admin accounts get their own IDs
        for (User other : db.users.findByRole("admin")) {
            if (!other.email.equals(email) && "admin".equalsIgnoreCase(other.username)) {
                // e.g. admin@kpjyotish.local -> login ID "admin-kpjyotish"
                String[] parts = other.email.split("@");
                other.username = parts[0] + "-" + (parts.length > 1 ? parts[1].split("\\.")[0] : other.id.substring(other.id.length() - 4));
                db.users.save(other);
            }
        }
        a.username = "admin";
        db.users.save(a);
        if (db.setting("adminPasswordFromEnv", "").isEmpty()) db.setSetting("adminPasswordFromEnv", Integer.toHexString((email + "|" + password).hashCode()));
        log.info("\n\n  Astrology Talk running at http://localhost:{}\n  Payments: {}\n  AI: {}\n",
                env.get("PORT", "3000"), rzp.live() ? "Razorpay (UPI / PhonePe / GPay / cards)" : "DEMO mode - add RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET to .env for real payments",
                ai.enabled() ? "smart answers (" + ai.model() + ")" : "built-in KP astrologer (add a smart-answers key in Admin > Settings for free-form answers)");
    }

    public Map<String, Integer> prices() {
        return Map.of("report", Integer.parseInt(db.setting("reportPrice", env.get("REPORT_PRICE", "499"))),
                "consultation", Integer.parseInt(db.setting("consultPrice", env.get("CONSULT_PRICE", "999"))));
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("siteName", env.siteName()); m.put("razorpayKey", rzp.live() ? rzp.keyId() : null); m.put("demoPayments", !rzp.live()); m.put("prices", prices());
        return m;
    }

    /** All languages: Indian languages first, then world languages. */
    @GetMapping("/languages")
    public List<com.kpjyotish.i18n.Lang> languages() { return com.kpjyotish.i18n.Lang.all(); }

    @GetMapping("/places")
    public List<PlaceIndex.Place> places(@RequestParam("q") String q) {
        q = clean(q, 80);
        return q.length() < 2 ? List.of() : places.search(q);
    }

    @GetMapping("/tz")
    public Map<String, Double> tz(@RequestParam String tzName, @RequestParam String dob, @RequestParam(required = false) String tob) {
        if (!PlaceIndex.validTz(tzName) || !dob.matches("\\d{4}-\\d{2}-\\d{2}")) throw new ApiException(400, "Invalid");
        return Map.of("tz", PlaceIndex.offsetForLocal(tzName, dob, tob != null && tob.matches("\\d{2}:\\d{2}(:\\d{2})?") ? tob : "12:00:00"));
    }

    static String genPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 8; i++) p.append(chars.charAt(RND.nextInt(chars.length())));
        return p.append('@').append(10 + RND.nextInt(90)).toString();
    }

    String genUsername(String fullName) {
        String base = fullName.split("\\s+")[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (base.isEmpty()) base = "user";
        if (base.length() > 10) base = base.substring(0, 10);
        while (true) {
            String u = base + (1000 + RND.nextInt(9000));
            if (!db.users.existsByUsername(u)) return u;
        }
    }

    static boolean has(Object o) { return o != null && !String.valueOf(o).isBlank(); }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> b, HttpServletResponse res) {
        String fullName = clean(b.get("fullName"), 80), email = clean(b.get("email"), 120).toLowerCase(Locale.ROOT), phone = clean(b.get("phone"), 20);
        if (fullName.isEmpty() || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ApiException(400, "Please enter your full name and a valid email.");
        if (!phone.isEmpty() && !phone.matches("\\+?[\\d\\s-]{10,16}")) throw new ApiException(400, "Please enter a valid WhatsApp number.");
        Birth.Data bd = birth.resolve(b);
        if (db.users.findByEmail(email).isPresent()) throw new ApiException(409, "This email is already registered. Please login.");

        String password = genPassword();
        User u = new User();
        u.role = "client"; u.fullName = fullName; u.email = email; u.phone = phone; u.dob = bd.dob(); u.tob = bd.tob(); u.gender = clean(b.get("gender"), 10);
        u.city = bd.city(); u.lat = bd.lat(); u.lon = bd.lon(); u.tz = bd.tz(); u.tzName = bd.tzName();
        u.username = genUsername(fullName); u.passwordHash = Auth.BCRYPT.encode(password);
        u = db.users.save(u);
        consult.self(u);
        auth.login(res, u);
        notify.registered(u, password);
        return Map.of("user", u, "credentials", Map.of("username", u.username, "email", email, "password", password));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> b, HttpServletResponse res) {
        String login = clean(b.get("login"), 120).toLowerCase(Locale.ROOT);
        String pw = String.valueOf(b.get("password"));
        if (login.isEmpty() || pw.isEmpty() || "null".equals(pw)) throw new ApiException(400, "Please enter your email / login ID and password");
        // Email first, then login ID (tolerant of duplicate IDs from older versions)
        List<User> candidates = new ArrayList<>();
        db.users.findByEmail(login).ifPresent(candidates::add);
        candidates.addAll(db.users.findAllByUsernameIgnoreCase(login));
        User u = candidates.stream().filter(x -> x.passwordHash != null && Auth.BCRYPT.matches(pw, x.passwordHash)).findFirst().orElse(null);
        if (u == null) throw new ApiException(401, candidates.isEmpty() ? "No account found with this email or login ID" : "Wrong password. Use “Forgot password?” to reset it.");
        auth.login(res, u);
        return Map.of("user", u);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletResponse res) { auth.logout(res); return Map.of("ok", true); }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) { return Map.of("user", auth.require(req, null)); }

    @PostMapping("/change-password")
    public Map<String, Boolean> changePassword(@RequestBody Map<String, String> b, HttpServletRequest req) {
        User u = auth.require(req, null);
        if (!Auth.BCRYPT.matches(String.valueOf(b.get("current")), u.passwordHash)) throw new ApiException(400, "Current password is wrong");
        String next = String.valueOf(b.get("next"));
        if (next.length() < 8) throw new ApiException(400, "New password must be at least 8 characters");
        u.passwordHash = Auth.BCRYPT.encode(next);
        db.users.save(u);
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------ forgot password (email OTP)
    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody Map<String, String> b) {
        String email = clean(b.get("email"), 120).toLowerCase(Locale.ROOT);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ApiException(400, "Please enter your registered email address.");
        User u = db.users.findByEmail(email).orElseThrow(() -> new ApiException(404, "No account is registered with this email."));
        var prev = db.resets.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(email);
        if (prev.isPresent() && prev.get().createdAt != null && java.time.Instant.parse(prev.get().createdAt).isAfter(java.time.Instant.now().minusSeconds(60)))
            throw new ApiException(429, "A code was sent less than a minute ago. Please check your email (and spam folder) or wait a moment.");
        prev.ifPresent(p -> { p.used = true; db.resets.save(p); });
        String otp = String.format("%06d", RND.nextInt(1_000_000));
        var r = new com.kpjyotish.store.Entities.PasswordReset();
        r.email = email; r.otpHash = Auth.BCRYPT.encode(otp); r.expiresAt = System.currentTimeMillis() + 10 * 60_000;
        db.resets.save(r);
        boolean sent = notify.passwordOtp(u, otp);
        String masked = email.replaceAll("(^.).*(.@)", "$1***$2");
        return Map.of("ok", true, "sentTo", masked, "emailDelivered", sent,
                "message", sent ? "We sent a 6-digit code to " + masked + ". It is valid for 10 minutes."
                        : "Email delivery is not set up yet on this site, so the code could not be emailed. Please contact support.");
    }

    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> b, HttpServletResponse res) {
        String email = clean(b.get("email"), 120).toLowerCase(Locale.ROOT);
        String otp = clean(b.get("otp"), 6), pw = String.valueOf(b.get("password"));
        var r = db.resets.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(email).orElseThrow(() -> new ApiException(400, "Please request a new code."));
        if (System.currentTimeMillis() > r.expiresAt) { r.used = true; db.resets.save(r); throw new ApiException(400, "This code has expired. Please request a new one."); }
        if (r.attempts >= 5) { r.used = true; db.resets.save(r); throw new ApiException(400, "Too many wrong attempts. Please request a new code."); }
        if (!Auth.BCRYPT.matches(otp, r.otpHash)) { r.attempts++; db.resets.save(r); throw new ApiException(400, "Wrong code. " + (5 - r.attempts) + " attempts left."); }
        if (pw.length() < 8) throw new ApiException(400, "New password must be at least 8 characters.");
        User u = db.users.findByEmail(email).orElseThrow(() -> new ApiException(404, "Account not found."));
        u.passwordHash = Auth.BCRYPT.encode(pw);
        db.users.save(u);
        r.used = true; db.resets.save(r);
        auth.login(res, u);
        return Map.of("ok", true, "user", u);
    }

    @PostMapping("/razorpay/webhook")
    public Map<String, Boolean> webhook(@RequestBody byte[] raw, @RequestHeader(value = "X-Razorpay-Signature", required = false) String sig) {
        if (!rzp.verifyWebhook(raw, sig)) throw new ApiException(400, "Bad signature");
        pay.onWebhook(raw);
        return Map.of("ok", true);
    }
}
