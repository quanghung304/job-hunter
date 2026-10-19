package io.github.quanghung304.jobhunter.tracking.persistence;

import io.github.quanghung304.jobhunter.tracking.TrackingEventKind;
import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One change of a tracked job (history): a status change or an eligibility decision (ADR-0011).
 * {@code from}/{@code to} hold tracking status names for {@code STATUS} events and
 * {@code ELIGIBLE}/{@code INELIGIBLE}/{@code UNDECIDED} for {@code ELIGIBILITY} events.
 *
 * <p>Append-only log row: created complete, never updated.
 */
@Entity
@Table(name = "job_tracking_event")
public class JobTrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private String profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private TrackingEventKind kind;

    @Column(name = "from_status", nullable = false, updatable = false)
    private String fromValue;

    @Column(name = "to_status", nullable = false, updatable = false)
    private String toValue;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JobTrackingEvent() {
        // for JPA
    }

    /** A status change. */
    public JobTrackingEvent(
            Long jobId,
            String profileId,
            TrackingStatus fromStatus,
            TrackingStatus toStatus,
            String note,
            Instant createdAt) {
        this(jobId, profileId, TrackingEventKind.STATUS, fromStatus.name(), toStatus.name(), note, createdAt);
    }

    public JobTrackingEvent(
            Long jobId,
            String profileId,
            TrackingEventKind kind,
            String fromValue,
            String toValue,
            String note,
            Instant createdAt) {
        this.jobId = jobId;
        this.profileId = profileId;
        this.kind = kind;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getProfileId() {
        return profileId;
    }

    public TrackingEventKind getKind() {
        return kind;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
