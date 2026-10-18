package io.github.quanghung304.jobhunter.llm.client;

import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared parsing of provider rate-limit headers and error classification
 * (header names and formats: comments in config/llm.yaml).
 */
public final class ProviderErrors {

    /** A reset longer than this means a daily/monthly quota, not a per-minute limit (data-model). */
    public static final Duration EXHAUSTED_AFTER = Duration.ofHours(1);

    /**
     * Words providers use for daily/monthly quotas in 429 bodies, e.g. Groq "on requests per day
     * (RPD)", OpenRouter "free-models-per-day".
     */
    private static final Pattern DAILY_QUOTA = Pattern.compile(
            "per[ -]day|per[ -]month|\\b(rpd|tpd)\\b|daily|monthly|quota", Pattern.CASE_INSENSITIVE);
    /** Groq style durations: "2m59.56s", "7.66s", "1h2m", "350ms". */
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|h|m|s)");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private ProviderErrors() {
    }

    /** Maps an HTTP status and body to an error kind; 2xx is not an error. */
    public static LlmErrorKind classify(int status, String body, Duration retryAfter) {
        if (status == 429) {
            boolean longReset = retryAfter != null && retryAfter.compareTo(EXHAUSTED_AFTER) > 0;
            return longReset || isDailyQuota(body) ? LlmErrorKind.QUOTA_EXHAUSTED : LlmErrorKind.RATE_LIMIT;
        }
        if (status == 401 || status == 403) {
            return LlmErrorKind.AUTH;
        }
        if (status == 404) {
            return LlmErrorKind.MODEL_NOT_FOUND;
        }
        if (status == 408) {
            return LlmErrorKind.TIMEOUT;
        }
        if (status >= 500) {
            return LlmErrorKind.SERVER;
        }
        // Groq answers 400 json_validate_failed when JSON mode output is not valid JSON
        if (status == 400 && body != null && body.contains("json_validate_failed")) {
            return LlmErrorKind.INVALID_OUTPUT;
        }
        return LlmErrorKind.BAD_REQUEST;
    }

    public static boolean isDailyQuota(String body) {
        return body != null && DAILY_QUOTA.matcher(body).find();
    }

    /**
     * {@code retry-after}: seconds ("17", "1.5") or an HTTP date. Null if absent or unreadable.
     */
    public static Duration retryAfter(String value, Clock clock) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (NUMBER.matcher(trimmed).matches()) {
            return seconds(trimmed);
        }
        try {
            Instant at = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration wait = Duration.between(clock.instant(), at);
            return wait.isNegative() ? Duration.ZERO : wait;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * A reset header: a duration ("2m59.56s", "7.66s", plain seconds) or an RFC 3339 timestamp
     * (Anthropic). Null if absent or unreadable.
     */
    public static Duration reset(String value, Clock clock) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (NUMBER.matcher(trimmed).matches()) {
            return seconds(trimmed);
        }
        Matcher matcher = DURATION_PART.matcher(trimmed);
        Duration total = Duration.ZERO;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                break;
            }
            BigDecimal amount = new BigDecimal(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "h" -> millis(amount.multiply(BigDecimal.valueOf(3_600_000)));
                case "m" -> millis(amount.multiply(BigDecimal.valueOf(60_000)));
                case "s" -> millis(amount.multiply(BigDecimal.valueOf(1_000)));
                default -> millis(amount);
            });
            end = matcher.end();
        }
        if (end == trimmed.length() && end > 0) {
            return total;
        }
        try {
            Duration wait = Duration.between(clock.instant(), Instant.parse(trimmed));
            return wait.isNegative() ? Duration.ZERO : wait;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Integer integer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Rate-limit info from headers named {@code <prefix>remaining-requests} style (OpenAI) or
     * {@code <prefix>requests-remaining} style (Anthropic); {@code header} returns null if absent.
     */
    public static RateLimitInfo openAiStyle(Function<String, String> header, Clock clock) {
        return new RateLimitInfo(
                integer(header.apply("x-ratelimit-remaining-requests")),
                integer(header.apply("x-ratelimit-remaining-tokens")),
                reset(header.apply("x-ratelimit-reset-requests"), clock),
                reset(header.apply("x-ratelimit-reset-tokens"), clock));
    }

    public static RateLimitInfo anthropicStyle(Function<String, String> header, Clock clock) {
        return new RateLimitInfo(
                integer(header.apply("anthropic-ratelimit-requests-remaining")),
                integer(header.apply("anthropic-ratelimit-tokens-remaining")),
                reset(header.apply("anthropic-ratelimit-requests-reset"), clock),
                reset(header.apply("anthropic-ratelimit-tokens-reset"), clock));
    }

    /** Truncates provider error text for logs and {@code llm_call.error}. */
    public static String shorten(String text) {
        if (text == null) {
            return null;
        }
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 2000 ? oneLine : oneLine.substring(0, 2000);
    }

    private static Duration seconds(String value) {
        return millis(new BigDecimal(value).multiply(BigDecimal.valueOf(1_000)));
    }

    private static Duration millis(BigDecimal value) {
        return Duration.ofMillis(value.setScale(0, java.math.RoundingMode.CEILING).longValueExact());
    }
}
