package io.github.quanghung304.jobhunter.llm;

import java.time.Instant;

/**
 * Published on every model status transition (ADR-0008); notify (T-022) turns some of them
 * into alerts. {@code from} is null for a model seen for the first time; {@code until} may be null.
 */
public record LlmModelStatusChanged(String modelId, ModelStatus from, ModelStatus to, String detail, Instant until) {
}
