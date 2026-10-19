package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.tracking.persistence.JobTracking;
import io.github.quanghung304.jobhunter.tracking.persistence.JobTrackingEvent;
import io.github.quanghung304.jobhunter.tracking.persistence.JobTrackingEventRepository;
import io.github.quanghung304.jobhunter.tracking.persistence.JobTrackingId;
import io.github.quanghung304.jobhunter.tracking.persistence.JobTrackingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The owner's tracking status and notes per job and profile (data-model §2). A job without a
 * {@code job_tracking} row is NEW; the row is created on the first change.
 */
@Service
public class TrackingService {

    public static final int MAX_NOTES = 10_000;

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    private final JobTrackingRepository trackings;
    private final JobTrackingEventRepository events;
    private final JobQueryService jobs;
    private final Clock clock;

    public TrackingService(JobTrackingRepository trackings, JobTrackingEventRepository events, JobQueryService jobs,
            Clock clock) {
        this.trackings = trackings;
        this.events = events;
        this.jobs = jobs;
        this.clock = clock;
    }

    /**
     * Changes the status if {@link TrackingStatusMachine} allows it and records the change in the
     * history.
     *
     * @throws JobNotFoundException            unknown job
     * @throws IllegalStatusTransitionException transition not allowed (also for the same status)
     */
    @Transactional
    public void changeStatus(long jobId, String profileId, TrackingStatus to, String note) {
        requireJob(jobId);
        JobTracking row = trackings.findById(new JobTrackingId(jobId, profileId)).orElse(null);
        TrackingStatus from = row == null ? TrackingStatus.NEW : row.getStatus();
        TrackingStatusMachine.check(from, to);
        Instant now = clock.instant();
        if (row == null) {
            row = new JobTracking(new JobTrackingId(jobId, profileId), to, now);
        } else {
            row.setStatus(to);
            row.setUpdatedAt(now);
        }
        trackings.save(row);
        events.save(new JobTrackingEvent(jobId, profileId, from, to, blankToNull(note), now));
        log.info("tracking job={} profile={} {} -> {}", jobId, profileId, from, to);
    }

    /** Replaces the notes (allowed in every status, also terminal ones); blank clears them. */
    @Transactional
    public void updateNotes(long jobId, String profileId, String notes) {
        requireJob(jobId);
        if (notes != null && notes.length() > MAX_NOTES) {
            throw new IllegalArgumentException("notes longer than " + MAX_NOTES + " characters");
        }
        Instant now = clock.instant();
        JobTracking row = trackings.findById(new JobTrackingId(jobId, profileId))
                .orElseGet(() -> new JobTracking(new JobTrackingId(jobId, profileId), TrackingStatus.NEW, now));
        row.setNotes(blankToNull(notes));
        row.setUpdatedAt(now);
        trackings.save(row);
    }

    /** Current status; NEW when the job was never tracked. */
    @Transactional(readOnly = true)
    public TrackingStatus status(long jobId, String profileId) {
        return trackings.findById(new JobTrackingId(jobId, profileId)).map(JobTracking::getStatus)
                .orElse(TrackingStatus.NEW);
    }

    @Transactional(readOnly = true)
    public Optional<String> notes(long jobId, String profileId) {
        return trackings.findById(new JobTrackingId(jobId, profileId)).map(JobTracking::getNotes);
    }

    /** Status changes, oldest first. */
    @Transactional(readOnly = true)
    public List<StatusChange> history(long jobId, String profileId) {
        return events.findByJobIdAndProfileIdOrderByCreatedAtAsc(jobId, profileId).stream()
                .map(e -> new StatusChange(e.getFromStatus(), e.getToStatus(), e.getNote(), e.getCreatedAt()))
                .toList();
    }

    private void requireJob(long jobId) {
        if (jobs.find(jobId).isEmpty()) {
            throw new JobNotFoundException(jobId);
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
