package io.github.quanghung304.jobhunter.llm.client;

import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import java.time.Duration;

/**
 * A provider HTTP attempt failed. Thrown by {@link LlmClient#send}; handled by the router
 * (retry, quota status, fallback) and never leaves the {@code llm} module.
 *
 * @param httpStatus null for network errors and timeouts
 * @param retryAfter from {@code retry-after} or reset headers; null if unknown
 */
public class LlmCallException extends RuntimeException {

    private final LlmErrorKind kind;
    private final Integer httpStatus;
    private final Duration retryAfter;
    private final RateLimitInfo rateLimit;
    private final long latencyMs;

    public LlmCallException(LlmErrorKind kind, Integer httpStatus, String message, Duration retryAfter,
            RateLimitInfo rateLimit, long latencyMs, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
        this.rateLimit = rateLimit == null ? RateLimitInfo.NONE : rateLimit;
        this.latencyMs = latencyMs;
    }

    public LlmErrorKind kind() {
        return kind;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public RateLimitInfo rateLimit() {
        return rateLimit;
    }

    public long latencyMs() {
        return latencyMs;
    }
}
