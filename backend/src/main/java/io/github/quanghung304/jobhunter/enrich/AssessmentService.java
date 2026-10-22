package io.github.quanghung304.jobhunter.enrich;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessment;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessmentRepository;
import io.github.quanghung304.jobhunter.enrich.persistence.JobExtraction;
import io.github.quanghung304.jobhunter.enrich.persistence.JobExtractionRepository;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.LlmProperties;
import io.github.quanghung304.jobhunter.profile.Profile;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns {@code job_assessment} (data-model.md). Rows are kept per job, profile and the
 * configured score prompt version ({@code app.llm.score-prompt}).
 *
 * <p>Prefilter rules for a row: REJECT clears the eligibility checks and sets status DONE (no LLM
 * work); PASS after a REJECT (or on a new row) sets status PENDING. A PASS row whose job has an OK
 * extraction for the current extract prompt gets its eligibility checks evaluated (Java, no LLM)
 * when they are missing or the profile hash changed (ADR-0011). Scores are kept (re-scoring is
 * manual); the owner's eligibility decisions live in {@code tracking} and are never touched here.
 */
@Service
public class AssessmentService {

    /** Job ids per {@code in (...)} query. */
    static final int JOB_BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);
    private static final TypeReference<List<EligibilityCheck>> CHECKS = new TypeReference<>() {
    };

    private final JobAssessmentRepository assessments;
    private final JobExtractionRepository extractions;
    private final JobQueryService jobs;
    private final Prefilter prefilter;
    private final EligibilityEvaluator evaluator;
    private final ObjectMapper json;
    private final Clock clock;
    private final String scorePromptVersion;
    private final String extractPromptVersion;

    public AssessmentService(JobAssessmentRepository assessments, JobExtractionRepository extractions,
            JobQueryService jobs, Prefilter prefilter, EligibilityEvaluator evaluator, ObjectMapper json, Clock clock,
            LlmProperties llmProperties) {
        this.assessments = assessments;
        this.extractions = extractions;
        this.jobs = jobs;
        this.prefilter = prefilter;
        this.evaluator = evaluator;
        this.json = json;
        this.clock = clock;
        this.scorePromptVersion = llmProperties.scorePrompt();
        this.extractPromptVersion = llmProperties.extractPrompt();
    }

    /** Creates or updates the row of this job and profile for the current score prompt version. */
    @Transactional
    public void recordPrefilter(JobView job, Profile profile, String profileHash, PrefilterDecision decision) {
        Instant now = clock.instant();
        JobAssessment row = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(job.id(), profile.id(), scorePromptVersion)
                .orElse(null);
        if (row == null) {
            row = new JobAssessment(job.id(), profile.id(), profileHash, AssessmentStatus.PENDING, now, now);
            row.setScorePromptVersion(scorePromptVersion);
        }
        apply(row, job, profile, profileHash, decision, now, extractionOf(job.id()));
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
        recordPrefilter(job, profile, profileHash, decision);
        return decision.result();
    }

    /**
     * Stores the eligibility checks of an analyzed job (the row exists: {@link #ensurePrefilter}
     * first). The row waits for the owner's Score action (PENDING) unless it is already scored.
     */
    @Transactional
    public void recordChecks(long jobId, String profileId, String profileHash, long extractionId,
            List<EligibilityCheck> checks) {
        JobAssessment row = assessments
                .findByJobIdAndProfileIdAndScorePromptVersion(jobId, profileId, scorePromptVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "no assessment row for job " + jobId + "; record the prefilter first"));
        row.setProfileHash(profileHash);
        row.setExtractionId(extractionId);
        row.setEligibilityChecks(json.valueToTree(checks));
        row.setStatus(row.getScore() != null ? AssessmentStatus.DONE : AssessmentStatus.PENDING);
        row.setUpdatedAt(clock.instant());
        assessments.save(row);
    }

    /**
     * Prefilters these jobs for the profile (pipeline step, no LLM). A row is written only when
     * it is missing, the decision changed, or a PASS row lacks checks its stored extraction can
     * give, so a run with no new data writes nothing.
     */
    @Transactional
    public PrefilterSummary prefilterJobs(List<JobView> jobViews, Profile profile, String profileHash) {
        List<Long> ids = jobViews.stream().map(JobView::id).toList();
        Map<Long, AssessmentView> existing = findAll(ids, profile.id());
        Map<Long, JobExtraction> extracted = okExtractions(ids);
        int recorded = 0;
        int passed = 0;
        for (JobView job : jobViews) {
            PrefilterDecision decision = prefilter.evaluate(job, profile);
            passed += decision.passed() ? 1 : 0;
            AssessmentView row = existing.get(job.id());
            boolean same = row != null && row.prefilterResult() == decision.result()
                    && row.prefilterReasons().equals(decision.reasonCodes()) && profileHash.equals(row.profileHash());
            boolean checksMissing = decision.passed() && row != null && row.eligibilityChecks() == null
                    && extracted.containsKey(job.id());
            if (!same || checksMissing) {
                recordPrefilter(job, profile, profileHash, decision);
                recorded++;
            }
        }
        return new PrefilterSummary(jobViews.size(), recorded, passed, jobViews.size() - passed);
    }

    /** Among these jobs: prefilter PASS for the profile but not analyzed yet (no checks). */
    @Transactional(readOnly = true)
    public int countNotAnalyzed(Collection<Long> jobIds, String profileId) {
        return (int) findAll(jobIds, profileId).values().stream()
                .filter(row -> row.prefilterResult() == PrefilterResult.PASS && row.eligibilityChecks() == null)
                .count();
    }

    /** The row of this job and profile for the current score prompt version. */
    @Transactional(readOnly = true)
    public Optional<AssessmentView> find(long jobId, String profileId) {
        return assessments.findByJobIdAndProfileIdAndScorePromptVersion(jobId, profileId, scorePromptVersion)
                .map(this::view);
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

    /** Unscored rows of this profile whose last scoring attempt failed (admin "Failed LLM work"). */
    @Transactional(readOnly = true)
    public List<FailedWork> failedScorings(String profileId) {
        return assessments.findByProfileIdAndScorePromptVersionAndStatusAndScoreIsNullOrderByJobId(profileId,
                        scorePromptVersion, AssessmentStatus.FAILED).stream()
                .map(row -> new FailedWork(row.getJobId(), row.getAttempts()))
                .toList();
    }

    /**
     * Admin "Retry" of a failed scoring: attempts back to 0 and status PENDING, so the next Score
     * calls the LLM again. A scored row is left alone.
     *
     * @return whether a row was reset
     */
    @Transactional
    public boolean resetScoreAttempts(long jobId, String profileId) {
        return assessments.findByJobIdAndProfileIdAndScorePromptVersion(jobId, profileId, scorePromptVersion)
                .filter(row -> row.getScore() == null && row.getStatus() == AssessmentStatus.FAILED)
                .map(row -> {
                    row.setAttempts(0);
                    row.setStatus(AssessmentStatus.PENDING);
                    row.setUpdatedAt(clock.instant());
                    assessments.save(row);
                    return true;
                })
                .orElse(false);
    }

    /** Rows of this profile first prefiltered at or after {@code since} with result REJECT. */
    @Transactional(readOnly = true)
    public long countPrefilterRejectedSince(String profileId, Instant since) {
        return assessments.countByProfileIdAndScorePromptVersionAndPrefilterResultAndCreatedAtGreaterThanEqual(
                profileId, scorePromptVersion, PrefilterResult.REJECT, since);
    }

    /** Jobs of this profile with a score written at or after {@code since}. */
    @Transactional(readOnly = true)
    public long countScoredSince(String profileId, Instant since) {
        return assessments.countByProfileIdAndScorePromptVersionAndScoreIsNotNullAndUpdatedAtGreaterThanEqual(
                profileId, scorePromptVersion, since);
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

    AssessmentView view(JobAssessment row) {
        return new AssessmentView(row.getJobId(), row.getProfileId(), row.getProfileHash(), row.getPrefilterResult(),
                row.getPrefilterReasons(), checksOf(row), row.getExtractionId(), row.getScore(),
                row.getScoreBreakdown(), row.getSummary(), row.getScorePromptVersion(), row.getScoreModel(),
                row.getStatus(), row.getAttempts());
    }

    private List<EligibilityCheck> checksOf(JobAssessment row) {
        JsonNode node = row.getEligibilityChecks();
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return json.readerFor(CHECKS).readValue(node);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("unreadable eligibility_checks of job " + row.getJobId(), e);
        }
    }

    /**
     * Runs the prefilter again for every row of this profile (current score prompt version) whose
     * profile hash differs, stores the new result and hash, and re-evaluates the eligibility checks
     * of rows with an OK extraction. No LLM calls; scores are kept (and the owner's decisions live
     * in {@code tracking}).
     *
     * @return rows re-evaluated, rows whose prefilter result changed, rows whose checks changed
     */
    @Transactional
    public Reassessment reassessPrefilter(Profile profile, String profileHash) {
        List<JobAssessment> stale = assessments.findByProfileIdAndScorePromptVersionAndProfileHashNotOrderById(
                profile.id(), scorePromptVersion, profileHash);
        if (stale.isEmpty()) {
            return new Reassessment(0, 0, 0);
        }
        Map<Long, JobView> views = new HashMap<>();
        List<Long> ids = stale.stream().map(JobAssessment::getJobId).toList();
        for (int from = 0; from < ids.size(); from += JOB_BATCH_SIZE) {
            jobs.findAll(ids.subList(from, Math.min(from + JOB_BATCH_SIZE, ids.size())))
                    .forEach(view -> views.put(view.id(), view));
        }
        Map<Long, JobExtraction> extracted = okExtractions(ids);
        Instant now = clock.instant();
        int rejected = 0;
        int prefilterChanged = 0;
        int checksUpdated = 0;
        for (JobAssessment row : stale) {
            JobView job = views.get(row.getJobId());
            PrefilterResult before = row.getPrefilterResult();
            JsonNode checksBefore = row.getEligibilityChecks();
            PrefilterDecision decision = prefilter.evaluate(job, profile);
            apply(row, job, profile, profileHash, decision, now, Optional.ofNullable(extracted.get(row.getJobId())));
            rejected += decision.passed() ? 0 : 1;
            prefilterChanged += before != decision.result() ? 1 : 0;
            checksUpdated += java.util.Objects.equals(checksBefore, row.getEligibilityChecks()) ? 0 : 1;
        }
        assessments.saveAll(stale);
        log.info("prefilter reassessed profile={} rows={} rejected={} prefilterChanged={} checksUpdated={}",
                profile.id(), stale.size(), rejected, prefilterChanged, checksUpdated);
        return new Reassessment(stale.size(), prefilterChanged, checksUpdated);
    }

    /**
     * Startup step after V8 (ADR-0011): PASS rows of this profile without checks whose job has an
     * OK extraction get their checks evaluated from the stored attributes (no LLM).
     *
     * @return rows updated
     */
    @Transactional
    public int evaluateMissingChecks(Profile profile, String profileHash) {
        List<JobAssessment> rows = assessments.findMissingChecks(profile.id(), scorePromptVersion,
                PrefilterResult.PASS);
        if (rows.isEmpty()) {
            return 0;
        }
        List<Long> ids = rows.stream().map(JobAssessment::getJobId).toList();
        Map<Long, JobExtraction> extracted = okExtractions(ids);
        Map<Long, JobView> views = jobs.findAll(extracted.keySet()).stream()
                .collect(Collectors.toMap(JobView::id, Function.identity()));
        Instant now = clock.instant();
        List<JobAssessment> updated = new ArrayList<>();
        for (JobAssessment row : rows) {
            JobExtraction extraction = extracted.get(row.getJobId());
            JobView job = views.get(row.getJobId());
            if (extraction != null && job != null) {
                setChecks(row, job, profile, extraction);
                row.setUpdatedAt(now);
                updated.add(row);
            }
        }
        assessments.saveAll(updated);
        if (!updated.isEmpty()) {
            log.info("eligibility checks evaluated profile={} rows={} (no LLM)", profile.id(), updated.size());
        }
        return updated.size();
    }

    private void apply(JobAssessment row, JobView job, Profile profile, String profileHash, PrefilterDecision decision,
            Instant now, Optional<JobExtraction> extraction) {
        boolean wasPassed = row.getPrefilterResult() == PrefilterResult.PASS;
        boolean hashChanged = !profileHash.equals(row.getProfileHash());
        if (!decision.passed()) {
            row.setEligibilityChecks(null);
            row.setStatus(AssessmentStatus.DONE);
        } else {
            if (!wasPassed) {
                row.setStatus(row.getScore() != null ? AssessmentStatus.DONE : AssessmentStatus.PENDING);
            }
            boolean needsChecks = hashChanged || row.getEligibilityChecks() == null;
            if (needsChecks && extraction.isPresent() && job != null) {
                setChecks(row, job, profile, extraction.get());
            } else if (needsChecks) {
                row.setEligibilityChecks(null);
            }
        }
        row.setProfileHash(profileHash);
        row.setPrefilterResult(decision.result());
        row.setPrefilterReasons(decision.reasonCodes());
        row.setUpdatedAt(now);
    }

    private void setChecks(JobAssessment row, JobView job, Profile profile, JobExtraction extraction) {
        try {
            JobAttributes attributes = json.treeToValue(extraction.getAttributes(), JobAttributes.class);
            row.setExtractionId(extraction.getId());
            row.setEligibilityChecks(json.valueToTree(evaluator.evaluate(attributes, job, profile)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored attributes of job " + job.id() + " are unreadable", e);
        }
    }

    private Optional<JobExtraction> extractionOf(long jobId) {
        return extractions.findByJobIdAndPromptVersion(jobId, extractPromptVersion)
                .filter(row -> row.getStatus() == ExtractionStatus.OK);
    }

    private Map<Long, JobExtraction> okExtractions(Collection<Long> jobIds) {
        Map<Long, JobExtraction> result = new HashMap<>();
        List<Long> ids = List.copyOf(jobIds);
        for (int from = 0; from < ids.size(); from += JOB_BATCH_SIZE) {
            extractions.findByJobIdInAndPromptVersion(ids.subList(from, Math.min(from + JOB_BATCH_SIZE, ids.size())),
                            extractPromptVersion).stream()
                    .filter(row -> row.getStatus() == ExtractionStatus.OK)
                    .forEach(row -> result.put(row.getJobId(), row));
        }
        return result;
    }
}
