package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.llm.ModelChoice;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.swagger.v3.oas.annotations.media.Schema;

/** The model a bulk action would use; a PAID one must be named in the confirmation (ADR-0009). */
public record ModelChoiceResponse(
        @Schema(requiredMode = REQUIRED) String modelId,
        @Schema(requiredMode = REQUIRED) String label,
        @Schema(requiredMode = REQUIRED) ModelTier tier,
        @Schema(requiredMode = REQUIRED) boolean paidCallsAllowed) {

    static ModelChoiceResponse of(ModelChoice model) {
        return new ModelChoiceResponse(model.modelId(), model.label(), model.tier(), model.paidCallsAllowed());
    }
}
