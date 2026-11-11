package com.kpjyotish.store;

import jakarta.persistence.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * All database tables. Entities are static nested classes with public fields
 * (serialised to JSON as-is for the frontend).
 */
public final class Entities {
    private Entities() {}

    private static final SecureRandom RND = new SecureRandom();

    public static String newId(String prefix) {
        byte[] b = new byte[8];
        RND.nextBytes(b);
        return prefix + "_" + HexFormat.of().formatHex(b);
    }

    @MappedSuperclass
    public abstract static class Base {
        @Id @Column(length = 40) public String id;
        public String createdAt;
        public String updatedAt;

        protected abstract String prefix();

        @PrePersist
        void prePersist() {
            if (id == null) id = newId(prefix());
            if (createdAt == null) createdAt = Instant.now().toString();
        }

        @PreUpdate
        void preUpdate() { updatedAt = Instant.now().toString(); }
    }

    @Entity @Table(name = "users", indexes = {@Index(columnList = "email", unique = true), @Index(columnList = "username")})
    public static class User extends Base {
        public String role;          // client | admin
        public String fullName;
        public String email;
        public String username;
        public String phone;
        @com.fasterxml.jackson.annotation.JsonIgnore public String passwordHash;
        public String dob;           // YYYY-MM-DD
        public String tob;           // HH:MM:SS
        public String gender;
        @Column(length = 400) public String city;
        public Double lat;
        public Double lon;
        public Double tz;            // UTC offset hours at birth
        public String tzName;        // IANA zone
        public int chatSession = 1;  // current consultation session number
        public String language = "en"; // website language chosen in My Profile

        protected String prefix() { return "usr"; }
    }

    /**
     * A person whose horoscope is read: the account holder ("Self", created at registration and locked)
     * or a family member / friend added by them. Chats, memory and reports belong to a profile.
     */
    @Entity @Table(name = "profiles", indexes = @Index(columnList = "userId"))
    public static class Profile extends Base {
        public String userId;
        public String relation;      // Self | Mother | Father | Spouse | Son | Daughter | Brother | Sister | Friend | Other
        public String fullName;
        public String dob;
        public String tob;
        public String gender;
        @Column(length = 400) public String city;
        public Double lat;
        public Double lon;
        public Double tz;
        public String tzName;
        public int chatSession = 1;

        protected String prefix() { return "prf"; }

        /** Transient User view with this person's birth data (used by the chart, AI and PDF code). */
        public User asPerson() {
            User u = new User();
            u.id = id; u.fullName = fullName; u.dob = dob; u.tob = tob; u.gender = gender; u.city = city;
            u.lat = lat; u.lon = lon; u.tz = tz; u.tzName = tzName; u.chatSession = chatSession;
            return u;
        }
    }

    @Entity @Table(name = "reports", indexes = @Index(columnList = "userId"))
    public static class Report extends Base {
        public String userId;
        public String profileId;
        public int session;
        @Column(length = 300) public String title;
        @Column(length = 300) public String topic;
        public String status;        // generating | ready | failed
        public Integer pages;
        public String source;
        @Column(length = 20000) public String hookJson;
        @Column(length = 2000) public String error;
        public boolean unlocked;
        public String generatedAt;
        public String language = "en";   // PDF language code
        public String parentId;          // set on a translated copy of another report
        @com.fasterxml.jackson.annotation.JsonIgnore @Column(length = 400000) public String contentJson; // English report content (reused for translations)
        @com.fasterxml.jackson.annotation.JsonIgnore @Column(length = 3000000) public String translationsJson; // English -> target text, supplied by the browser translator

        protected String prefix() { return "rep"; }
    }

    @Entity @Table(name = "payments", indexes = {@Index(columnList = "userId"), @Index(columnList = "orderId")})
    public static class Payment extends Base {
        public String userId;
        public String purpose;       // report | consultation
        public long amount;          // paise
        public String orderId;
        public String status;        // created | paid | failed
        public String razorpayPaymentId;
        @Column(length = 300) public String signature;
        public boolean demo;
        public String reportId;
        public String preferredDate;
        public String preferredTime;
        @Column(length = 400) public String topic;
        public String paidAt;

        protected String prefix() { return "pay"; }
    }

    @Entity @Table(name = "consultations", indexes = @Index(columnList = "userId"))
    public static class Consultation extends Base {
        public String userId;
        public String paymentId;
        public String status;        // requested | confirmed | completed | cancelled
        public int durationMin = 30;
        public String preferredDate;
        public String preferredTime;
        @Column(length = 400) public String topic;
        public String scheduledAt;
        @Column(length = 400) public String meetingLink;
        @Column(length = 1200) public String notes;

        protected String prefix() { return "con"; }
    }

    @Entity @Table(name = "notifications")
    public static class Notification extends Base {
        public String channel;       // email | whatsapp
        @Column(name = "to_addr") public String to;
        @Column(length = 400) public String subject;
        @Column(length = 4000) public String text;
        public String status;        // sent | logged | failed | skipped
        @Column(length = 1000) public String error;

        protected String prefix() { return "not"; }
    }

    /** One chat message between the client and the AI astrologer. */
    @Entity @Table(name = "chat_messages", indexes = @Index(columnList = "userId,session"))
    public static class ChatMessage extends Base {
        public String userId;
        public String profileId;
        public int session;
        public String role;          // user | assistant
        @Column(length = 20000) public String text;

        protected String prefix() { return "msg"; }
    }

    /** State of one consultation session (topic, readiness, rule-based interview step). */
    @Entity @Table(name = "chat_sessions", indexes = @Index(columnList = "userId,session"))
    public static class ChatSession extends Base {
        public String userId;
        public String profileId;
        public int session;
        public String reportId;      // report generated from this consultation (one per reason)
        @Column(length = 300) public String topic;
        public String topicKey;
        public int stage;
        public boolean readyForReport;

        protected String prefix() { return "ses"; }
    }

    /** Long-term memory: facts the astrologer remembers about the client. */
    @Entity @Table(name = "memory_facts", indexes = @Index(columnList = "userId"))
    public static class MemoryFact extends Base {
        public String userId;
        public String profileId;
        @Column(length = 500) public String fact;

        protected String prefix() { return "mem"; }
    }

    /** One-time password for "forgot password" (stored hashed, expires in 10 minutes). */
    @Entity @Table(name = "password_resets", indexes = @Index(columnList = "email"))
    public static class PasswordReset extends Base {
        public String email;
        public String otpHash;
        public long expiresAt;
        public int attempts;
        public boolean used;

        protected String prefix() { return "otp"; }
    }

    @Entity @Table(name = "settings")
    public static class Setting {
        @Id @Column(name = "setting_key") public String key;
        @Column(name = "setting_value", length = 2000) public String value;
    }
}
