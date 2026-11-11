package com.kpjyotish.service;

import com.kpjyotish.config.ApiException;
import com.kpjyotish.config.Env;
import com.kpjyotish.store.Entities.User;
import com.kpjyotish.store.Repos;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Date;

/** JWT cookie sessions + password hashing. */
@Component
public class Auth {
    private static final Logger log = LoggerFactory.getLogger(Auth.class);
    public static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);
    private final SecretKey key;
    private final boolean secureCookie;
    private final Repos db;

    public Auth(Env env, Repos db) throws Exception {
        this.db = db;
        String secret = env.get("JWT_SECRET");
        if (secret.isEmpty() || secret.startsWith("change-me")) {
            byte[] r = new byte[32];
            new SecureRandom().nextBytes(r);
            secret = new String(r, StandardCharsets.ISO_8859_1);
            log.warn("JWT_SECRET not set - using a random one (sessions reset on restart)");
        }
        this.key = Keys.hmacShaKeyFor(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        this.secureCookie = "true".equals(env.get("COOKIE_SECURE"));
    }

    public void login(HttpServletResponse res, User u) {
        String token = Jwts.builder().subject(u.id).claim("role", u.role)
                .expiration(new Date(System.currentTimeMillis() + 7L * 86400000)).signWith(key).compact();
        res.addHeader("Set-Cookie", "token=" + token + "; Path=/; Max-Age=" + 7 * 86400 + "; HttpOnly; SameSite=Lax" + (secureCookie ? "; Secure" : ""));
    }

    public void logout(HttpServletResponse res) {
        res.addHeader("Set-Cookie", "token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    }

    /** Current user or 401; with role, 403 if the role differs. */
    public User require(HttpServletRequest req, String role) {
        String token = null;
        if (req.getCookies() != null) for (Cookie c : req.getCookies()) if (c.getName().equals("token")) token = c.getValue();
        if (token == null || token.isEmpty()) throw new ApiException(401, "Please login");
        User u;
        try {
            Claims cl = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            u = db.users.findById(cl.getSubject()).orElse(null);
        } catch (Exception e) {
            throw new ApiException(401, "Please login");
        }
        if (u == null) throw new ApiException(401, "Please login");
        if (role != null && !role.equals(u.role)) throw new ApiException(403, "Forbidden");
        return u;
    }
}
