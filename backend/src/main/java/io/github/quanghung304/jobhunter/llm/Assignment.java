package io.github.quanghung304.jobhunter.llm;

import java.util.List;

/** Which model serves a purpose, with its ordered fallbacks (data-model {@code llm_assignment}). */
public record Assignment(
        LlmPurpose purpose,
        String modelId,
        List<String> fallbacks,
        boolean autoFallback,
        boolean allowPaid,
        String updatedBy) {

    public Assignment {
        fallbacks = List.copyOf(fallbacks);
    }
}
