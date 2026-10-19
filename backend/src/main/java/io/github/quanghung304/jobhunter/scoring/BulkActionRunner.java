package io.github.quanghung304.jobhunter.scoring;

import io.github.quanghung304.jobhunter.common.ProgressBoard;
import io.github.quanghung304.jobhunter.common.ProgressBoard.Stage;
import io.github.quanghung304.jobhunter.common.ProgressListener;
import io.github.quanghung304.jobhunter.enrich.AnalysisService;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.BulkAnalysisReport;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.llm.BudgetReachedException;
import io.github.quanghung304.jobhunter.llm.LlmPausedException;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmRouter;
import io.github.quanghung304.jobhunter.llm.LlmUnavailable;
import io.github.quanghung304.jobhunter.llm.ModelChoice;
import io.github.quanghung304.jobhunter.llm.PaidCallsDisabledException;
import io.github.quanghung304.jobhunter.profile.Profile;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The admin's bulk LLM actions (ADR-0010): bulk Analyze, bulk Score, "Re-score active jobs" and
 * "Retry". A dry run answers at once. A confirmed run is checked first (PAID model without
 * consent, paused model, budget) and then runs in the background, one action at a time,
 * publishing its progress on the {@link ProgressBoard} (stage ANALYZE or SCORE).
 */
@Service
public class BulkActionRunner {

    private static final Logger log = LoggerFactory.getLogger(BulkActionRunner.class);

    private final AnalysisService analysis;
    private final ScoringService scoring;
    private final ExtractionService extraction;
    private final AssessmentService assessments;
    private final JobQueryService jobs;
    private final LlmRouter router;
    private final ProgressBoard progressBoard;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "llm-actions");
        thread.setDaemon(true);
        return thread;
    });

    public BulkActionRunner(AnalysisService analysis, ScoringService scoring, ExtractionService extraction,
            AssessmentService assessments, JobQueryService jobs, LlmRouter router, ProgressBoard progressBoard) {
        this.analysis = analysis;
        this.scoring = scoring;
        this.extraction = extraction;
        this.assessments = assessments;
        this.jobs = jobs;
        this.router = router;
        this.progressBoard = progressBoard;
    }

    /** Bulk Analyze of selected jobs: the dry run's counts; a confirmed run starts in the background. */
    public BulkAnalysisReport analyze(List<Long> jobIds, boolean dryRun) {
        BulkAnalysisReport plan = analysis.analyzeMany(jobIds, true);
        if (!dryRun) {
            checkCanStart(LlmPurpose.EXTRACT, plan.model(), plan.willAnalyze());
            submit(Stage.ANALYZE, "Analyze", listener -> analysis.analyzeMany(jobIds, false, listener));
        }
        return plan;
    }

    /** Bulk Score of selected jobs: the dry run's counts; a confirmed run starts in the background. */
    public BulkScoreReport score(List<Long> jobIds, Profile profile, boolean dryRun) {
        BulkScoreReport plan = scoring.scoreSelected(jobIds, profile, true);
        if (!dryRun) {
            checkCanStart(LlmPurpose.SCORE, plan.model(), plan.willScore());
            submit(Stage.SCORE, "Score", listener -> scoring.scoreSelected(jobIds, profile, false, listener));
        }
        return plan;
    }

    /** "Re-score active jobs": the dry run's counts; a confirmed run starts in the background. */
    public RescorePlan rescore(Profile profile, boolean dryRun) {
        RescorePlan plan = scoring.rescoreActive(profile, true);
        if (!dryRun) {
            checkCanStart(LlmPurpose.SCORE, plan.model(), plan.jobCount());
            submit(Stage.SCORE, "Re-score", listener -> scoring.rescoreActive(profile, false, listener));
        }
        return plan;
    }

    /**
     * Admin "Retry" of failed LLM work: gives the job its attempts back for the current prompt
     * version and runs Analyze (EXTRACT) or Score (SCORE) for it in the background.
     */
    public void retry(long jobId, LlmPurpose purpose, Profile profile) {
        if (jobs.find(jobId).isEmpty()) {
            throw new JobNotFoundException(jobId);
        }
        switch (purpose) {
            case EXTRACT -> {
                extraction.resetAttempts(jobId);
                submit(Stage.ANALYZE, "Retry analysis",
                        listener -> analysis.analyzeMany(List.of(jobId), false, listener));
            }
            case SCORE -> {
                assessments.resetScoreAttempts(jobId, profile.id());
                submit(Stage.SCORE, "Retry scoring",
                        listener -> scoring.scoreSelected(List.of(jobId), profile, false, listener));
            }
            default -> throw new IllegalArgumentException("retry is for EXTRACT or SCORE, not " + purpose);
        }
    }

    /** Refuses a run that could not make a single call; a run with nothing to do is fine. */
    private void checkCanStart(LlmPurpose purpose, ModelChoice model, int jobCount) {
        if (jobCount == 0) {
            return;
        }
        if (model.blockedByConsent()) {
            throw new PaidCallsDisabledException(model);
        }
        router.availability(purpose).ifPresent(reason -> {
            if (reason == LlmUnavailable.Reason.MONTHLY_BUDGET || reason == LlmUnavailable.Reason.RUN_CALL_LIMIT) {
                throw new BudgetReachedException("monthly budget of PAID calls used up");
            }
            throw new LlmPausedException(purpose, reason.name());
        });
    }

    private void submit(Stage stage, String label, Function<ProgressListener, Object> action) {
        executor.submit(() -> {
            try (ProgressBoard.Activity activity = progressBoard.begin(stage, label)) {
                Object result = action.apply((done, total) ->
                        activity.update(stage, label + " " + done + " / " + total, done, total));
                log.info("{} finished: {}", label, result);
            } catch (RuntimeException e) {
                log.error("{} failed", label, e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
