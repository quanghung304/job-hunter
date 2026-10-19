package io.github.quanghung304.jobhunter.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.quanghung304.jobhunter.common.ProgressBoard;
import io.github.quanghung304.jobhunter.common.ProgressListener;
import io.github.quanghung304.jobhunter.enrich.AnalysisService;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.BulkAnalysisReport;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.BudgetReachedException;
import io.github.quanghung304.jobhunter.llm.LlmPausedException;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmRouter;
import io.github.quanghung304.jobhunter.llm.LlmUnavailable;
import io.github.quanghung304.jobhunter.llm.ModelChoice;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.github.quanghung304.jobhunter.llm.PaidCallsDisabledException;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Pre-checks and background start of the admin bulk LLM actions (services mocked: no LLM). */
class BulkActionRunnerTest {

    static final ModelChoice FREE = new ModelChoice("free", "Free", ModelTier.FREE, false);
    static final ModelChoice PAID_NO_CONSENT = new ModelChoice("paid", "Paid", ModelTier.PAID, false);
    static final Profile PROFILE = ProfileFixtures.example();

    final AnalysisService analysis = mock(AnalysisService.class);
    final ScoringService scoring = mock(ScoringService.class);
    final ExtractionService extraction = mock(ExtractionService.class);
    final AssessmentService assessments = mock(AssessmentService.class);
    final JobQueryService jobs = mock(JobQueryService.class);
    final LlmRouter router = mock(LlmRouter.class);
    final ProgressBoard progress = new ProgressBoard();
    final BulkActionRunner runner = new BulkActionRunner(analysis, scoring, extraction, assessments, jobs, router,
            progress);

    @AfterEach
    void tearDown() {
        runner.shutdown();
    }

    static BulkAnalysisReport analysisPlan(int willAnalyze, ModelChoice model) {
        return new BulkAnalysisReport(true, willAnalyze, willAnalyze, Map.of(), willAnalyze, model, BigDecimal.ZERO,
                0, 0, null, List.of());
    }

    @Test
    void dryRunOnlyCounts() {
        given(analysis.analyzeMany(List.of(1L, 2L), true)).willReturn(analysisPlan(2, FREE));

        assertThat(runner.analyze(List.of(1L, 2L), true).willAnalyze()).isEqualTo(2);

        verify(analysis, after(200).never()).analyzeMany(any(), eq(false), any(ProgressListener.class));
        verify(router, never()).availability(any());
    }

    @Test
    void confirmedRunStartsInTheBackground() {
        given(analysis.analyzeMany(List.of(1L), true)).willReturn(analysisPlan(1, FREE));
        given(router.availability(LlmPurpose.EXTRACT)).willReturn(Optional.empty());

        runner.analyze(List.of(1L), false);

        verify(analysis, timeout(2000)).analyzeMany(eq(List.of(1L)), eq(false), any(ProgressListener.class));
    }

    @Test
    void paidModelWithoutConsentIsRefusedBeforeTheRun() {
        given(scoring.scoreSelected(List.of(1L), PROFILE, true)).willReturn(new BulkScoreReport(true, 1, 1, Map.of(),
                1, PAID_NO_CONSENT, null, 0, 0, null, List.of()));

        assertThatThrownBy(() -> runner.score(List.of(1L), PROFILE, false))
                .isInstanceOf(PaidCallsDisabledException.class);
        verify(scoring, after(200).never()).scoreSelected(any(), any(), eq(false), any(ProgressListener.class));
    }

    @Test
    void pausedModelOrBudgetIsRefusedBeforeTheRun() {
        given(scoring.rescoreActive(PROFILE, true)).willReturn(new RescorePlan(true, 3, 3, FREE, BigDecimal.ZERO, 0,
                0, null, List.of()));
        given(router.availability(LlmPurpose.SCORE)).willReturn(Optional.of(LlmUnavailable.Reason.NO_USABLE_MODEL));

        assertThatThrownBy(() -> runner.rescore(PROFILE, false))
                .isInstanceOfSatisfying(LlmPausedException.class,
                        e -> assertThat(e.purpose()).isEqualTo(LlmPurpose.SCORE));

        given(router.availability(LlmPurpose.SCORE)).willReturn(Optional.of(LlmUnavailable.Reason.MONTHLY_BUDGET));
        assertThatThrownBy(() -> runner.rescore(PROFILE, false)).isInstanceOf(BudgetReachedException.class);
        verify(scoring, after(200).never()).rescoreActive(any(), eq(false), any(ProgressListener.class));
    }

    @Test
    void nothingToDoIsNotRefused() {
        given(analysis.analyzeMany(List.of(1L), true)).willReturn(analysisPlan(0, PAID_NO_CONSENT));

        runner.analyze(List.of(1L), false);

        verify(router, never()).availability(any());
    }

    @Test
    void retryResetsTheAttemptsThenRunsInTheBackground() {
        given(jobs.find(42)).willReturn(Optional.of(new JobView(42, "t", "c", "c", null, null, null, "d", null, null,
                null, null, null, List.of(), null, Instant.EPOCH, Instant.EPOCH, JobStatus.ACTIVE)));

        runner.retry(42, LlmPurpose.SCORE, PROFILE);

        InOrder order = inOrder(assessments, scoring);
        order.verify(assessments).resetScoreAttempts(42, "hugh");
        order.verify(scoring, timeout(2000)).scoreSelected(eq(List.of(42L)), eq(PROFILE), eq(false),
                any(ProgressListener.class));
    }

    @Test
    void retryOfUnknownJobIsNotFound() {
        given(jobs.find(7)).willReturn(Optional.empty());

        assertThatThrownBy(() -> runner.retry(7, LlmPurpose.EXTRACT, PROFILE)).isInstanceOf(JobNotFoundException.class);
        verify(extraction, never()).resetAttempts(7);
        verify(analysis, after(200).never()).analyzeMany(any(), anyBoolean(), any(ProgressListener.class));
    }
}
