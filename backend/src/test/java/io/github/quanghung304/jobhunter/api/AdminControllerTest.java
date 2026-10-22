package io.github.quanghung304.jobhunter.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.quanghung304.jobhunter.common.ProgressBoard;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.BulkAnalysisReport;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import io.github.quanghung304.jobhunter.enrich.FailedWork;
import io.github.quanghung304.jobhunter.enrich.TooManyJobsException;
import io.github.quanghung304.jobhunter.ingest.IngestResult;
import io.github.quanghung304.jobhunter.ingest.IngestService;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.LlmAlertService;
import io.github.quanghung304.jobhunter.llm.BudgetReachedException;
import io.github.quanghung304.jobhunter.llm.LlmPausedException;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmUsage;
import io.github.quanghung304.jobhunter.llm.LlmUsageService;
import io.github.quanghung304.jobhunter.llm.ModelChoice;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.github.quanghung304.jobhunter.llm.PaidCallsDisabledException;
import io.github.quanghung304.jobhunter.pipeline.PipelineAlreadyRunningException;
import io.github.quanghung304.jobhunter.pipeline.PipelineOrchestrator;
import io.github.quanghung304.jobhunter.pipeline.PipelineReport;
import io.github.quanghung304.jobhunter.pipeline.PipelineSchedule;
import io.github.quanghung304.jobhunter.pipeline.SourceReport;
import io.github.quanghung304.jobhunter.pipeline.Trigger;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.scoring.BulkActionRunner;
import io.github.quanghung304.jobhunter.scoring.BulkScoreReport;
import io.github.quanghung304.jobhunter.scoring.RescorePlan;
import io.github.quanghung304.jobhunter.source.AtsType;
import io.github.quanghung304.jobhunter.source.CollectRequest;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.github.quanghung304.jobhunter.source.CollectorRegistry;
import io.github.quanghung304.jobhunter.source.CollectorRunOutcome;
import io.github.quanghung304.jobhunter.source.CollectorRunView;
import io.github.quanghung304.jobhunter.source.CollectorRunner;
import io.github.quanghung304.jobhunter.source.CompanyCheckStatus;
import io.github.quanghung304.jobhunter.source.CompanyService;
import io.github.quanghung304.jobhunter.source.CompanyView;
import io.github.quanghung304.jobhunter.source.JobCollector;
import io.github.quanghung304.jobhunter.tracking.JobBoardQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import({AdminViews.class, ProgressBoard.class})
class AdminControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-17T07:00:00Z");
    private static final ModelChoice FREE = new ModelChoice("groq-gpt-oss-120b", "GPT-OSS 120B (Groq)", ModelTier.FREE,
            false);

    @Autowired
    MockMvc mvc;

    @Autowired
    ProgressBoard progressBoard;

    @MockitoBean
    CollectorRegistry registry;

    @MockitoBean
    CollectorRunner runner;

    @MockitoBean
    IngestService ingestService;

    @MockitoBean
    ProfileProvider profileProvider;

    @MockitoBean
    LlmAlertService llmAlerts;

    @MockitoBean
    Clock clock;

    @MockitoBean
    PipelineOrchestrator pipeline;

    @MockitoBean
    BulkActionRunner bulkActions;

    @MockitoBean
    CompanyService companies;

    @MockitoBean
    PipelineSchedule schedule;

    @MockitoBean
    LlmUsageService llmUsage;

    @MockitoBean
    ExtractionService extraction;

    @MockitoBean
    AssessmentService assessments;

    @MockitoBean
    JobQueryService jobs;

    @MockitoBean
    JobBoardQuery board;

    private final JobCollector arbeitnow = mock(JobCollector.class);

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(profileProvider.current()).willReturn(ProfileFixtures.example()); // maxAgeDays 21, id hugh
        given(registry.find("arbeitnow")).willReturn(Optional.of(arbeitnow));
        given(runner.defaultRequest("arbeitnow", NOW.minus(Duration.ofDays(21))))
                .willReturn(new CollectRequest(NOW.minus(Duration.ofDays(21)), List.of(), 200));
    }

    @Test
    void collectsIngestsAndRecordsTheCounts() throws Exception {
        var result = new CollectResult(List.of(), 300, List.of("page 1 item x: missing slug"),
                CollectStatus.PARTIAL, null);
        given(runner.runNow(arbeitnow, new CollectRequest(NOW.minus(Duration.ofDays(21)), List.of(), 200)))
                .willReturn(new CollectorRunOutcome(7L, result, null));
        given(ingestService.ingest(anyList())).willReturn(new IngestResult(12, 9, 2, 1));

        mvc.perform(post("/api/admin/collect/arbeitnow"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.runId").value(7))
                .andExpect(jsonPath("$.collectStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.fetched").value(300))
                .andExpect(jsonPath("$.newRaw").value(12))
                .andExpect(jsonPath("$.newJobs").value(9))
                .andExpect(jsonPath("$.updatedJobs").value(2))
                .andExpect(jsonPath("$.skippedInvalid").value(1))
                .andExpect(jsonPath("$.warnings[0]").value("page 1 item x: missing slug"))
                .andExpect(jsonPath("$.error").isEmpty());
        verify(runner).recordIngestCounts(7L, 12, 9);
    }

    @Test
    void failedCollectorStillAnswersWithTheError() throws Exception {
        given(runner.runNow(arbeitnow, new CollectRequest(NOW.minus(Duration.ofDays(21)), List.of(), 200)))
                .willReturn(new CollectorRunOutcome(8L, CollectResult.failed(0, "page 1: 503"), null));
        given(ingestService.ingest(List.of())).willReturn(IngestResult.EMPTY);

        mvc.perform(post("/api/admin/collect/arbeitnow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectStatus").value("FAILED"))
                .andExpect(jsonPath("$.error").value("page 1: 503"))
                .andExpect(jsonPath("$.newRaw").value(0));
        verify(runner).recordIngestCounts(8L, 0, 0);
    }

    @Test
    void unknownSourceIsNotFound() throws Exception {
        given(registry.find("linkedin")).willReturn(Optional.empty());

        mvc.perform(post("/api/admin/collect/linkedin"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));
    }

    @Test
    void pipelineRunIsAcceptedAndStartedAsync() throws Exception {
        mvc.perform(post("/api/admin/pipeline/run"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));

        verify(pipeline).startAsync(Trigger.MANUAL);
    }

    @Test
    void pipelineRunWhileRunningIs409() throws Exception {
        willThrow(new PipelineAlreadyRunningException()).given(pipeline).startAsync(Trigger.MANUAL);

        mvc.perform(post("/api/admin/pipeline/run"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIPELINE_ALREADY_RUNNING"));
    }

    @Test
    void overviewHasEverySection() throws Exception {
        Instant midnight = Instant.parse("2026-10-16T22:00:00Z");
        given(registry.allCodes()).willReturn(List.of("arbeitnow", "himalayas"));
        given(registry.isEnabled("arbeitnow")).willReturn(true);
        given(runner.lastRun("arbeitnow")).willReturn(Optional.of(new CollectorRunView(5, "arbeitnow",
                CollectStatus.SUCCESS, NOW.minusSeconds(60), NOW, 300, 12, 9, null)));
        given(runner.lastSuccessAt("arbeitnow")).willReturn(Optional.of(NOW.minusSeconds(60)));
        given(pipeline.recentReports()).willReturn(List.of(new PipelineReport(3, Trigger.SCHEDULED, NOW.minusSeconds(90),
                NOW, List.of(new SourceReport("arbeitnow", CollectStatus.SUCCESS, 300, 12, 9, 1, null),
                        new SourceReport("himalayas", null, 0, 0, 0, 0, "min-interval")),
                9, 0, 40, 31, 9, 25, 2, 0, null)));
        given(pipeline.isRunning()).willReturn(true);
        given(schedule.zone()).willReturn(ZoneId.of("Europe/Rome"));
        given(schedule.startOfToday()).willReturn(midnight);
        given(schedule.nextRunAt()).willReturn(Optional.of(Instant.parse("2026-10-17T17:00:00Z")));
        given(schedule.nextDigestAt()).willReturn(Optional.empty());
        given(llmUsage.usage(ZoneId.of("Europe/Rome"), AdminViews.USAGE_DAYS)).willReturn(new LlmUsage(
                new BigDecimal("0.42"), new BigDecimal("5.00"), 12,
                List.of(new LlmUsage.Day(LocalDate.parse("2026-10-17"), 12, 24_000, 3_000, BigDecimal.ZERO))));
        given(extraction.failedJobs()).willReturn(List.of(new FailedWork(42, 2)));
        given(assessments.failedScorings("hugh")).willReturn(List.of());
        given(jobs.findAll(List.of(42L))).willReturn(List.of(job(42, "Java Developer")));
        given(llmUsage.lastErrors(LlmPurpose.EXTRACT, List.of(42L))).willReturn(Map.of(42L, "INVALID_OUTPUT: x"));
        given(llmUsage.lastErrors(LlmPurpose.SCORE, List.of())).willReturn(Map.of());
        given(jobs.countActive()).willReturn(1384L);
        given(jobs.countFirstSeenSince(midnight)).willReturn(42L);
        given(assessments.countPrefilterRejectedSince("hugh", midnight)).willReturn(31L);
        given(assessments.countScoredSince("hugh", midnight)).willReturn(9L);
        given(board.countDefaultView("hugh")).willReturn(37L);
        given(companies.all()).willReturn(List.of(company(true), company(false)));

        try (ProgressBoard.Activity activity = progressBoard.begin(ProgressBoard.Stage.SCORE, "Score")) {
            activity.update(ProgressBoard.Stage.SCORE, "Score 14 / 27", 14, 27);

            mvc.perform(get("/api/admin/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sources[0].code").value("arbeitnow"))
                    .andExpect(jsonPath("$.sources[0].name").value("Arbeitnow"))
                    .andExpect(jsonPath("$.sources[0].enabled").value(true))
                    .andExpect(jsonPath("$.sources[0].lastRun.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.sources[0].lastRun.newJobs").value(9))
                    .andExpect(jsonPath("$.sources[0].lastSuccessAt").value("2026-10-17T06:59:00Z"))
                    .andExpect(jsonPath("$.sources[1].enabled").value(false))
                    .andExpect(jsonPath("$.sources[1].lastRun").isEmpty())
                    .andExpect(jsonPath("$.pipelineReports[0].runNumber").value(3))
                    .andExpect(jsonPath("$.pipelineReports[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$.pipelineReports[0].durationMs").value(90_000))
                    .andExpect(jsonPath("$.pipelineReports[0].sources[1].status").isEmpty())
                    .andExpect(jsonPath("$.pipelineReports[0].notAnalyzed").value(25))
                    .andExpect(jsonPath("$.pipelineReports[0].llmCalls").value(0))
                    .andExpect(jsonPath("$.llmUsage.monthToDateUsd").value(0.42))
                    .andExpect(jsonPath("$.llmUsage.monthlyBudgetUsd").value(5.00))
                    .andExpect(jsonPath("$.llmUsage.callsToday").value(12))
                    .andExpect(jsonPath("$.llmUsage.daily[0].date").value("2026-10-17"))
                    .andExpect(jsonPath("$.llmUsage.daily[0].inputTokens").value(24_000))
                    .andExpect(jsonPath("$.failures.extractions[0].jobId").value(42))
                    .andExpect(jsonPath("$.failures.extractions[0].title").value("Java Developer"))
                    .andExpect(jsonPath("$.failures.extractions[0].attempts").value(2))
                    .andExpect(jsonPath("$.failures.extractions[0].lastError").value("INVALID_OUTPUT: x"))
                    .andExpect(jsonPath("$.failures.scorings").isEmpty())
                    .andExpect(jsonPath("$.pipelineRunning").value(true))
                    .andExpect(jsonPath("$.pipelineProgress.stage").value("SCORE"))
                    .andExpect(jsonPath("$.pipelineProgress.text").value("Score 14 / 27"))
                    .andExpect(jsonPath("$.pipelineProgress.percent").value(51))
                    .andExpect(jsonPath("$.schedule.nextRunAt").value("2026-10-17T17:00:00Z"))
                    .andExpect(jsonPath("$.schedule.nextDigestAt").isEmpty())
                    .andExpect(jsonPath("$.schedule.zone").value("Europe/Rome"))
                    .andExpect(jsonPath("$.kpis.activeJobs").value(1384))
                    .andExpect(jsonPath("$.kpis.newToday").value(42))
                    .andExpect(jsonPath("$.kpis.prefilterRejectedToday").value(31))
                    .andExpect(jsonPath("$.kpis.scoredToday").value(9))
                    .andExpect(jsonPath("$.kpis.toReview").value(37))
                    .andExpect(jsonPath("$.kpis.sourceCount").value(1))
                    .andExpect(jsonPath("$.kpis.companyBoardCount").value(1))
                    .andExpect(jsonPath("$.llmAlerts").isEmpty());
        }

        mvc.perform(get("/api/admin/overview"))
                .andExpect(jsonPath("$.pipelineProgress").isEmpty());
    }

    @Test
    void bulkAnalyzeDryRunAnswersTheCounts() throws Exception {
        given(bulkActions.analyze(List.of(42L, 57L), true)).willReturn(new BulkAnalysisReport(true, 2, 1,
                Map.of("alreadyAnalyzed", 1), 1, FREE, BigDecimal.ZERO, 0, 0, null, List.of()));

        mvc.perform(post("/api/admin/jobs/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobIds\":[42,57],\"dryRun\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected").value(2))
                .andExpect(jsonPath("$.willAnalyze").value(1))
                .andExpect(jsonPath("$.skipped.alreadyAnalyzed").value(1))
                .andExpect(jsonPath("$.skipped.expired").value(0))
                .andExpect(jsonPath("$.skipped.prefilterRejected").value(0))
                .andExpect(jsonPath("$.skipped.failedPermanently").value(0))
                .andExpect(jsonPath("$.skipped.notFound").value(0))
                .andExpect(jsonPath("$.estimatedCalls").value(1))
                .andExpect(jsonPath("$.estimatedCostUsd").value(0))
                .andExpect(jsonPath("$.model.modelId").value("groq-gpt-oss-120b"))
                .andExpect(jsonPath("$.model.tier").value("FREE"));
    }

    @Test
    void confirmedBulkAnalyzeIsAccepted() throws Exception {
        given(bulkActions.analyze(List.of(42L), false)).willReturn(new BulkAnalysisReport(true, 1, 1, Map.of(), 1,
                FREE, BigDecimal.ZERO, 0, 0, null, List.of()));

        mvc.perform(post("/api/admin/jobs/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobIds\":[42],\"dryRun\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));
        verify(bulkActions).analyze(List.of(42L), false);
    }

    @Test
    void bulkActionErrorsHaveTheirCodes() throws Exception {
        given(bulkActions.analyze(anyList(), eq(false))).willThrow(new TooManyJobsException(201, 200));
        mvc.perform(post("/api/admin/jobs/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobIds\":[1],\"dryRun\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_JOBS"));

        given(bulkActions.score(anyList(), any(), eq(false))).willThrow(new PaidCallsDisabledException(
                new ModelChoice("claude-haiku", "Claude Haiku", ModelTier.PAID, false)));
        mvc.perform(post("/api/admin/jobs/score").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobIds\":[1],\"dryRun\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAID_CALLS_DISABLED"));

        given(bulkActions.rescore(any(), eq(false))).willThrow(new LlmPausedException(LlmPurpose.SCORE, "EXHAUSTED"));
        mvc.perform(post("/api/admin/rescore").contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LLM_PAUSED"))
                .andExpect(jsonPath("$.purpose").value("SCORE"));

        willThrow(new BudgetReachedException("monthly")).given(bulkActions).score(anyList(), any(), eq(false));
        mvc.perform(post("/api/admin/jobs/score").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobIds\":[1],\"dryRun\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUDGET_REACHED"));

        mvc.perform(post("/api/admin/jobs/score").contentType(MediaType.APPLICATION_JSON).content("{\"jobIds\":[1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void bulkScoreDryRunNamesTheModel() throws Exception {
        var paid = new ModelChoice("claude-haiku", "Claude Haiku 5.5", ModelTier.PAID, true);
        given(bulkActions.score(eq(List.of(1L, 2L, 3L)), any(), eq(true))).willReturn(new BulkScoreReport(true, 3, 1,
                Map.of("notAnalyzed", 1, "markedIneligible", 1), 1, paid, null, 0, 0, null, List.of()));

        mvc.perform(post("/api/admin/jobs/score").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobIds\":[1,2,3],\"dryRun\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.willScore").value(1))
                .andExpect(jsonPath("$.skipped.notAnalyzed").value(1))
                .andExpect(jsonPath("$.skipped.markedIneligible").value(1))
                .andExpect(jsonPath("$.skipped.alreadyScored").value(0))
                .andExpect(jsonPath("$.estimatedCostUsd").isEmpty())
                .andExpect(jsonPath("$.model.label").value("Claude Haiku 5.5"))
                .andExpect(jsonPath("$.model.tier").value("PAID"))
                .andExpect(jsonPath("$.model.paidCallsAllowed").value(true));
    }

    @Test
    void rescoreDryRunAndConfirmation() throws Exception {
        given(bulkActions.rescore(any(), eq(true))).willReturn(new RescorePlan(true, 57, 57, FREE, BigDecimal.ZERO, 0,
                0, null, List.of()));
        given(bulkActions.rescore(any(), eq(false))).willReturn(new RescorePlan(true, 57, 57, FREE, BigDecimal.ZERO, 0,
                0, null, List.of()));

        mvc.perform(post("/api/admin/rescore").contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobCount").value(57))
                .andExpect(jsonPath("$.estimatedCalls").value(57))
                .andExpect(jsonPath("$.estimatedCostUsd").value(0))
                .andExpect(jsonPath("$.model.modelId").value("groq-gpt-oss-120b"));
        mvc.perform(post("/api/admin/rescore").contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void retryIsAcceptedAndUnknownJobIs404() throws Exception {
        mvc.perform(post("/api/admin/jobs/42/retry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"SCORE\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));
        verify(bulkActions).retry(eq(42L), eq(LlmPurpose.SCORE), any());

        willThrow(new JobNotFoundException(7)).given(bulkActions).retry(eq(7L), eq(LlmPurpose.EXTRACT), any());
        mvc.perform(post("/api/admin/jobs/7/retry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"EXTRACT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));

        mvc.perform(post("/api/admin/jobs/7/retry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"EVAL\"}"))
                .andExpect(status().isBadRequest());
        verify(bulkActions, never()).retry(eq(7L), eq(LlmPurpose.EVAL), any());
    }

    @Test
    void companiesWithActiveJobCounts() throws Exception {
        given(companies.all()).willReturn(List.of(company(true)));
        given(jobs.countActiveByCompanyKey(anyCollection())).willReturn(Map.of("acme", 4L));

        mvc.perform(get("/api/admin/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Acme GmbH"))
                .andExpect(jsonPath("$[0].ats").value("GREENHOUSE"))
                .andExpect(jsonPath("$[0].slug").value("acme"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].lastCheckStatus").value("OK"))
                .andExpect(jsonPath("$[0].activeJobs").value(4));
    }

    @Test
    void evalExportIsNotImplementedYet() throws Exception {
        mvc.perform(post("/api/admin/eval/export"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    private static CompanyView company(boolean enabled) {
        return new CompanyView(enabled ? 1 : 2, "Acme GmbH", AtsType.GREENHOUSE, "acme", enabled,
                CompanyCheckStatus.OK, NOW);
    }

    private static JobView job(long id, String title) {
        return new JobView(id, title, "Acme", "acme", null, null, null, "x", null, null, null, null, null, List.of(),
                null, NOW, NOW, JobStatus.ACTIVE);
    }
}
