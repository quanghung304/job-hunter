package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.common.ProgressBoard;
import io.github.quanghung304.jobhunter.common.text.TextNormalizer;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.ExtractionService;
import io.github.quanghung304.jobhunter.enrich.FailedWork;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmUsage;
import io.github.quanghung304.jobhunter.llm.LlmUsageService;
import io.github.quanghung304.jobhunter.pipeline.PipelineOrchestrator;
import io.github.quanghung304.jobhunter.pipeline.PipelineReport;
import io.github.quanghung304.jobhunter.pipeline.PipelineSchedule;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.source.CollectorRegistry;
import io.github.quanghung304.jobhunter.source.CollectorRunner;
import io.github.quanghung304.jobhunter.source.CompanyService;
import io.github.quanghung304.jobhunter.source.CompanyView;
import io.github.quanghung304.jobhunter.source.SourceCatalog;
import io.github.quanghung304.jobhunter.tracking.JobBoardQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Reads the module services and maps them to the admin page responses (overview, status,
 * companies). Read-only: no LLM call, no write.
 */
@Component
class AdminViews {

    static final int USAGE_DAYS = 14;

    private final CollectorRegistry collectors;
    private final CollectorRunner runner;
    private final CompanyService companies;
    private final PipelineOrchestrator pipeline;
    private final PipelineSchedule schedule;
    private final ProgressBoard progressBoard;
    private final LlmUsageService llmUsage;
    private final ExtractionService extraction;
    private final AssessmentService assessments;
    private final JobQueryService jobs;
    private final JobBoardQuery board;
    private final ProfileProvider profiles;

    AdminViews(CollectorRegistry collectors, CollectorRunner runner, CompanyService companies,
            PipelineOrchestrator pipeline, PipelineSchedule schedule, ProgressBoard progressBoard,
            LlmUsageService llmUsage, ExtractionService extraction, AssessmentService assessments,
            JobQueryService jobs, JobBoardQuery board, ProfileProvider profiles) {
        this.collectors = collectors;
        this.runner = runner;
        this.companies = companies;
        this.pipeline = pipeline;
        this.schedule = schedule;
        this.progressBoard = progressBoard;
        this.llmUsage = llmUsage;
        this.extraction = extraction;
        this.assessments = assessments;
        this.jobs = jobs;
        this.board = board;
        this.profiles = profiles;
    }

    AdminOverviewResponse overview() {
        String profileId = profiles.current().id();
        List<AdminOverviewResponse.SourceStatus> sources = collectors.allCodes().stream().map(this::sourceStatus)
                .toList();
        LlmUsage usage = llmUsage.usage(schedule.zone(), USAGE_DAYS);
        Instant today = schedule.startOfToday();
        var kpis = new AdminOverviewResponse.Kpis(
                jobs.countActive(),
                jobs.countFirstSeenSince(today),
                assessments.countPrefilterRejectedSince(profileId, today),
                assessments.countScoredSince(profileId, today),
                board.countDefaultView(profileId),
                (int) sources.stream().filter(AdminOverviewResponse.SourceStatus::enabled).count(),
                (int) companies.all().stream().filter(CompanyView::enabled).count());
        return new AdminOverviewResponse(
                sources,
                pipeline.recentReports().stream().map(PipelineReportResponse::of).toList(),
                new AdminOverviewResponse.LlmUsage(usage.monthToDateUsd(), usage.monthlyBudgetUsd(), usage.callsToday(),
                        usage.daily().stream()
                                .map(d -> new AdminOverviewResponse.DailyUsage(d.date(), d.calls(), d.inputTokens(),
                                        d.outputTokens(), d.costUsd()))
                                .toList()),
                new AdminOverviewResponse.Failures(
                        failedJobs(extraction.failedJobs(), LlmPurpose.EXTRACT),
                        failedJobs(assessments.failedScorings(profileId), LlmPurpose.SCORE)),
                pipeline.isRunning(),
                progressBoard.current()
                        .map(p -> new AdminOverviewResponse.Progress(p.stage(), p.text(), p.percent()))
                        .orElse(null),
                new AdminOverviewResponse.Schedule(schedule.nextRunAt().orElse(null),
                        schedule.nextDigestAt().orElse(null), schedule.zone().getId()),
                kpis,
                List.of()); // LLM alerts come with T-022
    }

    StatusResponse status() {
        String profileId = profiles.current().id();
        StatusResponse.LastRun lastRun = pipeline.lastReport()
                .map(report -> lastRun(report, profileId))
                .orElse(null);
        return new StatusResponse(lastRun, pipeline.isRunning(), List.of()); // LLM alerts come with T-022
    }

    List<CompanyResponse> companies() {
        List<CompanyView> all = companies.all();
        Map<String, Long> activeJobs = jobs.countActiveByCompanyKey(
                all.stream().map(c -> TextNormalizer.companyKey(c.name())).distinct().toList());
        return all.stream()
                .map(c -> new CompanyResponse(c.id(), c.name(), c.ats(), c.slug(), c.enabled(), c.lastCheckStatus(),
                        c.lastCheckedAt(), activeJobs.getOrDefault(TextNormalizer.companyKey(c.name()), 0L)))
                .toList();
    }

    private AdminOverviewResponse.SourceStatus sourceStatus(String code) {
        return new AdminOverviewResponse.SourceStatus(code, SourceCatalog.name(code), collectors.isEnabled(code),
                runner.lastRun(code)
                        .map(run -> new AdminOverviewResponse.LastRun(run.status(), run.startedAt(), run.fetched(),
                                run.newRaw(), run.newJobs(), run.error()))
                        .orElse(null),
                runner.lastSuccessAt(code).orElse(null));
    }

    private List<AdminOverviewResponse.FailedJob> failedJobs(List<FailedWork> failed, LlmPurpose purpose) {
        List<Long> ids = failed.stream().map(FailedWork::jobId).toList();
        Map<Long, String> titles = jobs.findAll(ids).stream()
                .collect(Collectors.toMap(JobView::id, JobView::title));
        Map<Long, String> errors = llmUsage.lastErrors(purpose, ids);
        return failed.stream()
                .map(f -> new AdminOverviewResponse.FailedJob(f.jobId(), titles.getOrDefault(f.jobId(), "?"),
                        f.attempts(), errors.get(f.jobId())))
                .toList();
    }

    private StatusResponse.LastRun lastRun(PipelineReport report, String profileId) {
        return new StatusResponse.LastRun(report.startedAt(), report.newJobs(),
                assessments.countScoredSince(profileId, report.startedAt()), report.status());
    }
}
