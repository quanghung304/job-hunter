package io.github.quanghung304.jobhunter.api;

import static io.github.quanghung304.jobhunter.api.BulkAnalyzeResponse.count;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.scoring.BulkScoreReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;

/** Dry run of {@code POST /api/admin/jobs/score}. */
public record BulkScoreResponse(
        @Schema(requiredMode = REQUIRED) int selected,
        @Schema(requiredMode = REQUIRED) int willScore,
        @Schema(requiredMode = REQUIRED) Skipped skipped,
        @Schema(requiredMode = REQUIRED, description = "One SCORE call per job to score") int estimatedCalls,
        @Schema(requiredMode = REQUIRED, nullable = true,
                description = "0 for FREE/LOCAL; null for a PAID model without cost history") BigDecimal estimatedCostUsd,
        @Schema(requiredMode = REQUIRED) ModelChoiceResponse model) {

    /** Jobs that make no call, by reason. */
    @Schema(name = "BulkScoreSkipped")
    public record Skipped(
            @Schema(requiredMode = REQUIRED) int notAnalyzed,
            @Schema(requiredMode = REQUIRED) int ineligible,
            @Schema(requiredMode = REQUIRED) int alreadyScored,
            @Schema(requiredMode = REQUIRED) int expired,
            @Schema(requiredMode = REQUIRED) int failedPermanently,
            @Schema(requiredMode = REQUIRED) int notFound) {
    }

    static BulkScoreResponse of(BulkScoreReport report) {
        Map<String, Integer> s = report.skipped();
        return new BulkScoreResponse(report.selected(), report.willScore(),
                new Skipped(count(s, "notAnalyzed"), count(s, "ineligible"), count(s, "alreadyScored"),
                        count(s, "expired"), count(s, "failedPermanently"), count(s, "notFound")),
                report.estimatedCalls(), report.estimatedCostUsd(), ModelChoiceResponse.of(report.model()));
    }
}
