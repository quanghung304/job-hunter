package io.github.quanghung304.jobhunter.tracking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite key of {@link JobTracking}: one tracking row per job and profile. */
@Embeddable
public record JobTrackingId(
        @Column(name = "job_id", nullable = false, updatable = false) Long jobId,
        @Column(name = "profile_id", nullable = false, updatable = false) String profileId) {
}
