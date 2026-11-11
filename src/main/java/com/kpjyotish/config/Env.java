package com.kpjyotish.config;

import com.kpjyotish.store.Entities.Setting;
import com.kpjyotish.store.Repos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Settings: values saved by the admin in Admin > Settings > Integrations (database) win over
 * .env / environment variables, so keys can be added without editing files or restarting.
 */
@Component
public class Env {
    public static final String PREFIX = "cfg.";
    private final Environment env;
    private final ObjectProvider<Repos> repos;
    private volatile Map<String, String> overrides;

    public Env(Environment env, ObjectProvider<Repos> repos) { this.env = env; this.repos = repos; }

    private Map<String, String> overrides() {
        Map<String, String> o = overrides;
        if (o == null) {
            o = new ConcurrentHashMap<>();
            try {
                for (Setting s : repos.getObject().settings.findAll())
                    if (s.key.startsWith(PREFIX) && s.value != null && !s.value.isBlank()) o.put(s.key.substring(PREFIX.length()), s.value);
            } catch (Exception e) { return Map.of(); } // database not ready yet
            overrides = o;
        }
        return o;
    }

    /** Called after the admin saves integration settings. */
    public void reload() { overrides = null; }

    public String get(String key) { return get(key, ""); }

    public String get(String key, String def) {
        String v = overrides().get(key);
        if (v == null) v = env.getProperty(key);
        if (v == null) v = System.getenv(key);
        if (v == null) return def;
        v = v.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) v = v.substring(1, v.length() - 1);
        return v.isEmpty() ? def : v;
    }

    /** "admin" if set in the admin panel, "env" if from .env, "" if not set. */
    public String source(String key) {
        if (overrides().containsKey(key)) return "admin";
        String v = env.getProperty(key);
        if (v == null) v = System.getenv(key);
        return v != null && !v.isBlank() ? "env" : "";
    }

    public boolean has(String key) { return !get(key).isEmpty(); }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    public String siteName() { return get("SITE_NAME", "Astrology Talk"); }

    public String siteUrl() { return get("SITE_URL", "http://localhost:" + get("PORT", "8090")); }
}
