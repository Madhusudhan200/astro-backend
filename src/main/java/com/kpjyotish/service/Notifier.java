package com.kpjyotish.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpjyotish.config.Env;
import com.kpjyotish.kp.Chart;
import com.kpjyotish.store.Entities.*;
import com.kpjyotish.store.Repos;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Email (SMTP) + WhatsApp (Meta Cloud API or Twilio) notifications, sent in the background.
 * Without credentials, messages are printed to the console and stored in the notification log.
 */
@Component
public class Notifier {
    private static final Logger log = LoggerFactory.getLogger(Notifier.class);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Env env;
    private final Repos db;
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> { Thread t = new Thread(r, "notify"); t.setDaemon(true); return t; });
    private volatile JavaMailSenderImpl mail;
    private volatile String mailConfig = "";

    public Notifier(Env env, Repos db) { this.env = env; this.db = db; }

    /** Mail sender built from the current settings (admin panel or .env); null if SMTP is not configured. */
    JavaMailSenderImpl mail() {
        if (!env.has("SMTP_HOST") || !env.has("SMTP_USER") || !env.has("SMTP_PASS")) return null;
        String cfg = env.get("SMTP_HOST") + "|" + env.get("SMTP_PORT") + "|" + env.get("SMTP_USER") + "|" + env.get("SMTP_PASS") + "|" + env.get("SMTP_SECURE");
        if (mail == null || !cfg.equals(mailConfig)) {
            JavaMailSenderImpl m = new JavaMailSenderImpl();
            int port = env.getInt("SMTP_PORT", 587);
            m.setHost(env.get("SMTP_HOST"));
            m.setPort(port);
            m.setUsername(env.get("SMTP_USER"));
            m.setPassword(env.get("SMTP_PASS").replace(" ", "")); // Gmail app passwords are often pasted with spaces
            Properties p = m.getJavaMailProperties();
            p.put("mail.smtp.auth", "true");
            p.put("mail.smtp.connectiontimeout", "15000"); p.put("mail.smtp.timeout", "15000"); p.put("mail.smtp.writetimeout", "15000");
            if ("true".equals(env.get("SMTP_SECURE")) || port == 465) p.put("mail.smtp.ssl.enable", "true"); else p.put("mail.smtp.starttls.enable", "true");
            mail = m; mailConfig = cfg;
        }
        return mail;
    }

    /** Sends immediately; throws with the provider's error message. Returns false if email is not configured. */
    boolean sendEmailNow(String to, String subject, String title, String bodyHtml) throws Exception {
        JavaMailSenderImpl m = mail();
        if (m == null) return false;
        MimeMessage msg = m.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");
        String from = env.get("MAIL_FROM", env.get("SMTP_USER"));
        if (!from.contains("@")) from = env.siteName() + " <" + env.get("SMTP_USER") + ">";
        h.setFrom(new jakarta.mail.internet.InternetAddress(from.contains("<") ? from : env.siteName() + " <" + from + ">"));
        h.setTo(to); h.setSubject(subject); h.setText(layout(title, bodyHtml), true);
        m.send(msg);
        return true;
    }

    /** Test helpers for the admin panel: return "ok" or the exact error. */
    public String testEmail(String to) {
        try {
            boolean sent = sendEmailNow(to, env.siteName() + " - test email", "Test email ", "<p>Email notifications are working.</p>");
            log(sentRecord("email", to, "Test email", null, sent ? "sent" : "logged", null));
            return sent ? "ok" : "Email is not configured: fill SMTP host, user and password first.";
        } catch (Exception e) {
            log(sentRecord("email", to, "Test email", null, "failed", e.getMessage()));
            return "Failed: " + rootMessage(e);
        }
    }

    public String testWhatsApp(String to) {
        try {
            String status = sendWhatsAppNow(to, "Test message from " + env.siteName() + ". WhatsApp notifications are working.");
            log(sentRecord("whatsapp", phone(to), null, "Test message", status, null));
            return status.equals("sent") ? "ok" : "WhatsApp is not configured: choose a provider and fill its keys first.";
        } catch (Exception e) {
            log(sentRecord("whatsapp", phone(to), null, "Test message", "failed", e.getMessage()));
            return "Failed: " + rootMessage(e);
        }
    }

    static String rootMessage(Throwable e) {
        Throwable r = e; while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String m = r.getMessage() == null ? e.toString() : r.getMessage();
        if (m.contains("535") || m.toLowerCase(Locale.ROOT).contains("authentication")) m += "  →  Wrong email/password. For Gmail you must use a 16-letter App Password (Google Account > Security > 2-Step Verification > App passwords), not your normal password.";
        return m;
    }

    Notification sentRecord(String channel, String to, String subject, String text, String status, String error) {
        Notification n = new Notification();
        n.channel = channel; n.to = to; n.subject = subject; n.text = trim(text); n.status = status; n.error = trim(error);
        return n;
    }

    void log(Notification n) { db.notifications.save(n); }

    static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    String layout(String title, String body) {
        return "<!doctype html><html><body style=\"margin:0;background:#fff7ed;font-family:Segoe UI,Arial,sans-serif;color:#1f1300\">"
                + "<div style=\"max-width:560px;margin:24px auto;background:#fff;border-radius:16px;overflow:hidden;border:1px solid #fed7aa\">"
                + "<div style=\"background:linear-gradient(135deg,#ea580c,#f59e0b);padding:24px 28px;color:#fff\"><div style=\"font-size:13px;letter-spacing:2px;text-transform:uppercase\">" + esc(env.siteName())
                + "</div><div style=\"font-size:22px;font-weight:700;margin-top:6px\">" + title + "</div></div><div style=\"padding:24px 28px;font-size:15px;line-height:1.6\">" + body
                + "</div><div style=\"padding:14px 28px;background:#fff7ed;font-size:12px;color:#9a3412\">KP Horoscope - Krishnamurti Paddhati</div></div></body></html>";
    }

    public void email(String to, String subject, String title, String bodyHtml) {
        pool.submit(() -> {
            Notification n = new Notification();
            n.channel = "email"; n.to = to; n.subject = subject; n.status = "logged";
            try {
                if (sendEmailNow(to, subject, title, bodyHtml)) n.status = "sent";
                else log.info("[email:not-configured] to={} subject=\"{}\"", to, subject);
            } catch (Exception e) { n.status = "failed"; n.error = trim(rootMessage(e)); log.warn("email failed: {}", rootMessage(e)); }
            db.notifications.save(n);
        });
    }

    static String trim(String s) { return s == null ? null : s.length() > 900 ? s.substring(0, 900) : s; }

    String phone(String p) {
        if (p == null) return "";
        String d = p.replaceAll("[^\\d+]", "");
        if (d.startsWith("+")) return d.substring(1);
        if (d.length() == 10) return env.get("DEFAULT_COUNTRY_CODE", "91") + d;
        return d.replaceFirst("^0+", "");
    }

    public void whatsapp(String rawPhone, String text) {
        pool.submit(() -> {
            Notification n = new Notification();
            n.channel = "whatsapp"; n.to = phone(rawPhone); n.text = trim(text);
            try { n.status = sendWhatsAppNow(rawPhone, text); }
            catch (Exception e) { n.status = "failed"; n.error = trim(rootMessage(e)); log.warn("whatsapp failed: {}", rootMessage(e)); }
            db.notifications.save(n);
        });
    }

    /** Sends immediately. Returns "sent", "skipped" (no number) or "logged" (not configured); throws with the provider error. */
    String sendWhatsAppNow(String rawPhone, String text) throws Exception {
        String to = phone(rawPhone);
        String provider = env.get("WHATSAPP_PROVIDER").toLowerCase(Locale.ROOT);
        {
            {
                if (to.isEmpty()) return "skipped";
                else if (provider.equals("meta") && env.has("META_WA_TOKEN") && env.has("META_WA_PHONE_ID")) {
                    Map<String, Object> body = env.has("META_WA_TEMPLATE")
                            ? Map.of("messaging_product", "whatsapp", "to", to, "type", "template", "template", Map.of("name", env.get("META_WA_TEMPLATE"),
                                "language", Map.of("code", env.get("META_WA_TEMPLATE_LANG", "en")),
                                "components", List.of(Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", templateText(text)))))))
                            : Map.of("messaging_product", "whatsapp", "to", to, "type", "text", "text", Map.of("body", text));
                    HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://graph.facebook.com/v20.0/" + env.get("META_WA_PHONE_ID") + "/messages"))
                            .header("Authorization", "Bearer " + env.get("META_WA_TOKEN")).header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() >= 300) throw new IllegalStateException("Meta WhatsApp " + r.statusCode() + ": " + r.body()
                            + (env.has("META_WA_TEMPLATE") ? "" : "  →  Meta only delivers free-text messages to people who messaged your business in the last 24 hours. Create and approve a message template and enter its name."));
                    return "sent";
                } else if (provider.equals("twilio") && env.has("TWILIO_SID") && env.has("TWILIO_TOKEN") && env.has("TWILIO_WA_FROM")) {
                    String form = "From=" + enc("whatsapp:" + env.get("TWILIO_WA_FROM")) + "&To=" + enc("whatsapp:+" + to) + "&Body=" + enc(text);
                    HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + env.get("TWILIO_SID") + "/Messages.json"))
                            .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((env.get("TWILIO_SID") + ":" + env.get("TWILIO_TOKEN")).getBytes(StandardCharsets.UTF_8)))
                            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() >= 300) throw new IllegalStateException("Twilio " + r.statusCode() + ": " + r.body()
                            + "  →  With the Twilio sandbox, each receiver must first send the 'join <code>' message to the sandbox number.");
                    return "sent";
                } else {
                    log.info("[whatsapp:not-configured] to=+{}", to);
                    return "logged";
                }
            }
        }
    }

    /** Meta template parameters may not contain new lines, tabs or more than 4 spaces in a row. */
    static String templateText(String text) {
        String t = text.replaceAll("\\s*\\n+\\s*", " | ").replaceAll("\\s{2,}", " ").trim();
        return t.length() > 1000 ? t.substring(0, 1000) : t;
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private void toAdmin(String subject, String title, String html, String waText) {
        String adminEmail = env.get("ADMIN_NOTIFY_EMAIL", env.get("ADMIN_EMAIL"));
        if (!adminEmail.isEmpty()) email(adminEmail, subject, title, html);
        if (env.has("ADMIN_WHATSAPP")) whatsapp(env.get("ADMIN_WHATSAPP"), waText);
    }

    /** Password-reset OTP. Returns true if the email was actually sent. */
    public boolean passwordOtp(User u, String otp) {
        String body = "<p>Dear " + esc(u.fullName) + ",</p><p>Use this one-time password to reset your " + esc(env.siteName()) + " password:</p>"
                + "<p style=\"font-size:30px;letter-spacing:8px;font-weight:700;color:#c2410c;background:#fff7ed;padding:14px 20px;border-radius:12px;display:inline-block\">" + otp + "</p>"
                + "<p>It is valid for 10 minutes. If you did not ask for this, you can ignore this email.</p>";
        boolean sent;
        Notification n = sentRecord("email", u.email, "Password reset code", null, "logged", null);
        try { sent = sendEmailNow(u.email, env.siteName() + " - password reset code " + otp, "Your password reset code", body); n.status = sent ? "sent" : "logged"; }
        catch (Exception e) { sent = false; n.status = "failed"; n.error = trim(rootMessage(e)); log.warn("OTP email failed: {}", rootMessage(e)); }
        if (!sent) log.info("[password-reset] OTP for {} is {} (email not delivered: configure SMTP in Admin > Settings)", u.email, otp);
        db.notifications.save(n);
        if (u.phone != null && !u.phone.isBlank()) whatsapp(u.phone, "Your " + env.siteName() + " password reset code is " + otp + ". It is valid for 10 minutes.");
        return sent;
    }

    // ------------------------------------------------------------------ events
    public void registered(User u, String password) {
        String login = env.siteUrl() + "/login.html";
        email(u.email, "Welcome to " + env.siteName() + " - your login details", "Your KP Horoscope account is ready",
                "<p>Namaste <b>" + esc(u.fullName) + "</b>,</p><p>Your account has been created. Chat with our KP astrologer to get your personalised report.</p>"
                        + "<table style=\"background:#fff7ed;border-radius:10px;padding:12px 16px;margin:12px 0\"><tr><td>Login ID</td><td><b>" + esc(u.username) + "</b></td></tr>"
                        + "<tr><td>Email</td><td><b>" + esc(u.email) + "</b></td></tr><tr><td>Password</td><td><b>" + esc(password) + "</b></td></tr></table>"
                        + "<p><a href=\"" + login + "\" style=\"background:#ea580c;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none\">Login to your portal</a></p>");
        whatsapp(u.phone, "Namaste " + u.fullName + "!\nYour " + env.siteName() + " KP Horoscope account is ready.\n\nLogin ID: " + u.username + "\nPassword: " + password + "\n\nLogin: " + login);
        toAdmin("New registration: " + u.fullName, "New customer registered", details(u, null), "New registration: " + u.fullName + " (" + u.phone + ", " + u.email + ")");
    }

    String details(User u, Chart c) {
        StringBuilder s = new StringBuilder("<table style=\"background:#fff7ed;border-radius:10px;padding:12px 16px\">");
        String[][] rows = {{"Name", u.fullName}, {"Email", u.email}, {"WhatsApp", u.phone}, {"Login ID", u.username}, {"Gender", u.gender}, {"Date of birth", u.dob},
                {"Time of birth", u.tob}, {"Birth place", u.city}, {"Lat / Lon", u.lat + ", " + u.lon}, {"UTC offset", u.tz + " (" + u.tzName + ")"}};
        for (String[] r : rows) s.append("<tr><td style=\"padding:2px 12px 2px 0;color:#9a3412\">").append(r[0]).append("</td><td><b>").append(esc(r[1])).append("</b></td></tr>");
        if (c != null) {
            s.append("<tr><td style=\"padding:2px 12px 2px 0;color:#9a3412\">Ascendant</td><td><b>").append(c.ascendant.sign).append(" ").append(c.ascendant.dms).append("</b></td></tr>")
             .append("<tr><td style=\"padding:2px 12px 2px 0;color:#9a3412\">Moon / Nakshatra</td><td><b>").append(c.moonSign).append(" / ").append(c.nakshatra).append("</b></td></tr>")
             .append("<tr><td style=\"padding:2px 12px 2px 0;color:#9a3412\">Running dasha</td><td><b>").append(c.currentDasha.mahadasha).append(" / ").append(c.currentDasha.antardasha).append("</b></td></tr>");
        }
        return s.append("</table>").toString();
    }

    /** Client started a consultation chat: send their full details (and the person consulted for) to the admin. */
    public void chatStarted(User account, Profile p, Chart c) {
        boolean self = "Self".equals(p.relation);
        String forWhom = self ? "" : "<p>Consultation is for: <b>" + esc(p.fullName) + "</b> (" + esc(p.relation) + ") — born " + p.dob + " " + p.tob + ", " + esc(p.city) + "</p>";
        toAdmin("Consultation started: " + account.fullName + (self ? "" : " for " + p.fullName), "A client started a consultation chat",
                "<p><b>" + esc(account.fullName) + "</b> just started a chat with the astrologer (consultation #" + p.chatSession + ").</p>" + details(account, self ? c : null) + forWhom
                        + (self ? "" : "<p>Chart of " + esc(p.fullName) + ": " + c.ascendant.sign + " ascendant, Moon " + c.moonSign + " (" + c.nakshatra + "), dasha " + c.currentDasha.mahadasha + "/" + c.currentDasha.antardasha + "</p>")
                        + "<p><a href=\"" + env.siteUrl() + "/admin.html\">Open admin portal</a></p>",
                "Consultation started\nClient: " + account.fullName + "\nPhone: " + account.phone + "\nEmail: " + account.email
                        + (self ? "" : "\nFor: " + p.fullName + " (" + p.relation + ")")
                        + "\nDOB: " + p.dob + " " + p.tob + "\nPlace: " + p.city
                        + "\nAsc: " + c.ascendant.sign + ", Moon: " + c.moonSign + " (" + c.nakshatra + ")\nDasha: " + c.currentDasha.mahadasha + "/" + c.currentDasha.antardasha);
        whatsapp(account.phone, "" + account.fullName + ", you are now connected with our KP astrologer" + (self ? "" : " for " + p.fullName) + ". Continue here: " + env.siteUrl() + "/client.html");
    }

    public void reportReady(User u, Report r) {
        email(u.email, "Your KP Horoscope report is ready", "Your report is ready ", "<p>Dear " + esc(u.fullName) + ", your " + r.pages + "-page KP horoscope report (" + esc(r.title)
                + ") is ready. Page 1 is free to read; unlock all pages in your portal.</p><p><a href=\"" + env.siteUrl() + "/client.html#report\">Open my report</a></p>");
        whatsapp(u.phone, "" + u.fullName + ", your " + r.pages + "-page KP report is ready! Read page 1 free: " + env.siteUrl() + "/client.html#report");
        toAdmin("Report generated: " + u.fullName, "Report generated", "<p>" + esc(u.fullName) + " generated a " + r.pages + "-page report: " + esc(r.title) + "</p>", "Report generated for " + u.fullName + ": " + r.title);
    }

    public void paymentSuccess(User u, Payment p) {
        String what = p.purpose.equals("report") ? "Full KP Horoscope Report" : "30-min KP Consultation";
        String amt = "₹" + (p.amount / 100);
        email(u.email, "Payment received - " + what, "Payment successful ", "<p>Dear " + esc(u.fullName) + ", we received " + amt + " for <b>" + what + "</b>.</p><p>Payment ID: " + p.razorpayPaymentId + "</p>");
        whatsapp(u.phone, "Payment of " + amt + " received for " + what + ". Payment ID: " + p.razorpayPaymentId + ". Thank you!");
        toAdmin("Payment: " + amt + " from " + u.fullName, "Payment received", "<p>" + esc(u.fullName) + " paid " + amt + " for " + what + " (" + p.razorpayPaymentId + ")</p>", "" + u.fullName + " paid " + amt + " for " + what);
    }

    public void consultationBooked(User u, Consultation c) {
        email(u.email, "Consultation booked", "Your 30-min KP consultation is booked ", "<p>Dear " + esc(u.fullName) + ", your consultation is booked for <b>" + c.preferredDate + " " + c.preferredTime + "</b>. We will confirm and share the meeting link.</p>");
        whatsapp(u.phone, "Your 30-min KP consultation is booked for " + c.preferredDate + " " + c.preferredTime + ". We'll share the meeting link soon.");
        toAdmin("New consultation: " + u.fullName, "New consultation booking", "<p>" + esc(u.fullName) + " booked " + c.preferredDate + " " + c.preferredTime + ". Topic: " + esc(c.topic) + "</p>" + details(u, null),
                "New consultation: " + u.fullName + " (" + u.phone + ") on " + c.preferredDate + " " + c.preferredTime);
    }

    public void consultationUpdated(User u, Consultation c) {
        String when = c.scheduledAt != null && !c.scheduledAt.isBlank() ? c.scheduledAt : c.preferredDate + " " + c.preferredTime;
        email(u.email, "Consultation " + c.status, "Consultation " + c.status, "<p>Dear " + esc(u.fullName) + ", your consultation on <b>" + esc(when) + "</b> is <b>" + c.status + "</b>.</p>"
                + (c.meetingLink != null && !c.meetingLink.isBlank() ? "<p>Meeting link: <a href=\"" + esc(c.meetingLink) + "\">" + esc(c.meetingLink) + "</a></p>" : ""));
        whatsapp(u.phone, "Your KP consultation (" + when + ") is " + c.status + "." + (c.meetingLink != null && !c.meetingLink.isBlank() ? "\nMeeting link: " + c.meetingLink : ""));
    }
}
