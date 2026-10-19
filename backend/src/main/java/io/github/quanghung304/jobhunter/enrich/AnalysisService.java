package io.github.quanghung304.jobhunter.enrich;

import io.github.quanghung304.jobhunter.common.ProgressListener;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.BudgetGuard;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmRouter;
import io.github.quanghung304.jobhunter.llm.LlmUnavailable;
import io.github.quanghung304.jobhunter.llm.ModelChoice;
import io.github.quanghung304.jobhunter.llm.PaidCallsDisabledException;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The owner's "Analyze" action (ADR-0010): prefilter check, extraction (at most one EXTRACT
 * call, cached per prompt version), then the eligibility checks in Java. Called by the analyze endpoints
 * only, never by the pipeline. Each action is one budget "run" ({@link BudgetGuard#beginRun()}),
 * so {@code app.llm.max-calls-per-run} caps a single bulk action.
 */
@Service
public class AnalysisService {

    public static final int MAX_BULK = 200;

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final JobQueryService jobs;
    private final ProfileProvider profiles;
    private final AssessmentService assessments;
    private final Prefilter prefilter;
    private final ExtractionService extraction;
    private final EligibilityEvaluator eligibility;
    private final BudgetGuard budget;
    private final LlmRouter router;

    public AnalysisService(JobQueryService jobs, ProfileProvider profiles, AssessmentService assessments,
            Prefilter prefilter, ExtractionService extraction, EligibilityEvaluator eligibility, BudgetGuard budget,
            LlmRouter router) {
        this.jobs = jobs;
        this.profiles = profiles;
        this.assessments = assessments;
        this.prefilter = prefilter;
        this.extraction = extraction;
        this.eligibility = eligibility;
        this.budget = budget;
        this.router = router;
    }

    /** Analyzes one job; repeating it makes no LLM call once the extraction is stored. */
    public AnalysisResult analyze(long jobId) {
        JobView job = jobs.find(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        budget.beginRun();
        return analyze(job);
    }

    /**
     * Bulk analysis of selected jobs (admin, max {@value #MAX_BULK}). The dry run only counts;
     * a real run analyzes the jobs that need it and stops at the first pause or budget stop.
     */
    public BulkAnalysisReport analyzeMany(List<Long> jobIds, boolean dryRun) {
        return analyzeMany(jobIds, dryRun, ProgressListener.NONE);
    }

    /**
     * As {@link #analyzeMany(List, boolean)}, reporting "jobs done of jobs to analyze" after each
     * job. A real run with a PAID EXTRACT model is refused without the owner's consent (ADR-0009).
     */
    public BulkAnalysisReport analyzeMany(List<Long> jobIds, boolean dryRun, ProgressListener progress) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(jobIds));
        if (ids.size() > MAX_BULK) {
            throw new TooManyJobsException(ids.size(), MAX_BULK);
        }
        Map<Long, JobView> found = jobs.findAll(ids).stream().collect(Collectors.toMap(JobView::id, Function.identity()));
        Map<Long, ExtractionStatus> stored = extraction.statuses(ids);
        Profile profile = profiles.current();
        Map<String, Integer> skipped = new LinkedHashMap<>();
        List<JobView> toAnalyze = new ArrayList<>();
        for (Long id : ids) {
            JobView job = found.get(id);
            String skip = null;
            if (job == null) {
                skip = "notFound";
            } else if (job.status() == JobStatus.EXPIRED) {
                skip = "expired";
            } else if (stored.get(id) == ExtractionStatus.OK) {
                skip = "alreadyAnalyzed";
            } else if (stored.get(id) == ExtractionStatus.FAILED && extraction.current(id)
                    .map(r -> r.status() == ExtractionResult.Status.FAILED_PERMANENTLY).orElse(false)) {
                skip = "failedPermanently";
            } else if (!prefilter.evaluate(job, profile).passed()) {
                skip = "prefilterRejected";
            }
            if (skip != null) {
                skipped.merge(skip, 1, Integer::sum);
            } else {
                toAnalyze.add(job);
            }
        }
        ModelChoice model = router.resolve(LlmPurpose.EXTRACT);
        BigDecimal cost = budget.estimateCostUsd(model, LlmPurpose.EXTRACT, toAnalyze.size()).orElse(null);
        if (dryRun) {
            return new BulkAnalysisReport(true, ids.size(), toAnalyze.size(), skipped, toAnalyze.size(), model, cost, 0,
                    0, null, List.of());
        }
        if (!toAnalyze.isEmpty() && model.blockedByConsent()) {
            throw new PaidCallsDisabledException(model);
        }
        budget.beginRun();
        int analyzed = 0;
        int failed = 0;
        for (int i = 0; i < toAnalyze.size(); i++) {
            progress.progress(i, toAnalyze.size());
            AnalysisResult result = analyze(toAnalyze.get(i));
            switch (result.outcome()) {
                case ANALYZED -> analyzed++;
                case FAILED, FAILED_PERMANENTLY -> failed++;
                case PREFILTER_REJECTED -> skipped.merge("prefilterRejected", 1, Integer::sum);
                case JOB_EXPIRED -> skipped.merge("expired", 1, Integer::sum);
                case LLM_PAUSED, BUDGET_REACHED -> {
                    List<Long> rest = toAnalyze.subList(i, toAnalyze.size()).stream().map(JobView::id).toList();
                    log.info("bulk analyze stopped by {} after {} job(s); {} left", result.outcome(), analyzed + failed,
                            rest.size());
                    return new BulkAnalysisReport(false, ids.size(), toAnalyze.size(), skipped, toAnalyze.size(),
                            model, cost, analyzed, failed, result.outcome(), rest);
                }
            }
        }
        log.info("bulk analyze selected={} analyzed={} failed={} skipped={}", ids.size(), analyzed, failed, skipped);
        progress.progress(toAnalyze.size(), toAnalyze.size());
        return new BulkAnalysisReport(false, ids.size(), toAnalyze.size(), skipped, toAnalyze.size(), model, cost,
                analyzed, failed, null, List.of());
    }

    private AnalysisResult analyze(JobView job) {
        if (job.status() == JobStatus.EXPIRED) {
            return new AnalysisResult(job.id(), AnalysisResult.Outcome.JOB_EXPIRED, List.of(), false,
                    "the job is no longer listed by any source");
        }
        Profile profile = profiles.current();
        String profileHash = profiles.profileHash();
        if (assessments.ensurePrefilter(job, profile, profileHash) != PrefilterResult.PASS) {
            return new AnalysisResult(job.id(), AnalysisResult.Outcome.PREFILTER_REJECTED, List.of(), false,
                    "the prefilter rejects this job for profile " + profile.id());
        }
        ExtractionResult extracted = extraction.extract(job.id());
        return switch (extracted.status()) {
            case OK -> {
                List<EligibilityCheck> checks = eligibility.evaluate(extracted.attributes(), job, profile);
                assessments.recordChecks(job.id(), profile.id(), profileHash, extracted.extractionId(), checks);
                yield new AnalysisResult(job.id(), AnalysisResult.Outcome.ANALYZED, checks, extracted.llmCalled(),
                        null);
            }
            case FAILED -> new AnalysisResult(job.id(), AnalysisResult.Outcome.FAILED, List.of(),
                    extracted.llmCalled(), extracted.message());
            case FAILED_PERMANENTLY -> new AnalysisResult(job.id(), AnalysisResult.Outcome.FAILED_PERMANENTLY,
                    List.of(), extracted.llmCalled(), extracted.message());
            case UNAVAILABLE -> new AnalysisResult(job.id(), unavailableOutcome(extracted.unavailable()), List.of(),
                    false, extracted.message());
        };
    }

    static AnalysisResult.Outcome unavailableOutcome(LlmUnavailable.Reason reason) {
        return switch (reason) {
            case RUN_CALL_LIMIT, MONTHLY_BUDGET -> AnalysisResult.Outcome.BUDGET_REACHED;
            case NO_USABLE_MODEL, PAID_NOT_ALLOWED -> AnalysisResult.Outcome.LLM_PAUSED;
        };
    }
}
