package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * A purpose whose assigned model is not AVAILABLE (api-spec {@code LlmAlert}, ADR-0008). The
 * lists that carry it are always empty until T-022 fills them.
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
        @Schema(requiredMode = REQUIRED) List<Suggestion> suggestions) {

    public LlmAlertResponse {
        suggestions = List.copyOf(suggestions);
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
