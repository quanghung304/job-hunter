package io.github.quanghung304.jobhunter.enrich;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessment;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessmentRepository;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.LlmProperties;
import io.github.quanghung304.jobhunter.profile.Profile;
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
 * Owns {@code job_assessment} (data-model.md). Rows are kept per job, profile and the
 * configured score prompt version ({@code app.llm.score-prompt}).
 *
 * <p>Prefilter rules for a row: REJECT clears the eligibility and sets status DONE (no LLM work);
 * PASS after a REJECT (or on a new row) sets status PENDING. A different profile hash clears the
 * eligibility so that it is evaluated again; scores are kept (re-scoring is manual).
 */
@Service
public class AssessmentService {

    /** Job ids per {@code in (...)} query. */
    static final int JOB_BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    private final JobAssessmentRepository assessments;
    private final JobQueryService jobs;
    private final Prefilter prefilter;
    private final Clock clock;
    private final String scorePromptVersion;

    public AssessmentService(JobAssessmentRepository assessments, JobQueryService jobs, Prefilter prefilter,
            Clock clock, LlmProperties llmProperties) {
        this.assessments = assessments;
        this.jobs = jobs;
        this.prefilter = prefilter;
        this.clock = clock;
        this.scorePromptVersion = llmProperties.scorePrompt();
    }

    /** Creates or updates the row of this job and profile for the current score prompt version. */
    @Transactional
    public void recordPrefilter(long jobId, String profileId, String profileHash, PrefilterDecision decision) {
        Instant now = clock.instant();
        JobAssessment row = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(jobId, profileId, scorePromptVersion)
                .orElse(null);
        if (row == null) {
            row = new JobAssessment(jobId, profileId, profileHash, AssessmentStatus.PENDING, now, now);
            row.setScorePromptVersion(scorePromptVersion);
        }
        apply(row, profileHash, decision, now);
        assessments.save(row);
    }

    /**
     * The prefilter result of this job for the profile: the stored one when it was computed with
     * this profile hash, otherwise evaluated now and stored (no LLM).
     */
    @Transactional
    public PrefilterResult ensurePrefilter(JobView job, Profile profile, String profileHash) {
        Optional<JobAssessment> row = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(job.id(), profile.id(), scorePromptVersion);
        if (row.isPresent() && profileHash.equals(row.get().getProfileHash())
                && row.get().getPrefilterResult() != null) {
            return row.get().getPrefilterResult();
        }
        PrefilterDecision decision = prefilter.evaluate(job, profile);
        recordPrefilter(job.id(), profile.id(), profileHash, decision);
        return decision.result();
    }

    /**
     * Stores the eligibility of an analyzed job (the row exists: {@link #ensurePrefilter} first).
     * INELIGIBLE needs no further LLM work (status DONE); otherwise the row waits for the owner's
     * Score action (PENDING) unless it is already scored.
     */
    @Transactional
    public void recordEligibility(long jobId, String profileId, String profileHash, long extractionId,
            EligibilityResult result) {
        JobAssessment row = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(jobId, profileId, scorePromptVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "no assessment row for job " + jobId + "; record the prefilter first"));
        row.setProfileHash(profileHash);
        row.setExtractionId(extractionId);
        row.setEligibility(result.eligibility());
        row.setEligibilityReasons(result.reasons());
        if (result.eligibility() == Eligibility.INELIGIBLE || row.getScore() != null) {
            row.setStatus(AssessmentStatus.DONE);
        } else {
            row.setStatus(AssessmentStatus.PENDING);
        }
        row.setUpdatedAt(clock.instant());
        assessments.save(row);
    }

    /** The row of this job and profile for the current score prompt version. */
    @Transactional(readOnly = true)
    public Optional<AssessmentView> find(long jobId, String profileId) {
        return assessments.findByJobIdAndProfileIdAndScorePromptVersion(jobId, profileId, scorePromptVersion)
                .map(AssessmentService::view);
    }

    /** Rows of these jobs for the profile and the current score prompt version, by job id. */
    @Transactional(readOnly = true)
    public Map<Long, AssessmentView> findAll(Collection<Long> jobIds, String profileId) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AssessmentView> result = new HashMap<>();
        for (int from = 0; from < jobIds.size(); from += JOB_BATCH_SIZE) {
            List<Long> ids = jobIds.stream().skip(from).limit(JOB_BATCH_SIZE).toList();
            assessments.findByProfileIdAndScorePromptVersionAndJobIdIn(profileId, scorePromptVersion, ids)
                    .forEach(row -> result.put(row.getJobId(), view(row)));
        }
        return result;
    }

    /** Job ids of this profile that have a score for the current score prompt version. */
    @Transactional(readOnly = true)
    public List<Long> scoredJobIds(String profileId) {
        return assessments.findScoredJobIds(profileId, scorePromptVersion);
    }

    public String scorePromptVersion() {
        return scorePromptVersion;
    }

    /**
     * Stores a score (scoring writes only through this service): score, the full §5 object as
     * {@code score_breakdown}, summary and model; status DONE; one more attempt.
     */
    @Transactional
    public void recordScore(long jobId, String profileId, int score, JsonNode details, String summary,
            String model) {
        JobAssessment row = requireRow(jobId, profileId);
        row.setScore(score);
        row.setScoreBreakdown(details);
        row.setSummary(summary);
        row.setScoreModel(model);
        row.setStatus(AssessmentStatus.DONE);
        row.setAttempts(row.getAttempts() + 1);
        row.setUpdatedAt(clock.instant());
        assessments.save(row);
    }

    /** A failed scoring attempt: status FAILED and one more attempt; an earlier score is kept. */
    @Transactional
    public int recordScoreFailure(long jobId, String profileId) {
        JobAssessment row = requireRow(jobId, profileId);
        row.setAttempts(row.getAttempts() + 1);
        if (row.getScore() == null) {
            row.setStatus(AssessmentStatus.FAILED);
        }
        row.setUpdatedAt(clock.instant());
        assessments.save(row);
        return row.getAttempts();
    }

    private JobAssessment requireRow(long jobId, String profileId) {
        return assessments.findByJobIdAndProfileIdAndScorePromptVersion(jobId, profileId, scorePromptVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "no assessment row for job " + jobId + " and profile " + profileId));
    }

    static AssessmentView view(JobAssessment row) {
        return new AssessmentView(row.getJobId(), row.getProfileId(), row.getProfileHash(), row.getPrefilterResult(),
                row.getEligibility(), row.getEligibilityReasons(), row.getExtractionId(), row.getScore(),
                row.getScoreBreakdown(), row.getSummary(), row.getScorePromptVersion(), row.getScoreModel(),
                row.getStatus(), row.getAttempts());
    }

    /**
     * Runs the prefilter again for every row of this profile (current score prompt version) whose
     * profile hash differs, and stores the new result and hash. No LLM calls.
     *
     * @return the number of rows re-evaluated
     */
    @Transactional
    public int reassessPrefilter(Profile profile, String profileHash) {
        List<JobAssessment> stale = assessments.findByProfileIdAndScorePromptVersionAndProfileHashNotOrderById(
                profile.id(), scorePromptVersion, profileHash);
        if (stale.isEmpty()) {
            return 0;
        }
        Map<Long, JobView> views = new HashMap<>();
        for (int from = 0; from < stale.size(); from += JOB_BATCH_SIZE) {
            List<Long> ids = stale.subList(from, Math.min(from + JOB_BATCH_SIZE, stale.size())).stream()
                    .map(JobAssessment::getJobId)
                    .toList();
            jobs.findAll(ids).forEach(view -> views.put(view.id(), view));
        }
        Instant now = clock.instant();
        int rejected = 0;
        for (JobAssessment row : stale) {
            PrefilterDecision decision = prefilter.evaluate(views.get(row.getJobId()), profile);
            apply(row, profileHash, decision, now);
            rejected += decision.passed() ? 0 : 1;
        }
        assessments.saveAll(stale);
        log.info("prefilter reassessed profile={} rows={} rejected={}", profile.id(), stale.size(), rejected);
        return stale.size();
    }

    private static void apply(JobAssessment row, String profileHash, PrefilterDecision decision, Instant now) {
        boolean wasPassed = row.getPrefilterResult() == PrefilterResult.PASS;
        if (!profileHash.equals(row.getProfileHash()) || !decision.passed()) {
            row.setEligibility(null);
            row.setEligibilityReasons(List.of());
        }
        if (!decision.passed()) {
            row.setStatus(AssessmentStatus.DONE);
        } else if (!wasPassed) {
            row.setStatus(AssessmentStatus.PENDING);
        }
        row.setProfileHash(profileHash);
        row.setPrefilterResult(decision.result());
        row.setPrefilterReasons(decision.reasonCodes());
        row.setUpdatedAt(now);
    }
}
