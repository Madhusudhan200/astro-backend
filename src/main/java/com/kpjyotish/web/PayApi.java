package com.kpjyotish.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpjyotish.config.ApiException;
import com.kpjyotish.config.Env;
import com.kpjyotish.service.Auth;
import com.kpjyotish.service.Notifier;
import com.kpjyotish.service.Razorpay;
import com.kpjyotish.store.Entities.*;
import com.kpjyotish.store.Repos;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Razorpay payments for the full report and the 30-min consultation. */
@RestController
@RequestMapping("/api/pay")
public class PayApi {
    private static final Logger log = LoggerFactory.getLogger(PayApi.class);
    private final Repos db; private final Auth auth; private final Razorpay rzp; private final Notifier notify; private final Env env;

    public PayApi(Repos db, Auth auth, Razorpay rzp, Notifier notify, Env env) { this.db = db; this.auth = auth; this.rzp = rzp; this.notify = notify; this.env = env; }

    int price(String purpose) {
        return purpose.equals("report") ? Integer.parseInt(db.setting("reportPrice", env.get("REPORT_PRICE", "499")))
                : Integer.parseInt(db.setting("consultPrice", env.get("CONSULT_PRICE", "999")));
    }

    @PostMapping("/order")
    public Map<String, Object> order(@RequestBody Map<String, String> b, HttpServletRequest req) throws Exception {
        User u = auth.require(req, "client");
        String purpose = String.valueOf(b.get("purpose"));
        Payment p = new Payment();
        if (purpose.equals("report")) {
            Report r = db.reports.findById(String.valueOf(b.get("reportId"))).filter(x -> x.userId.equals(u.id)).orElseThrow(() -> new ApiException(404, "Report not found"));
            if (r.unlocked) throw new ApiException(400, "This report is already unlocked");
            if (!"ready".equals(r.status)) throw new ApiException(400, "Report is still being prepared");
            p.reportId = r.id;
        } else if (purpose.equals("consultation")) {
            p.preferredDate = PublicApi.clean(b.get("preferredDate"), 10);
            p.preferredTime = PublicApi.clean(b.get("preferredTime"), 5);
            p.topic = PublicApi.clean(b.get("topic"), 300);
            if (!p.preferredDate.matches("\\d{4}-\\d{2}-\\d{2}") || !p.preferredTime.matches("\\d{2}:\\d{2}")) throw new ApiException(400, "Choose a preferred date and time");
            if (p.preferredDate.compareTo(LocalDate.now().toString()) < 0) throw new ApiException(400, "Choose a future date");
        } else throw new ApiException(400, "Invalid purpose");
        long amount = price(purpose) * 100L;
        Razorpay.Order o;
        try { o = rzp.createOrder(amount, purpose + "_" + System.currentTimeMillis(), Map.of("userId", u.id, "purpose", purpose)); }
        catch (Exception e) { log.error("razorpay order failed", e); throw new ApiException(502, "Could not start payment. Please try again."); }
        p.userId = u.id; p.purpose = purpose; p.amount = amount; p.orderId = o.id(); p.status = "created"; p.demo = o.demo();
        db.payments.save(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.id()); m.put("amount", amount); m.put("currency", "INR"); m.put("key", rzp.live() ? rzp.keyId() : null); m.put("demo", o.demo());
        m.put("name", env.siteName()); m.put("description", purpose.equals("report") ? "Full KP Horoscope Report" : "30-min KP Consultation");
        m.put("prefill", Map.of("name", u.fullName, "email", u.email, "contact", u.phone == null ? "" : u.phone));
        return m;
    }

    @PostMapping("/verify")
    public Map<String, Boolean> verify(@RequestBody Map<String, String> b, HttpServletRequest req) {
        User u = auth.require(req, "client");
        Payment p = db.payments.findByOrderId(String.valueOf(b.get("orderId"))).filter(x -> x.userId.equals(u.id)).orElseThrow(() -> new ApiException(404, "Order not found"));
        if ("paid".equals(p.status)) return Map.of("ok", true);
        if (!p.demo && !rzp.verify(p.orderId, b.get("paymentId"), b.get("signature"))) {
            p.status = "failed"; db.payments.save(p);
            throw new ApiException(400, "Payment verification failed");
        }
        String pid = p.demo ? "pay_demo_" + Integer.toHexString(new SecureRandom().nextInt()) : b.get("paymentId");
        fulfil(p, pid, b.get("signature"));
        return Map.of("ok", true);
    }

    synchronized void fulfil(Payment p, String paymentId, String signature) {
        if ("paid".equals(p.status)) return;
        p.status = "paid"; p.razorpayPaymentId = paymentId; p.signature = signature; p.paidAt = Instant.now().toString();
        db.payments.save(p);
        User u = db.users.findById(p.userId).orElseThrow();
        if (p.purpose.equals("report") && p.reportId != null) db.reports.findById(p.reportId).ifPresent(r -> {
            // unlock the report and all its language versions
            Report root = r.parentId != null ? db.reports.findById(r.parentId).orElse(r) : r;
            root.unlocked = true; db.reports.save(root);
            for (Report t : db.reports.findByParentId(root.id)) { t.unlocked = true; db.reports.save(t); }
            r.unlocked = true; db.reports.save(r);
        });
        if (p.purpose.equals("consultation")) {
            Consultation c = new Consultation();
            c.userId = u.id; c.paymentId = p.id; c.status = "requested"; c.preferredDate = p.preferredDate; c.preferredTime = p.preferredTime; c.topic = p.topic;
            db.consultations.save(c);
            notify.consultationBooked(u, c);
        }
        notify.paymentSuccess(u, p);
    }

    void onWebhook(byte[] raw) {
        try {
            JsonNode evt = new ObjectMapper().readTree(raw);
            String type = evt.path("event").asText();
            if (type.equals("payment.captured") || type.equals("order.paid")) {
                JsonNode ent = evt.path("payload").path("payment").path("entity");
                db.payments.findByOrderId(ent.path("order_id").asText()).ifPresent(p -> fulfil(p, ent.path("id").asText(), null));
            }
        } catch (Exception e) { log.warn("webhook error: {}", e.getMessage()); }
    }
}
