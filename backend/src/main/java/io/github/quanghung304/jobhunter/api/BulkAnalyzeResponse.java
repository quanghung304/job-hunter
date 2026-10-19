package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.enrich.BulkAnalysisReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;

/** Dry run of {@code POST /api/admin/jobs/analyze}. */
public record BulkAnalyzeResponse(
        @Schema(requiredMode = REQUIRED) int selected,
        @Schema(requiredMode = REQUIRED) int willAnalyze,
        @Schema(requiredMode = REQUIRED) Skipped skipped,
        @Schema(requiredMode = REQUIRED, description = "One EXTRACT call per job to analyze") int estimatedCalls,
        @Schema(requiredMode = REQUIRED, nullable = true,
                description = "0 for FREE/LOCAL; null for a PAID model without cost history") BigDecimal estimatedCostUsd,
        @Schema(requiredMode = REQUIRED) ModelChoiceResponse model) {

    /** Jobs that make no call, by reason. */
    @Schema(name = "BulkAnalyzeSkipped")
    public record Skipped(
            @Schema(requiredMode = REQUIRED) int alreadyAnalyzed,
            @Schema(requiredMode = REQUIRED) int expired,
            @Schema(requiredMode = REQUIRED) int prefilterRejected,
            @Schema(requiredMode = REQUIRED) int failedPermanently,
            @Schema(requiredMode = REQUIRED) int notFound) {
    }

    static BulkAnalyzeResponse of(BulkAnalysisReport report) {
        Map<String, Integer> s = report.skipped();
        return new BulkAnalyzeResponse(report.selected(), report.willAnalyze(),
                new Skipped(count(s, "alreadyAnalyzed"), count(s, "expired"), count(s, "prefilterRejected"),
                        count(s, "failedPermanently"), count(s, "notFound")),
                report.estimatedCalls(), report.estimatedCostUsd(), ModelChoiceResponse.of(report.model()));
    }

    static int count(Map<String, Integer> skipped, String reason) {
        return skipped.getOrDefault(reason, 0);
    }
}
