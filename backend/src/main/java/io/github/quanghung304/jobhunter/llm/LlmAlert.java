package io.github.quanghung304.jobhunter.llm;

import java.time.Instant;
import java.util.List;

/**
 * A purpose whose model needs the owner's attention (api-spec {@code LlmAlert}, ADR-0008): its
 * model is not AVAILABLE, or it was switched by auto-fallback and the previous model is usable
 * again ({@code switchedFrom} set, status AVAILABLE).
 *
 * @param paused      Analyze/Score of this purpose are refused (no usable model, ADR-0010)
 * @param suggestions usable models to switch to: the assignment's fallbacks first, then other
 *                    registry models, FREE/LOCAL before PAID
 */
public record LlmAlert(
        LlmPurpose purpose,
        String modelId,
        String modelLabel,
        ModelStatus status,
        String detail,
        Instant until,
        boolean paused,
        String switchedFrom,
        List<Suggestion> suggestions) {

    public LlmAlert {
        suggestions = List.copyOf(suggestions);
    }

    public record Suggestion(String modelId, String label, ModelTier tier, ModelStatus status) {
    }
}
