package io.github.quanghung304.jobhunter.llm.client;

import java.time.Duration;

/**
 * Rate-limit values a provider reported in response headers; every field may be null.
 *
 * @param resetRequests time until the request window resets (Groq: the daily window)
 * @param resetTokens   time until the token window resets
 */
public record RateLimitInfo(
        Integer remainingRequests,
        Integer remainingTokens,
        Duration resetRequests,
        Duration resetTokens) {

    public static final RateLimitInfo NONE = new RateLimitInfo(null, null, null, null);
}
