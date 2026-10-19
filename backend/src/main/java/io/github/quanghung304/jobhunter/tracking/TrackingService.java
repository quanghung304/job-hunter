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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The owner's tracking status, eligibility decision (ADR-0011) and notes per job and profile
 * (data-model §2, §2.1). A job without a {@code job_tracking} row is NEW and undecided; the row is
 * created on the first change.
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

    /**
     * Sets, changes or clears ({@code null}) the owner's eligibility decision (ADR-0011): any value
     * to any value, also before analysis, and independent of the status. The current value again
     * is a no-op (no event).
     *
     * @return whether the decision changed
     * @throws JobNotFoundException unknown job
     */
    @Transactional
    public boolean setEligibilityDecision(long jobId, String profileId, EligibilityDecision decision, String note) {
        requireJob(jobId);
        Instant now = clock.instant();
        JobTracking row = trackings.findById(new JobTrackingId(jobId, profileId))
                .orElseGet(() -> new JobTracking(new JobTrackingId(jobId, profileId), TrackingStatus.NEW, now));
        EligibilityDecision from = row.getEligibilityDecision();
        if (from == decision) {
            return false;
        }
        row.setEligibilityDecision(decision, now);
        row.setUpdatedAt(now);
        trackings.save(row);
        events.save(new JobTrackingEvent(jobId, profileId, TrackingEventKind.ELIGIBILITY, decisionName(from),
                decisionName(decision), blankToNull(note), now));
        log.info("eligibility decision job={} profile={} {} -> {}", jobId, profileId, decisionName(from),
                decisionName(decision));
        return true;
    }

    /** The owner's decision; empty when undecided. */
    @Transactional(readOnly = true)
    public Optional<EligibilityDecision> decision(long jobId, String profileId) {
        return trackings.findById(new JobTrackingId(jobId, profileId)).map(JobTracking::getEligibilityDecision);
    }

    /** When the current decision was set; empty when undecided. */
    @Transactional(readOnly = true)
    public Optional<Instant> decidedAt(long jobId, String profileId) {
        return trackings.findById(new JobTrackingId(jobId, profileId)).map(JobTracking::getEligibilityDecidedAt);
    }

    /** Decisions of these jobs for the profile; undecided jobs are absent. */
    @Transactional(readOnly = true)
    public Map<Long, EligibilityDecision> decisions(Collection<Long> jobIds, String profileId) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, EligibilityDecision> result = new HashMap<>();
        trackings.findAllById(jobIds.stream().map(id -> new JobTrackingId(id, profileId)).toList()).forEach(row -> {
            if (row.getEligibilityDecision() != null) {
                result.put(row.getId().jobId(), row.getEligibilityDecision());
            }
        });
        return result;
    }

    private static String decisionName(EligibilityDecision decision) {
        return decision == null ? StatusChange.UNDECIDED : decision.name();
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

    /** Status changes and eligibility decisions, oldest first. */
    @Transactional(readOnly = true)
    public List<StatusChange> history(long jobId, String profileId) {
        return events.findByJobIdAndProfileIdOrderByCreatedAtAscIdAsc(jobId, profileId).stream()
                .map(e -> new StatusChange(e.getKind(), e.getFromValue(), e.getToValue(), e.getNote(),
                        e.getCreatedAt()))
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
