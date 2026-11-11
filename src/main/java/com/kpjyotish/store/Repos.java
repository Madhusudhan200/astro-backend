package com.kpjyotish.store;

import com.kpjyotish.store.Entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** All repositories, injected as one bean for convenience. */
@Component
public class Repos {
    public interface Users extends JpaRepository<User, String> {
        Optional<User> findByEmail(String email);
        Optional<User> findByUsernameIgnoreCase(String username);
        boolean existsByUsername(String username);
        List<User> findByRoleOrderByCreatedAtDesc(String role);
        long countByRole(String role);
        long countByRoleAndCreatedAtStartingWith(String role, String prefix);
        Optional<User> findFirstByRoleAndEmail(String role, String email);
    }

    public interface Profiles extends JpaRepository<Profile, String> {
        List<Profile> findByUserIdOrderByCreatedAtAsc(String userId);
        Optional<Profile> findFirstByUserIdAndRelation(String userId, String relation);
        long countByUserId(String userId);
    }

    public interface Reports extends JpaRepository<Report, String> {
        List<Report> findByUserIdOrderByCreatedAtDesc(String userId);
        List<Report> findByParentId(String parentId);
        long countByStatus(String status);
    }

    public interface Payments extends JpaRepository<Payment, String> {
        Optional<Payment> findByOrderId(String orderId);
        List<Payment> findByUserIdOrderByCreatedAtDesc(String userId);
        List<Payment> findAllByOrderByCreatedAtDesc();
        List<Payment> findByStatusOrderByPaidAtDesc(String status);
    }

    public interface Consultations extends JpaRepository<Consultation, String> {
        List<Consultation> findByUserIdOrderByCreatedAtDesc(String userId);
        List<Consultation> findAllByOrderByCreatedAtDesc();
        long countByStatusIn(List<String> statuses);
    }

    public interface Notifications extends JpaRepository<Notification, String> {
        List<Notification> findTop100ByOrderByCreatedAtDesc();
    }

    public interface ChatMessages extends JpaRepository<ChatMessage, String> {
        List<ChatMessage> findByProfileIdAndSessionOrderByCreatedAtAsc(String profileId, int session);
        List<ChatMessage> findByProfileIdOrderByCreatedAtAsc(String profileId);
        long countByProfileIdAndSessionAndRole(String profileId, int session, String role);
    }

    public interface ChatSessions extends JpaRepository<ChatSession, String> {
        Optional<ChatSession> findByProfileIdAndSession(String profileId, int session);
        List<ChatSession> findByProfileIdOrderBySessionDesc(String profileId);
    }

    public interface Memories extends JpaRepository<MemoryFact, String> {
        List<MemoryFact> findByProfileIdOrderByCreatedAtAsc(String profileId);
        boolean existsByProfileIdAndFactIgnoreCase(String profileId, String fact);
    }

    public interface Settings extends JpaRepository<Setting, String> {}

    public interface Resets extends JpaRepository<PasswordReset, String> {
        Optional<PasswordReset> findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(String email);
    }

    public final Users users;
    public final Profiles profiles;
    public final Reports reports;
    public final Payments payments;
    public final Consultations consultations;
    public final Notifications notifications;
    public final ChatMessages messages;
    public final ChatSessions sessions;
    public final Memories memories;
    public final Settings settings;
    public final Resets resets;

    public Repos(Users users, Profiles profiles, Reports reports, Payments payments, Consultations consultations, Notifications notifications,
                 ChatMessages messages, ChatSessions sessions, Memories memories, Settings settings, Resets resets) {
        this.users = users; this.profiles = profiles; this.reports = reports; this.payments = payments; this.consultations = consultations;
        this.notifications = notifications; this.messages = messages; this.sessions = sessions; this.memories = memories; this.settings = settings; this.resets = resets;
    }

    public String setting(String key, String def) {
        return settings.findById(key).map(s -> s.value).orElse(def);
    }

    public void setSetting(String key, String value) {
        Setting s = new Setting();
        s.key = key; s.value = value;
        settings.save(s);
    }
}
