package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.pipeline.PipelineReport;
import io.github.quanghung304.jobhunter.pipeline.SourceReport;
import io.github.quanghung304.jobhunter.pipeline.Trigger;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** One pipeline run (kept in memory, last 20). The run makes no LLM calls (ADR-0010). */
public record PipelineReportResponse(
        @Schema(requiredMode = REQUIRED) long runNumber,
        @Schema(requiredMode = REQUIRED) Trigger trigger,
        @Schema(requiredMode = REQUIRED) CollectStatus status,
        @Schema(requiredMode = REQUIRED) Instant startedAt,
        @Schema(requiredMode = REQUIRED) Instant finishedAt,
        @Schema(requiredMode = REQUIRED) long durationMs,
        @Schema(requiredMode = REQUIRED) List<SourceRun> sources,
        @Schema(requiredMode = REQUIRED) int newJobs,
        @Schema(requiredMode = REQUIRED, description = "Rows re-evaluated after a profile change") int reassessed,
        @Schema(requiredMode = REQUIRED) int prefilterEvaluated,
        @Schema(requiredMode = REQUIRED) int prefilterPassed,
        @Schema(requiredMode = REQUIRED) int prefilterRejected,
        @Schema(requiredMode = REQUIRED, description = "ACTIVE prefilter-PASS jobs waiting for Analyze")
        int notAnalyzed,
        @Schema(requiredMode = REQUIRED) int expired,
        @Schema(requiredMode = REQUIRED) long llmCalls,
        @Schema(requiredMode = REQUIRED, nullable = true) String error) {

    public PipelineReportResponse {
        sources = List.copyOf(sources);
    }

    /** One collector in the run; {@code status} is null when it was skipped (min-interval). */
    @Schema(name = "PipelineSourceRun")
    public record SourceRun(
            @Schema(requiredMode = REQUIRED) String sourceCode,
            @Schema(requiredMode = REQUIRED, nullable = true) CollectStatus status,
            @Schema(requiredMode = REQUIRED) int fetched,
            @Schema(requiredMode = REQUIRED) int newRaw,
            @Schema(requiredMode = REQUIRED) int newJobs,
            @Schema(requiredMode = REQUIRED) int updatedJobs,
            @Schema(requiredMode = REQUIRED, nullable = true, description = "Skip reason or error") String message) {

        static SourceRun of(SourceReport report) {
            return new SourceRun(report.sourceCode(), report.status(), report.fetched(), report.newRaw(),
                    report.newJobs(), report.updatedJobs(), report.message());
        }
    }

    static PipelineReportResponse of(PipelineReport report) {
        return new PipelineReportResponse(report.runNumber(), report.trigger(), report.status(), report.startedAt(),
                report.finishedAt(), report.duration().toMillis(),
                report.sources().stream().map(SourceRun::of).toList(), report.newJobs(), report.reassessed(),
                report.prefilterEvaluated(), report.prefilterPassed(), report.prefilterRejected(),
                report.notAnalyzed(), report.expired(), report.llmCalls(), report.error());
    }
}
