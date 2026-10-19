package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.tracking.EligibilityDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/jobs/{id}/eligibility}; {@code decision} null clears it (undecided). */
public record EligibilityDecisionRequest(
        @Schema(nullable = true, description = "null = undecided") EligibilityDecision decision,
        @Schema(nullable = true, maxLength = StatusChangeRequest.MAX_NOTE) @Size(max = StatusChangeRequest.MAX_NOTE)
        String note) {
}
