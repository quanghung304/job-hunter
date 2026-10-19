package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/admin/jobs/{id}/retry}. */
public record RetryRequest(@Schema(requiredMode = REQUIRED) @NotNull Purpose purpose) {

    /** The LLM work to retry. */
    public enum Purpose {
        EXTRACT,
        SCORE;

        LlmPurpose llmPurpose() {
            return this == EXTRACT ? LlmPurpose.EXTRACT : LlmPurpose.SCORE;
        }
    }
}
