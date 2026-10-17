package io.github.quanghung304.jobhunter.tracking.persistence;

import io.github.quanghung304.jobhunter.tracking.TrackingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The owner's tracking status and notes for one job and profile. Created lazily: a job
 * without a row is {@link TrackingStatus#NEW}.
 */
@Entity
@Table(name = "job_tracking")
public class JobTracking {

    @EmbeddedId
    private JobTrackingId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TrackingStatus status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobTracking() {
        // for JPA
    }

    public JobTracking(JobTrackingId id, TrackingStatus status, Instant updatedAt) {
        this.id = id;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public JobTrackingId getId() {
        return id;
    }

    public TrackingStatus getStatus() {
        return status;
    }

    public void setStatus(TrackingStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
