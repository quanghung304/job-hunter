package io.github.quanghung304.jobhunter.llm.client;

import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.LlmProperties;
import io.github.quanghung304.jobhunter.llm.registry.ProviderType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Offline client for tests and {@code app.llm.force-model=fake} (testing.md "FakeLlmClient").
 *
 * <p>Answer per call, first match wins:
 * <ol>
 *   <li>the next scripted step ({@link #enqueue}) — text, an HTTP error or a 429;
 *   <li>{@code app.llm.fake.simulate=quota-after:<n>}: after n answered calls, a 429 daily quota;
 *   <li>{@code classpath:llm-fakes/<prompt>/job-<jobId>.json}, where {@code <prompt>} is the prompt id
 *       without its version ({@code score-v1} → {@code score});
 *   <li>{@code classpath:llm-fakes/<prompt>/default.json} (valid per scoring-spec §3/§5).
 * </ol>
 * Token usage is estimated as characters / 4. Thread-safe.
 */
@Component
public class FakeLlmClient implements LlmClient {

    /** The 429 body the fake sends for an exhausted daily quota (Groq wording). */
    public static final String DAILY_QUOTA_BODY = """
            {"error":{"message":"Rate limit reached for model `fake` on requests per day (RPD): \
            Limit 1000, Used 1000, Requested 1.","type":"requests","code":"rate_limit_exceeded"}}""";

    private static final Pattern QUOTA_AFTER = Pattern.compile("quota-after:(\\d+)");

    private final Integer quotaAfter;
    private final Deque<Step> script = new ArrayDeque<>();
    private final List<LlmRequest> requests = new ArrayList<>();
    private int answered;

    @Autowired
    public FakeLlmClient(LlmProperties properties) {
        this(properties.fake() == null ? null : properties.fake().simulate());
    }

    private FakeLlmClient(String simulate) {
        this.quotaAfter = parseSimulate(simulate);
    }

    /** A fake outside Spring; {@code simulate} as {@code app.llm.fake.simulate}, may be null. */
    public static FakeLlmClient simulating(String simulate) {
        return new FakeLlmClient(simulate);
    }

    @Override
    public ProviderType type() {
        return ProviderType.FAKE;
    }

    @Override
    public synchronized LlmResponse send(LlmRequest request) {
        requests.add(request);
        Step step = script.pollFirst();
        if (step != null) {
            return step.apply(request, this);
        }
        if (quotaAfter != null && answered >= quotaAfter) {
            throw quotaExhausted(null);
        }
        return answer(request, cannedAnswer(request));
    }

    /** Adds steps answered before any canned JSON. */
    public synchronized FakeLlmClient enqueue(Step... steps) {
        script.addAll(List.of(steps));
        return this;
    }

    /** Requests received so far, oldest first. */
    public synchronized List<LlmRequest> requests() {
        return List.copyOf(requests);
    }

    /** Clears the script, the request log and the answered-call counter. */
    public synchronized void reset() {
        script.clear();
        requests.clear();
        answered = 0;
    }

    private LlmResponse answer(LlmRequest request, String text) {
        answered++;
        int input = (request.system().length() + request.messages().stream().mapToInt(m -> m.content().length()).sum()) / 4;
        return new LlmResponse(text, input, text.length() / 4, 0, 200, RateLimitInfo.NONE);
    }

    static String cannedAnswer(LlmRequest request) {
        String folder = "llm-fakes/" + promptFolder(request.promptId()) + "/";
        if (request.jobId() != null) {
            String perJob = resource(folder + "job-" + request.jobId() + ".json");
            if (perJob != null) {
                return perJob;
            }
        }
        String fallback = resource(folder + "default.json");
        return fallback != null ? fallback : "{}";
    }

    /** {@code score-v1} → {@code score}. */
    private static String promptFolder(String promptId) {
        int version = promptId.lastIndexOf("-v");
        return version > 0 ? promptId.substring(0, version) : promptId;
    }

    private static String resource(String path) {
        try (InputStream in = FakeLlmClient.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Integer parseSimulate(String simulate) {
        if (simulate == null || simulate.isBlank()) {
            return null;
        }
        Matcher matcher = QUOTA_AFTER.matcher(simulate.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("app.llm.fake.simulate must be quota-after:<n>, was: " + simulate);
        }
        return Integer.valueOf(matcher.group(1));
    }

    private static LlmCallException quotaExhausted(Duration retryAfter) {
        return new LlmCallException(LlmErrorKind.QUOTA_EXHAUSTED, 429, "HTTP 429: " + DAILY_QUOTA_BODY, retryAfter,
                RateLimitInfo.NONE, 0, null);
    }

    /** One scripted answer. */
    @FunctionalInterface
    public interface Step {

        LlmResponse apply(LlmRequest request, FakeLlmClient client);

        /** Answers with this text (counted as an answered call). */
        static Step text(String text) {
            return (request, client) -> client.answer(request, text);
        }

        /** Answers with the canned JSON for the request (counted as an answered call). */
        static Step canned() {
            return (request, client) -> client.answer(request, cannedAnswer(request));
        }

        /** Fails like a provider HTTP error, classified as the real clients do. */
        static Step httpError(int status, String body, Duration retryAfter) {
            return (request, client) -> {
                throw new LlmCallException(ProviderErrors.classify(status, body, retryAfter), status,
                        "HTTP " + status + ": " + ProviderErrors.shorten(body), retryAfter, RateLimitInfo.NONE, 0, null);
            };
        }

        /** 429 per-minute limit with this {@code retry-after}. */
        static Step rateLimited(Duration retryAfter) {
            return httpError(429, "{\"error\":{\"message\":\"Rate limit reached on requests per minute (RPM)\"}}",
                    retryAfter);
        }

        /** 429 daily quota. */
        static Step dailyQuota(Duration retryAfter) {
            return (request, client) -> {
                throw quotaExhausted(retryAfter);
            };
        }

        /** A timeout without HTTP status. */
        static Step timeout() {
            return (request, client) -> {
                throw new LlmCallException(LlmErrorKind.TIMEOUT, null, "timeout: fake", null, null, 0, null);
            };
        }
    }
}
