package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.llm.LlmAlert;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * A purpose whose model needs attention (api-spec {@code LlmAlert}, ADR-0008): its model is not
 * AVAILABLE, or auto-fallback switched it and the previous model works again
 * ({@code switchedFromModelId}, status AVAILABLE).
 */
public record LlmAlertResponse(
        @Schema(requiredMode = REQUIRED) LlmPurpose purpose,
        @Schema(requiredMode = REQUIRED) String modelId,
        @Schema(requiredMode = REQUIRED) String modelLabel,
        @Schema(requiredMode = REQUIRED) ModelStatus status,
        @Schema(requiredMode = REQUIRED, nullable = true) String detail,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant until,
        @Schema(requiredMode = REQUIRED, description = "Analyze/Score for this purpose answer LLM_PAUSED")
        boolean paused,
        @Schema(requiredMode = REQUIRED, nullable = true,
                description = "Set when auto-fallback switched away from this model, which is usable again")
        String switchedFromModelId,
        @Schema(requiredMode = REQUIRED) List<Suggestion> suggestions) {

    public LlmAlertResponse {
        suggestions = List.copyOf(suggestions);
    }

    static LlmAlertResponse of(LlmAlert alert) {
        return new LlmAlertResponse(alert.purpose(), alert.modelId(), alert.modelLabel(), alert.status(),
                alert.detail(), alert.until(), alert.paused(), alert.switchedFrom(),
                alert.suggestions().stream()
                        .map(s -> new Suggestion(s.modelId(), s.label(), s.tier(), s.status()))
                        .toList());
    }

    /** A usable model the owner may switch to. */
    @Schema(name = "LlmModelSuggestion")
    public record Suggestion(
            @Schema(requiredMode = REQUIRED) String modelId,
            @Schema(requiredMode = REQUIRED) String label,
            @Schema(requiredMode = REQUIRED) ModelTier tier,
            @Schema(requiredMode = REQUIRED) ModelStatus status) {
    }
}
