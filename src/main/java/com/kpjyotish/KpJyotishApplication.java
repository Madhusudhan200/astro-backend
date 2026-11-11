package com.kpjyotish;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@SpringBootApplication(exclude = MailSenderAutoConfiguration.class)
@EnableAsync
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(considerNestedRepositories = true)
public class KpJyotishApplication {
    public static void main(String[] args) {
        usePostgresUrlIfGiven();
        SpringApplication.run(KpJyotishApplication.class, args);
    }

    /**
     * Accepts the connection string exactly as Neon / Supabase / Render show it
     * (postgresql://user:password@host/db?sslmode=require) in DB_URL or DATABASE_URL,
     * and converts it to the JDBC form Spring needs.
     */
    static void usePostgresUrlIfGiven() {
        String raw = System.getenv("DB_URL");
        if (raw == null || raw.isBlank()) raw = System.getenv("DATABASE_URL");
        if (raw == null || raw.isBlank()) return;
        raw = raw.trim();
        if (raw.startsWith("jdbc:")) { System.setProperty("DB_URL", raw); return; }
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) return;
        URI u = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
        String query = u.getRawQuery() == null ? "sslmode=require" : u.getRawQuery().replace("channel_binding=require", "").replaceAll("&&+", "&").replaceAll("^&|&$", "");
        if (!query.contains("sslmode")) query += (query.isEmpty() ? "" : "&") + "sslmode=require";
        System.setProperty("DB_URL", "jdbc:postgresql://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + u.getRawPath() + "?" + query);
        String info = u.getRawUserInfo();
        if (info != null) {
            String[] up = info.split(":", 2);
            System.setProperty("DB_USER", URLDecoder.decode(up[0], StandardCharsets.UTF_8));
            if (up.length > 1) System.setProperty("DB_PASSWORD", URLDecoder.decode(up[1], StandardCharsets.UTF_8));
        }
    }
}
