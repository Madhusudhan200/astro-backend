package com.kpjyotish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpjyotish.config.Env;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Razorpay Orders API + signature verification. Razorpay Checkout supports UPI
 * (PhonePe, Google Pay, Paytm, BHIM), cards, net banking and wallets.
 * Without RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET the site runs in DEMO payment mode.
 */
@Component
public class Razorpay {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Env env;

    public Razorpay(Env env) { this.env = env; }

    public boolean live() { return env.has("RAZORPAY_KEY_ID") && env.has("RAZORPAY_KEY_SECRET"); }
    public String keyId() { return env.get("RAZORPAY_KEY_ID"); }

    public record Order(String id, long amount, boolean demo) {}

    public Order createOrder(long amountPaise, String receipt, Map<String, String> notes) throws Exception {
        if (!live()) {
            byte[] b = new byte[6]; new SecureRandom().nextBytes(b);
            return new Order("order_demo_" + HexFormat.of().formatHex(b), amountPaise, true);
        }
        String body = JSON.writeValueAsString(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes));
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/orders"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((keyId() + ":" + env.get("RAZORPAY_KEY_SECRET")).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("Razorpay order failed: " + r.statusCode() + " " + r.body());
        JsonNode j = JSON.readTree(r.body());
        return new Order(j.get("id").asText(), j.get("amount").asLong(), false);
    }

    static String hmac(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }

    public boolean verify(String orderId, String paymentId, String signature) {
        if (!live()) return true;
        try {
            String expected = hmac(env.get("RAZORPAY_KEY_SECRET"), (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            return signature != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { return false; }
    }

    /** GET from the Razorpay API (payments, settlements...) for the admin gateway dashboard. */
    public JsonNode get(String path) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/" + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((keyId() + ":" + env.get("RAZORPAY_KEY_SECRET")).getBytes(StandardCharsets.UTF_8)))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("Razorpay " + r.statusCode() + ": " + r.body());
        return JSON.readTree(r.body());
    }

    public boolean verifyWebhook(byte[] raw, String signature) {
        if (!env.has("RAZORPAY_WEBHOOK_SECRET") || signature == null) return false;
        try {
            return MessageDigest.isEqual(hmac(env.get("RAZORPAY_WEBHOOK_SECRET"), raw).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { return false; }
    }
}
