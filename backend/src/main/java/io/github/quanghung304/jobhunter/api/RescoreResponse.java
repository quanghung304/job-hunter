package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.scoring.RescorePlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Dry run of {@code POST /api/admin/rescore}. */
public record RescoreResponse(
        @Schema(requiredMode = REQUIRED) int jobCount,
        @Schema(requiredMode = REQUIRED) int estimatedCalls,
        @Schema(requiredMode = REQUIRED, nullable = true,
                description = "0 for FREE/LOCAL; for PAID the 30-day average cost × calls, null without history")
        BigDecimal estimatedCostUsd,
        @Schema(requiredMode = REQUIRED) ModelChoiceResponse model) {

    static RescoreResponse of(RescorePlan plan) {
        return new RescoreResponse(plan.jobCount(), plan.estimatedCalls(), plan.estimatedCostUsd(),
                ModelChoiceResponse.of(plan.model()));
    }
}
