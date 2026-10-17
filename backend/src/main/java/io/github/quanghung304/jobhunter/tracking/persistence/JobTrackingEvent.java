package io.github.quanghung304.jobhunter.tracking.persistence;

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
 * One status change of a tracked job (history).
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
    @Column(name = "from_status", nullable = false, updatable = false)
    private TrackingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private TrackingStatus toStatus;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JobTrackingEvent() {
        // for JPA
    }

    public JobTrackingEvent(
            Long jobId,
            String profileId,
            TrackingStatus fromStatus,
            TrackingStatus toStatus,
            String note,
            Instant createdAt) {
        this.jobId = jobId;
        this.profileId = profileId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
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

    public TrackingStatus getFromStatus() {
        return fromStatus;
    }

    public TrackingStatus getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
