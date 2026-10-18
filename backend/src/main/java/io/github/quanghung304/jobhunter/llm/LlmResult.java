package io.github.quanghung304.jobhunter.llm;

/**
 * A validated answer.
 *
 * @param modelId    registry id of the model that answered
 * @param httpCalls  HTTP attempts made for this call (retries and the corrective retry included)
 */
public record LlmResult<T>(
        T value,
        String rawText,
        String modelId,
        String providerModel,
        String promptId,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        int httpCalls) implements LlmOutcome<T> {
}
