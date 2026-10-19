package io.github.quanghung304.jobhunter.enrich;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessment;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessmentRepository;
import io.github.quanghung304.jobhunter.enrich.persistence.JobExtraction;
import io.github.quanghung304.jobhunter.enrich.persistence.JobExtractionRepository;
import io.github.quanghung304.jobhunter.ingest.JobDescriptionChanged;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.LlmFailed;
import io.github.quanghung304.jobhunter.llm.LlmOutcome;
import io.github.quanghung304.jobhunter.llm.LlmProperties;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmResult;
import io.github.quanghung304.jobhunter.llm.LlmRouter;
import io.github.quanghung304.jobhunter.llm.LlmUnavailable;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * LLM extraction of job attributes (scoring-spec.md §3), cached per job and prompt version
 * ({@code app.llm.extract-prompt}). Runs only on an owner action (ADR-0010) through
 * {@link LlmRouter}; at most {@value #MAX_ATTEMPTS} failed attempts per job and prompt version.
 */
@Service
public class ExtractionService {

    static final int MAX_ATTEMPTS = 3;
    static final int MAX_DESCRIPTION = 12_000;
    static final int DESCRIPTION_HEAD = 9_000;
    static final int DESCRIPTION_TAIL = 3_000;
    static final String TRUNCATION_MARK = "\n[…]\n";

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final LlmRouter router;
    private final JobExtractionRepository extractions;
    private final JobAssessmentRepository assessments;
    private final JobQueryService jobs;
    private final ObjectMapper json;
    private final Clock clock;
    private final String promptVersion;

    public ExtractionService(LlmRouter router, JobExtractionRepository extractions, JobAssessmentRepository assessments,
            JobQueryService jobs, ObjectMapper json, Clock clock, LlmProperties llmProperties) {
        this.router = router;
        this.extractions = extractions;
        this.assessments = assessments;
        this.jobs = jobs;
        this.json = json;
        this.clock = clock;
        this.promptVersion = llmProperties.extractPrompt();
    }

    public String promptVersion() {
        return promptVersion;
    }

    /** One EXTRACT call for this job, no DB writes except the router's {@code llm_call} log (eval, T-016). */
    public ExtractionResult extractAttributes(JobView job) {
        Long logJobId = job.id() > 0 ? job.id() : null;
        LlmOutcome<JobAttributes> outcome = router.call(LlmPurpose.EXTRACT, promptVersion, variables(job), logJobId,
                JobAttributes.class);
        return switch (outcome) {
            case LlmResult<JobAttributes> result -> new ExtractionResult(ExtractionResult.Status.OK, result.value(),
                    null, result.modelId(), true, null, 0, null);
            case LlmFailed<JobAttributes> failed -> new ExtractionResult(ExtractionResult.Status.FAILED, null, null,
                    failed.modelId(), true, null, 0, failed.kind() + ": " + failed.message());
            case LlmUnavailable<JobAttributes> unavailable -> new ExtractionResult(ExtractionResult.Status.UNAVAILABLE,
                    null, null, null, false, unavailable.reason(), 0, unavailable.detail());
        };
    }

    /**
     * The current extraction of a job: the cached OK row, or one EXTRACT call stored as OK or
     * FAILED. A FAILED row is retried on the next request until {@value #MAX_ATTEMPTS} attempts;
     * UNAVAILABLE stores nothing and uses no attempt.
     */
    public ExtractionResult extract(long jobId) {
        JobView job = jobs.find(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        JobExtraction row = extractions.findByJobIdAndPromptVersion(jobId, promptVersion).orElse(null);
        if (row != null && row.getStatus() == ExtractionStatus.OK) {
            return stored(row, false);
        }
        if (row != null && row.getAttempts() >= MAX_ATTEMPTS) {
            return new ExtractionResult(ExtractionResult.Status.FAILED_PERMANENTLY, null, row.getId(), row.getModel(),
                    false, null, row.getAttempts(), "failed " + row.getAttempts() + " times for " + promptVersion);
        }
        ExtractionResult result = extractAttributes(job);
        if (result.status() == ExtractionResult.Status.UNAVAILABLE) {
            return result;
        }
        if (row == null) {
            row = new JobExtraction(jobId, promptVersion, ExtractionStatus.FAILED, clock.instant());
        }
        row.setAttempts(row.getAttempts() + 1);
        row.setModel(result.modelId());
        if (result.status() == ExtractionResult.Status.OK) {
            row.setStatus(ExtractionStatus.OK);
            row.setAttributes(json.valueToTree(result.attributes()));
        } else {
            row.setStatus(ExtractionStatus.FAILED);
            row.setAttributes(null);
            log.warn("extraction failed job={} prompt={} attempt={}: {}", jobId, promptVersion, row.getAttempts(),
                    result.message());
        }
        row = extractions.save(row);
        if (row.getStatus() == ExtractionStatus.OK) {
            return stored(row, true);
        }
        ExtractionResult.Status status = row.getAttempts() >= MAX_ATTEMPTS
                ? ExtractionResult.Status.FAILED_PERMANENTLY : ExtractionResult.Status.FAILED;
        return new ExtractionResult(status, null, row.getId(), row.getModel(), true, null, row.getAttempts(),
                result.message());
    }

    /** The stored extraction for the current prompt version, without calling the LLM. */
    public Optional<ExtractionResult> current(long jobId) {
        return extractions.findByJobIdAndPromptVersion(jobId, promptVersion).map(row -> stored(row, false));
    }

    /** Jobs whose extraction failed for the current prompt version (admin "Failed LLM work"). */
    public List<FailedWork> failedJobs() {
        return extractions.findByPromptVersionAndStatusOrderByJobId(promptVersion, ExtractionStatus.FAILED).stream()
                .map(row -> new FailedWork(row.getJobId(), row.getAttempts()))
                .toList();
    }

    /**
     * Admin "Retry": a FAILED extraction of the current prompt version gets its attempts back, so
     * the next Analyze calls the LLM again. An OK extraction is left alone.
     *
     * @return whether a failed row was reset
     */
    public boolean resetAttempts(long jobId) {
        return extractions.findByJobIdAndPromptVersion(jobId, promptVersion)
                .filter(row -> row.getStatus() == ExtractionStatus.FAILED)
                .map(row -> {
                    row.setAttempts(0);
                    extractions.save(row);
                    return true;
                })
                .orElse(false);
    }

    /** Stored extraction status per job for the current prompt version; jobs without a row are absent. */
    public Map<Long, ExtractionStatus> statuses(Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return extractions.findByJobIdInAndPromptVersion(jobIds, promptVersion).stream()
                .collect(Collectors.toMap(JobExtraction::getJobId, JobExtraction::getStatus, (a, b) -> a));
    }

    /**
     * A changed description invalidates the extractions of the job (all prompt versions) and
     * the eligibility checks and score that were based on them; runs inside the ingest transaction.
     */
    @EventListener
    public void onDescriptionChanged(JobDescriptionChanged event) {
        List<JobExtraction> rows = extractions.findByJobId(event.jobId());
        if (rows.isEmpty()) {
            return;
        }
        for (JobAssessment assessment : assessments.findByJobId(event.jobId())) {
            assessment.setExtractionId(null);
            assessment.setEligibilityChecks(null);
            assessment.setScore(null);
            assessment.setScoreBreakdown(null);
            assessment.setSummary(null);
            assessment.setScoreModel(null);
            if (assessment.getPrefilterResult() == PrefilterResult.PASS) {
                assessment.setStatus(AssessmentStatus.PENDING);
            }
            assessment.setUpdatedAt(clock.instant());
        }
        assessments.flush();
        extractions.deleteAll(rows);
        log.info("description changed job={}: {} extraction(s) invalidated", event.jobId(), rows.size());
    }

    private ExtractionResult stored(JobExtraction row, boolean llmCalled) {
        if (row.getStatus() != ExtractionStatus.OK) {
            ExtractionResult.Status status = row.getAttempts() >= MAX_ATTEMPTS
                    ? ExtractionResult.Status.FAILED_PERMANENTLY : ExtractionResult.Status.FAILED;
            return new ExtractionResult(status, null, row.getId(), row.getModel(), llmCalled, null, row.getAttempts(),
                    null);
        }
        try {
            JobAttributes attributes = json.treeToValue(row.getAttributes(), JobAttributes.class);
            return new ExtractionResult(ExtractionResult.Status.OK, attributes, row.getId(), row.getModel(), llmCalled,
                    null, row.getAttempts(), null);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored attributes of job " + row.getJobId() + " are unreadable", e);
        }
    }

    /** Prompt variables of extract-v1; nulls become empty strings, the description is truncated. */
    static Map<String, String> variables(JobView job) {
        Function<String, String> text = value -> value == null ? "" : value;
        return Map.of(
                "title", text.apply(job.title()),
                "company", text.apply(job.companyName()),
                "locationText", text.apply(job.locationText()),
                "remoteHint", job.remoteHint() == null ? "UNKNOWN" : job.remoteHint().name(),
                "employmentHint", text.apply(job.employmentHint()),
                "description", truncate(text.apply(job.descriptionText())));
    }

    /** First 9 000 + "[…]" + last 3 000 characters when longer than 12 000 (T-011). */
    static String truncate(String description) {
        if (description.length() <= MAX_DESCRIPTION) {
            return description;
        }
        return description.substring(0, DESCRIPTION_HEAD) + TRUNCATION_MARK
                + description.substring(description.length() - DESCRIPTION_TAIL);
    }
}
