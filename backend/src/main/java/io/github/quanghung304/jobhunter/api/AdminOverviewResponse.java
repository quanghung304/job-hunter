package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.common.ProgressBoard;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code GET /api/admin/overview}: everything the admin page shows. "Today" = since local midnight. */
public record AdminOverviewResponse(
        @Schema(requiredMode = REQUIRED) List<SourceStatus> sources,
        @Schema(requiredMode = REQUIRED, description = "Newest first, at most 20")
        List<PipelineReportResponse> pipelineReports,
        @Schema(requiredMode = REQUIRED) LlmUsage llmUsage,
        @Schema(requiredMode = REQUIRED) Failures failures,
        @Schema(requiredMode = REQUIRED) boolean pipelineRunning,
        @Schema(requiredMode = REQUIRED, nullable = true,
                description = "Pipeline run or bulk LLM action in progress; null when idle") Progress pipelineProgress,
        @Schema(requiredMode = REQUIRED) Schedule schedule,
        @Schema(requiredMode = REQUIRED) Kpis kpis,
        @Schema(requiredMode = REQUIRED) List<LlmAlertResponse> llmAlerts) {

    public AdminOverviewResponse {
        sources = List.copyOf(sources);
        pipelineReports = List.copyOf(pipelineReports);
        llmAlerts = List.copyOf(llmAlerts);
    }

    /** One registered collector. */
    @Schema(name = "AdminSourceStatus")
    public record SourceStatus(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) boolean enabled,
            @Schema(requiredMode = REQUIRED, nullable = true) LastRun lastRun,
            @Schema(requiredMode = REQUIRED, nullable = true) Instant lastSuccessAt) {
    }

    /** The newest {@code collector_run} of a source; {@code status} is null while it runs. */
    @Schema(name = "AdminSourceLastRun")
    public record LastRun(
            @Schema(requiredMode = REQUIRED, nullable = true) CollectStatus status,
            @Schema(requiredMode = REQUIRED) Instant startedAt,
            @Schema(requiredMode = REQUIRED) int fetched,
            @Schema(requiredMode = REQUIRED) int newRaw,
            @Schema(requiredMode = REQUIRED) int newJobs,
            @Schema(requiredMode = REQUIRED, nullable = true) String error) {
    }

    @Schema(name = "AdminLlmUsage")
    public record LlmUsage(
            @Schema(requiredMode = REQUIRED, description = "PAID cost of the current UTC month")
            BigDecimal monthToDateUsd,
            @Schema(requiredMode = REQUIRED) BigDecimal monthlyBudgetUsd,
            @Schema(requiredMode = REQUIRED) long callsToday,
            @Schema(requiredMode = REQUIRED, description = "Last 14 local days, oldest first") List<DailyUsage> daily) {

        public LlmUsage {
            daily = List.copyOf(daily);
        }
    }

    @Schema(name = "AdminDailyLlmUsage")
    public record DailyUsage(
            @Schema(requiredMode = REQUIRED) LocalDate date,
            @Schema(requiredMode = REQUIRED) long calls,
            @Schema(requiredMode = REQUIRED) long inputTokens,
            @Schema(requiredMode = REQUIRED) long outputTokens,
            @Schema(requiredMode = REQUIRED) BigDecimal costUsd) {
    }

    /** Failed LLM work with a Retry button. */
    @Schema(name = "AdminFailures")
    public record Failures(
            @Schema(requiredMode = REQUIRED) List<FailedJob> extractions,
            @Schema(requiredMode = REQUIRED) List<FailedJob> scorings) {

        public Failures {
            extractions = List.copyOf(extractions);
            scorings = List.copyOf(scorings);
        }
    }

    @Schema(name = "AdminFailedJob")
    public record FailedJob(
            @Schema(requiredMode = REQUIRED) long jobId,
            @Schema(requiredMode = REQUIRED) String title,
            @Schema(requiredMode = REQUIRED) int attempts,
            @Schema(requiredMode = REQUIRED, nullable = true) String lastError) {
    }

    @Schema(name = "AdminProgress")
    public record Progress(
            @Schema(requiredMode = REQUIRED) ProgressBoard.Stage stage,
            @Schema(requiredMode = REQUIRED) String text,
            @Schema(requiredMode = REQUIRED, minimum = "0", maximum = "100") int percent) {
    }

    /** Next fire times; null when the schedule is disabled. */
    @Schema(name = "AdminSchedule")
    public record Schedule(
            @Schema(requiredMode = REQUIRED, nullable = true) Instant nextRunAt,
            @Schema(requiredMode = REQUIRED, nullable = true) Instant nextDigestAt,
            @Schema(requiredMode = REQUIRED, example = "Europe/Rome") String zone) {
    }

    @Schema(name = "AdminKpis")
    public record Kpis(
            @Schema(requiredMode = REQUIRED) long activeJobs,
            @Schema(requiredMode = REQUIRED) long newToday,
            @Schema(requiredMode = REQUIRED) long prefilterRejectedToday,
            @Schema(requiredMode = REQUIRED) long scoredToday,
            @Schema(requiredMode = REQUIRED, description = "Jobs in the board's default view") long toReview,
            @Schema(requiredMode = REQUIRED, description = "Enabled sources") int sourceCount,
            @Schema(requiredMode = REQUIRED, description = "Enabled company boards") int companyBoardCount) {
    }
}
