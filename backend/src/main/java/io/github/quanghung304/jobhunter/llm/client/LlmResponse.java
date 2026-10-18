package io.github.quanghung304.jobhunter.llm.client;

/** A successful (HTTP 2xx) provider answer. */
public record LlmResponse(
        String text,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        int httpStatus,
        RateLimitInfo rateLimit) {
}
