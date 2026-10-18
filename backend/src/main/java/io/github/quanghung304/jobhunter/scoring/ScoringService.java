package io.github.quanghung304.jobhunter.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.AssessmentView;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import io.github.quanghung304.jobhunter.enrich.EligibilityEvaluator;
import io.github.quanghung304.jobhunter.enrich.EligibilityResult;
import io.github.quanghung304.jobhunter.enrich.ExtractionResult;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import io.github.quanghung304.jobhunter.enrich.JobAttributes;
import io.github.quanghung304.jobhunter.enrich.TooManyJobsException;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.BudgetGuard;
import io.github.quanghung304.jobhunter.llm.LlmFailed;
import io.github.quanghung304.jobhunter.llm.LlmOutcome;
import io.github.quanghung304.jobhunter.llm.LlmProperties;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmResult;
import io.github.quanghung304.jobhunter.llm.LlmRouter;
import io.github.quanghung304.jobhunter.llm.LlmUnavailable;
import io.github.quanghung304.jobhunter.llm.ModelChoice;
import io.github.quanghung304.jobhunter.llm.PaidCallsDisabledException;
import io.github.quanghung304.jobhunter.profile.Profile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * LLM scoring (scoring-spec.md §5) of analyzed jobs whose eligibility is ELIGIBLE or UNCERTAIN,
 * only on an owner action (ADR-0010): the "Score" button, the admin bulk Score and "Re-score
 * active jobs". At most one SCORE call per job and action, cached per job, profile and score
 * prompt version; max {@value #MAX_ATTEMPTS} failed attempts. Each action is one budget run.
 */
@Service
public class ScoringService {

    static final int MAX_ATTEMPTS = 3;
    static final int MAX_BULK = 200;
    static final int MAX_DESCRIPTION = 8_000;

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final LlmRouter router;
    private final AssessmentService assessments;
    private final ExtractionService extraction;
    private final EligibilityEvaluator eligibility;
    private final JobQueryService jobs;
    private final BudgetGuard budget;
    private final ObjectMapper json;
    private final String promptVersion;

    public ScoringService(LlmRouter router, AssessmentService assessments, ExtractionService extraction,
            EligibilityEvaluator eligibility, JobQueryService jobs, BudgetGuard budget, ObjectMapper json,
            LlmProperties llmProperties) {
        this.router = router;
        this.assessments = assessments;
        this.extraction = extraction;
        this.eligibility = eligibility;
        this.jobs = jobs;
        this.budget = budget;
        this.json = json;
        this.promptVersion = llmProperties.scorePrompt();
    }

    /** The owner's "Score" on one job; a stored score is returned without a call. */
    public ScoringResult score(long jobId, Profile profile) {
        JobView job = jobs.find(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        budget.beginRun();
        return score(job, profile, false);
    }

    /** One SCORE call, no DB writes except the router's {@code llm_call} log (eval, T-016). */
    public ScoreComputation computeScore(JobView job, JobAttributes attributes, EligibilityResult eligibilityResult,
            Profile profile) {
        Long logJobId = job.id() > 0 ? job.id() : null;
        LlmOutcome<ScoreResult> outcome = router.call(LlmPurpose.SCORE, promptVersion,
                variables(job, attributes, eligibilityResult, profile), logJobId, ScoreResult.class);
        return switch (outcome) {
            case LlmResult<ScoreResult> result -> {
                ScoreResult value = result.value();
                if (!value.scoreMatchesBreakdown()) {
                    log.warn("score job={} model={}: score {} != breakdown sum {}; using the sum", job.id(),
                            result.modelId(), value.score(), value.breakdown().sum());
                    value = value.withSumAsScore();
                }
                yield new ScoreComputation(ScoreComputation.Status.OK, value, result.modelId(), null, null);
            }
            case LlmFailed<ScoreResult> failed -> new ScoreComputation(ScoreComputation.Status.FAILED, null,
                    failed.modelId(), null, failed.kind() + ": " + failed.message());
            case LlmUnavailable<ScoreResult> unavailable -> new ScoreComputation(ScoreComputation.Status.UNAVAILABLE,
                    null, null, unavailable.reason(), unavailable.detail());
        };
    }

    /**
     * Bulk scoring of selected jobs (admin, max {@value #MAX_BULK}): skips jobs that are not
     * analyzed, ineligible, already scored, expired or failed for good. The dry run names the
     * model and the estimated cost; the real run stops at the first pause or budget stop.
     */
    public BulkScoreReport scoreSelected(List<Long> jobIds, Profile profile, boolean dryRun) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(jobIds));
        if (ids.size() > MAX_BULK) {
            throw new TooManyJobsException(ids.size(), MAX_BULK);
        }
        Map<Long, JobView> found = jobs.findAll(ids).stream().collect(Collectors.toMap(JobView::id, Function.identity()));
        Map<Long, AssessmentView> rows = assessments.findAll(ids, profile.id());
        Map<String, Integer> skipped = new LinkedHashMap<>();
        List<JobView> toScore = new ArrayList<>();
        for (Long id : ids) {
            String skip = skipReason(found.get(id), rows.get(id));
            if (skip != null) {
                skipped.merge(skip, 1, Integer::sum);
            } else {
                toScore.add(found.get(id));
            }
        }
        ModelChoice model = router.resolve(LlmPurpose.SCORE);
        BigDecimal cost = budget.estimateCostUsd(model, LlmPurpose.SCORE, toScore.size()).orElse(null);
        if (dryRun) {
            return new BulkScoreReport(true, ids.size(), toScore.size(), skipped, toScore.size(), model, cost, 0, 0,
                    null, List.of());
        }
        if (!toScore.isEmpty() && model.blockedByConsent()) {
            throw new PaidCallsDisabledException(model);
        }
        Run run = run(toScore, profile, false);
        log.info("bulk score selected={} scored={} failed={} skipped={} stoppedBy={}", ids.size(), run.scored,
                run.failed, skipped, run.stoppedBy);
        return new BulkScoreReport(false, ids.size(), toScore.size(), skipped, toScore.size(), model, cost,
                run.scored, run.failed, run.stoppedBy, run.notProcessed);
    }

    /**
     * "Re-score active jobs": every ACTIVE, analyzed, ELIGIBLE/UNCERTAIN job of the profile that
     * already has a score for the current prompt version is scored again (after a profile or
     * model change). Dry run first; the real run is refused for a PAID model without consent.
     */
    public RescorePlan rescoreActive(Profile profile, boolean dryRun) {
        List<Long> scored = assessments.scoredJobIds(profile.id());
        Map<Long, AssessmentView> rows = assessments.findAll(scored, profile.id());
        List<JobView> active = jobs.findAll(scored).stream()
                .filter(job -> job.status() == JobStatus.ACTIVE)
                .filter(job -> isScorable(rows.get(job.id())))
                .toList();
        ModelChoice model = router.resolve(LlmPurpose.SCORE);
        BigDecimal cost = budget.estimateCostUsd(model, LlmPurpose.SCORE, active.size()).orElse(null);
        if (dryRun) {
            return new RescorePlan(true, active.size(), active.size(), model, cost, 0, 0, null, List.of());
        }
        if (!active.isEmpty() && model.blockedByConsent()) {
            throw new PaidCallsDisabledException(model);
        }
        Run run = run(active, profile, true);
        log.info("rescore active jobs={} scored={} failed={} stoppedBy={}", active.size(), run.scored, run.failed,
                run.stoppedBy);
        return new RescorePlan(false, active.size(), active.size(), model, cost, run.scored, run.failed,
                run.stoppedBy, run.notProcessed);
    }

    private Run run(List<JobView> toScore, Profile profile, boolean rescore) {
        budget.beginRun();
        Run run = new Run();
        for (int i = 0; i < toScore.size(); i++) {
            ScoringResult result = score(toScore.get(i), profile, rescore);
            switch (result.outcome()) {
                case SCORED, ALREADY_SCORED -> run.scored++;
                case FAILED, FAILED_PERMANENTLY -> run.failed++;
                case NOT_ANALYZED, NOT_ELIGIBLE, JOB_EXPIRED -> {
                    // checked before the run; a concurrent change makes no call
                }
                case LLM_PAUSED, BUDGET_REACHED -> {
                    run.stoppedBy = result.outcome();
                    run.notProcessed = toScore.subList(i, toScore.size()).stream().map(JobView::id).toList();
                    return run;
                }
            }
        }
        return run;
    }

    private ScoringResult score(JobView job, Profile profile, boolean rescore) {
        if (job.status() == JobStatus.EXPIRED) {
            return refused(job, ScoringResult.Outcome.JOB_EXPIRED, "the job is no longer listed by any source");
        }
        Optional<AssessmentView> row = assessments.find(job.id(), profile.id());
        Optional<ExtractionResult> extracted = extraction.current(job.id())
                .filter(r -> r.status() == ExtractionResult.Status.OK);
        if (row.isEmpty() || row.get().eligibility() == null || extracted.isEmpty()) {
            return refused(job, ScoringResult.Outcome.NOT_ANALYZED, "press Analyze first");
        }
        AssessmentView assessment = row.get();
        if (assessment.scored() && !rescore) {
            return new ScoringResult(job.id(), ScoringResult.Outcome.ALREADY_SCORED, assessment.score(),
                    assessment.summary(), false, null);
        }
        if (!assessment.scored() && assessment.attempts() >= MAX_ATTEMPTS) {
            return refused(job, ScoringResult.Outcome.FAILED_PERMANENTLY,
                    "failed " + assessment.attempts() + " times for " + promptVersion);
        }
        // eligibility is cheap and deterministic: check it again with the current profile
        EligibilityResult current = eligibility.evaluate(extracted.get().attributes(), job, profile);
        if (assessment.eligibility() == Eligibility.INELIGIBLE || current.eligibility() == Eligibility.INELIGIBLE) {
            return refused(job, ScoringResult.Outcome.NOT_ELIGIBLE, String.join(", ", current.reasons()));
        }
        ScoreComputation computation = computeScore(job, extracted.get().attributes(), current, profile);
        return switch (computation.status()) {
            case OK -> {
                ScoreResult result = computation.result();
                assessments.recordScore(job.id(), profile.id(), result.score(), json.valueToTree(result),
                        result.summary(), computation.modelId());
                yield new ScoringResult(job.id(), ScoringResult.Outcome.SCORED, result.score(), result.summary(),
                        true, null);
            }
            case FAILED -> {
                int attempts = assessments.recordScoreFailure(job.id(), profile.id());
                log.warn("scoring failed job={} attempt={}: {}", job.id(), attempts, computation.message());
                yield new ScoringResult(job.id(), attempts >= MAX_ATTEMPTS && !assessment.scored()
                        ? ScoringResult.Outcome.FAILED_PERMANENTLY : ScoringResult.Outcome.FAILED, null, null, true,
                        computation.message());
            }
            case UNAVAILABLE -> refused(job, unavailableOutcome(computation.unavailable()), computation.message());
        };
    }

    /** Skip reason for bulk scoring, null if the job can be scored. */
    private static String skipReason(JobView job, AssessmentView row) {
        if (job == null) {
            return "notFound";
        }
        if (job.status() == JobStatus.EXPIRED) {
            return "expired";
        }
        if (row == null || row.eligibility() == null || row.extractionId() == null) {
            return "notAnalyzed";
        }
        if (row.eligibility() == Eligibility.INELIGIBLE) {
            return "ineligible";
        }
        if (row.scored()) {
            return "alreadyScored";
        }
        if (row.attempts() >= MAX_ATTEMPTS) {
            return "failedPermanently";
        }
        return null;
    }

    private static boolean isScorable(AssessmentView row) {
        return row != null && row.extractionId() != null
                && (row.eligibility() == Eligibility.ELIGIBLE || row.eligibility() == Eligibility.UNCERTAIN);
    }

    static ScoringResult.Outcome unavailableOutcome(LlmUnavailable.Reason reason) {
        return switch (reason) {
            case RUN_CALL_LIMIT, MONTHLY_BUDGET -> ScoringResult.Outcome.BUDGET_REACHED;
            case NO_USABLE_MODEL, PAID_NOT_ALLOWED -> ScoringResult.Outcome.LLM_PAUSED;
        };
    }

    private static ScoringResult refused(JobView job, ScoringResult.Outcome outcome, String message) {
        return new ScoringResult(job.id(), outcome, null, null, false, message);
    }

    /** Variables of score-v1 (docs/ai/prompts/score-v1.md). */
    Map<String, String> variables(JobView job, JobAttributes attributes, EligibilityResult eligibilityResult,
            Profile profile) {
        List<String> reasons = eligibilityResult.reasons();
        return Map.of(
                "summaryLanguage", profile.summaryLanguage().code(),
                "candidateBlock", profile.candidateBlock(),
                "title", text(job.title()),
                "company", text(job.companyName()),
                "extractionJson", prettyJson(attributes),
                "eligibility", eligibilityResult.eligibility().name(),
                "eligibilityReasons", reasons.isEmpty() ? "none" : String.join("\n", reasons),
                "description", truncate(text(job.descriptionText())));
    }

    /** The first 8 000 characters (scoring-spec §5). */
    static String truncate(String description) {
        return description.length() <= MAX_DESCRIPTION ? description : description.substring(0, MAX_DESCRIPTION) + "\n[…]";
    }

    private String prettyJson(JobAttributes attributes) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot write job attributes as JSON", e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static final class Run {
        int scored;
        int failed;
        ScoringResult.Outcome stoppedBy;
        List<Long> notProcessed = List.of();
    }
}
