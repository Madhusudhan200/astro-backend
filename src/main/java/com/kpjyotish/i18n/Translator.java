package com.kpjyotish.i18n;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpjyotish.config.ApiException;
import com.kpjyotish.config.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Translates report text into another language.
 * Provider 1: Google Cloud Translation API (GOOGLE_TRANSLATE_API_KEY) - fast, all languages.
 * Provider 2: the smart-answers key (Anthropic) if set.
 */
@Component
public class Translator {
    private static final Logger log = LoggerFactory.getLogger(Translator.class);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Env env;

    public Translator(Env env) { this.env = env; }

    public boolean available() { return env.has("GOOGLE_TRANSLATE_API_KEY") || env.has("ANTHROPIC_API_KEY"); }

    /** Translates unique non-trivial strings; returns a map original -> translated. */
    public Map<String, String> translate(Collection<String> texts, String target) throws Exception {
        Lang l = Lang.get(target);
        if (l == null) throw new ApiException(400, "Unknown language");
        List<String> todo = new ArrayList<>();
        for (String t : new LinkedHashSet<>(texts)) if (t != null && t.matches("(?s).*[A-Za-z]{2,}.*")) todo.add(t);
        Map<String, String> out = new HashMap<>();
        if (todo.isEmpty()) return out;
        if (env.has("GOOGLE_TRANSLATE_API_KEY")) google(todo, target, out);
        else if (env.has("ANTHROPIC_API_KEY")) claude(todo, l, out);
        else throw new ApiException(400, "Report translation is not set up yet. The site owner must add a translation key in Admin > Settings > Integrations.");
        return out;
    }

    // ---- Google Cloud Translation v2 (batches of up to 100 strings / ~25k chars)
    void google(List<String> todo, String target, Map<String, String> out) throws Exception {
        List<String> batch = new ArrayList<>();
        int chars = 0;
        for (String t : todo) {
            if (!batch.isEmpty() && (batch.size() >= 100 || chars + t.length() > 25000)) { googleBatch(batch, target, out); batch.clear(); chars = 0; }
            batch.add(t); chars += t.length();
        }
        if (!batch.isEmpty()) googleBatch(batch, target, out);
    }

    void googleBatch(List<String> batch, String target, Map<String, String> out) throws Exception {
        StringBuilder form = new StringBuilder("target=" + URLEncoder.encode(target, StandardCharsets.UTF_8) + "&source=en&format=text");
        for (String t : batch) form.append("&q=").append(URLEncoder.encode(t, StandardCharsets.UTF_8));
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://translation.googleapis.com/language/translate/v2?key=" + URLEncoder.encode(env.get("GOOGLE_TRANSLATE_API_KEY"), StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(90)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("Translation failed (" + r.statusCode() + "): " + r.body());
        JsonNode arr = JSON.readTree(r.body()).path("data").path("translations");
        for (int i = 0; i < batch.size() && i < arr.size(); i++) out.put(batch.get(i), arr.get(i).path("translatedText").asText());
    }

    // ---- Anthropic (structured output: list of translations in the same order)
    public record Batch(List<String> translations) {}

    void claude(List<String> todo, Lang l, Map<String, String> out) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(env.get("ANTHROPIC_API_KEY")).build();
        List<String> batch = new ArrayList<>();
        int chars = 0;
        List<List<String>> batches = new ArrayList<>();
        for (String t : todo) {
            if (!batch.isEmpty() && (batch.size() >= 60 || chars + t.length() > 12000)) { batches.add(batch); batch = new ArrayList<>(); chars = 0; }
            batch.add(t); chars += t.length();
        }
        if (!batch.isEmpty()) batches.add(batch);
        for (List<String> b : batches) {
            StringBuilder p = new StringBuilder("Translate each of the following " + b.size() + " English texts from a KP astrology report into " + l.name()
                    + " (" + l.nativeName() + "). Keep numbers, dates, degree values and abbreviations like Su, Mo, KP unchanged; use natural, respectful astrology vocabulary. Return exactly "
                    + b.size() + " translations in the same order.\n\n");
            for (int i = 0; i < b.size(); i++) p.append("[").append(i + 1).append("] ").append(b.get(i)).append("\n");
            StructuredMessageCreateParams<Batch> params = MessageCreateParams.builder().model(env.get("AI_MODEL", "claude-opus-5-5")).maxTokens(16000L)
                    .outputConfig(Batch.class).addUserMessage(p.toString()).build();
            Batch res = client.messages().create(params).content().stream().flatMap(c -> c.text().stream()).map(t -> t.text()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Translation returned nothing"));
            for (int i = 0; i < b.size() && i < res.translations().size(); i++) out.put(b.get(i), res.translations().get(i));
        }
        log.info("translated {} strings into {}", todo.size(), l.name());
    }
}
