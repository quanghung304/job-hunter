package io.github.quanghung304.jobhunter.notify.persistence;

import io.github.quanghung304.jobhunter.notify.NotificationChannel;
import io.github.quanghung304.jobhunter.notify.NotificationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A sent (or failed) Telegram digest or alert.
 *
 * <p>Append-only log row: created complete, never updated.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private String profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private NotificationKind kind;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "job_ids", nullable = false, updatable = false)
    private List<Long> jobIds = new ArrayList<>();

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    @Column(name = "error", updatable = false)
    private String error;

    protected Notification() {
        // for JPA
    }

    public Notification(
            String profileId,
            NotificationChannel channel,
            NotificationKind kind,
            List<Long> jobIds,
            Instant sentAt,
            boolean success,
            String error) {
        this.profileId = profileId;
        this.channel = channel;
        this.kind = kind;
        this.jobIds = new ArrayList<>(jobIds);
        this.sentAt = sentAt;
        this.success = success;
        this.error = error;
    }

    public Long getId() {
        return id;
    }

    public String getProfileId() {
        return profileId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationKind getKind() {
        return kind;
    }

    /** Unmodifiable. */
    public List<Long> getJobIds() {
        return List.copyOf(jobIds);
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }
}
