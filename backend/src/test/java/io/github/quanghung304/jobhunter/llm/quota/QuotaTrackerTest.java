package io.github.quanghung304.jobhunter.llm.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.LlmModelStatusChanged;
import io.github.quanghung304.jobhunter.llm.LlmTestModels;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.github.quanghung304.jobhunter.llm.client.LlmCallException;
import io.github.quanghung304.jobhunter.llm.client.RateLimitInfo;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmModelState;
import io.github.quanghung304.jobhunter.llm.persistence.LlmModelStateRepository;
import io.github.quanghung304.jobhunter.llm.registry.LlmRegistryFile;
import io.github.quanghung304.jobhunter.llm.registry.ModelRegistry;
import io.github.quanghung304.jobhunter.llm.registry.ProviderType;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import jakarta.validation.Validation;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Status rules of data-model.md {@code llm_model_state}, with a movable clock. */
class QuotaTrackerTest {

    static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    static final Instant NEXT_UTC_MIDNIGHT = Instant.parse("2026-10-18T00:00:00Z");
    static final Instant NEXT_MONTH = Instant.parse("2026-11-01T00:00:00Z");
    static final RegisteredModel PLAIN = LlmTestModels.withLimits("plain", ModelTier.FREE, LlmTestModels.NO_LIMITS);
    static final RegisteredModel DAILY = LlmTestModels.withLimits("daily", ModelTier.FREE,
            new LlmRegistryFile.Limits(30, 1000, null, 100_000, null));
    static final RegisteredModel MONTHLY = LlmTestModels.withLimits("monthly", ModelTier.FREE,
            new LlmRegistryFile.Limits(null, null, null, null, 500));

    final MutableClock clock = new MutableClock(NOW);
    final Map<String, LlmModelState> rows = new LinkedHashMap<>();
    final List<LlmModelStatusChanged> events = new ArrayList<>();
    final LlmModelStateRepository states = mock(LlmModelStateRepository.class);
    final LlmCallRepository calls = mock(LlmCallRepository.class);
    QuotaTracker tracker;

    @BeforeEach
    void setUp() {
        given(states.findById(anyString())).willAnswer(inv -> Optional.ofNullable(rows.get(inv.<String>getArgument(0))));
        given(states.save(any())).willAnswer(inv -> {
            LlmModelState state = inv.getArgument(0);
            rows.put(state.getModelId(), state);
            return state;
        });
        given(states.findAll()).willAnswer(inv -> List.copyOf(rows.values()));
        ModelRegistry registry = ModelRegistry.load(Path.of("..", "config", "llm.yaml"),
                Validation.buildDefaultValidatorFactory().getValidator(), name -> null);
        tracker = new QuotaTracker(states, calls, registry, event -> events.add((LlmModelStatusChanged) event), clock);
    }

    @Test
    void unknownModelIsAvailable() {
        assertThat(tracker.status(PLAIN)).isEqualTo(new ModelState("plain", ModelStatus.AVAILABLE, null, null));
    }

    @Test
    void rateLimitWithRetryAfterUpToOneHour() {
        tracker.onFailure(PLAIN, error(LlmErrorKind.RATE_LIMIT, 429, Duration.ofSeconds(30)));

        assertState(PLAIN, ModelStatus.RATE_LIMITED, NOW.plusSeconds(30));
        assertThat(events).containsExactly(new LlmModelStatusChanged("plain", null, ModelStatus.RATE_LIMITED,
                "HTTP 429: limit", NOW.plusSeconds(30)));
    }

    @Test
    void rateLimitWithoutRetryAfterWaitsOneMinute() {
        tracker.onFailure(PLAIN, error(LlmErrorKind.RATE_LIMIT, 429, null));

        assertState(PLAIN, ModelStatus.RATE_LIMITED, NOW.plusSeconds(60));
    }

    @Test
    void rateLimitWithResetOverOneHourIsExhausted() {
        tracker.onFailure(PLAIN, error(LlmErrorKind.RATE_LIMIT, 429, Duration.ofHours(2)));

        assertState(PLAIN, ModelStatus.EXHAUSTED, NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void dailyQuotaUsesRetryAfterOrNextUtcMidnight() {
        tracker.onFailure(PLAIN, error(LlmErrorKind.QUOTA_EXHAUSTED, 429, Duration.ofSeconds(87)));
        assertState(PLAIN, ModelStatus.EXHAUSTED, NOW.plusSeconds(87));

        tracker.onFailure(DAILY, error(LlmErrorKind.QUOTA_EXHAUSTED, 429, null));
        assertState(DAILY, ModelStatus.EXHAUSTED, NEXT_UTC_MIDNIGHT);
    }

    @Test
    void monthlyQuotaWithoutRetryAfterLastsUntilMonthStart() {
        tracker.onFailure(PLAIN, new LlmCallException(LlmErrorKind.QUOTA_EXHAUSTED, 429,
                "HTTP 429: monthly request quota used", null, null, 0, null));

        assertState(PLAIN, ModelStatus.EXHAUSTED, NEXT_MONTH);
    }

    @Test
    void authAndModelNotFoundAreErrorsWithoutUntil() {
        tracker.onFailure(PLAIN, error(LlmErrorKind.AUTH, 401, null));
        tracker.onFailure(DAILY, error(LlmErrorKind.MODEL_NOT_FOUND, 404, null));
        clock.set(NOW.plus(Duration.ofDays(3)));

        assertState(PLAIN, ModelStatus.ERROR, null);
        assertState(DAILY, ModelStatus.ERROR, null);
    }

    @Test
    void transientErrorsDoNotChangeTheStatus() {
        tracker.onFailure(PLAIN, error(LlmErrorKind.TIMEOUT, null, null));
        tracker.onFailure(PLAIN, error(LlmErrorKind.SERVER, 503, null));
        tracker.onFailure(PLAIN, error(LlmErrorKind.INVALID_OUTPUT, 200, null));
        tracker.onFailure(PLAIN, error(LlmErrorKind.BAD_REQUEST, 400, null));

        assertThat(rows).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void ownCountsDriveNearLimitAndExhausted() {
        counts(DAILY, 799, 0);
        tracker.onSuccess(DAILY, RateLimitInfo.NONE);
        assertState(DAILY, ModelStatus.AVAILABLE, null);

        counts(DAILY, 800, 0);
        tracker.onSuccess(DAILY, RateLimitInfo.NONE);
        assertState(DAILY, ModelStatus.NEAR_LIMIT, NEXT_UTC_MIDNIGHT);
        assertThat(tracker.status(DAILY).detail()).isEqualTo("Daily request limit 800/1000 (80%)");

        counts(DAILY, 1000, 0);
        tracker.onSuccess(DAILY, RateLimitInfo.NONE);
        assertState(DAILY, ModelStatus.EXHAUSTED, NEXT_UTC_MIDNIGHT);
        assertThat(tracker.status(DAILY).detail()).isEqualTo("Daily request limit 1000/1000");

        assertThat(events).extracting(LlmModelStatusChanged::to)
                .containsExactly(ModelStatus.NEAR_LIMIT, ModelStatus.EXHAUSTED);
    }

    @Test
    void tokensPerDayCountsToo() {
        counts(DAILY, 10, 85_000);
        tracker.onSuccess(DAILY, RateLimitInfo.NONE);

        assertState(DAILY, ModelStatus.NEAR_LIMIT, NEXT_UTC_MIDNIGHT);
        assertThat(tracker.status(DAILY).detail()).startsWith("Daily token limit 85000/100000");
    }

    @Test
    void monthlyRequestLimitLastsUntilNextMonth() {
        given(calls.countByModelIdAndCreatedAtGreaterThanEqual(eq("monthly"), any())).willReturn(500L);

        tracker.onSuccess(MONTHLY, RateLimitInfo.NONE);

        assertState(MONTHLY, ModelStatus.EXHAUSTED, NEXT_MONTH);
    }

    @Test
    void midnightResetMakesTheModelAvailableAgain() {
        counts(DAILY, 1000, 0);
        tracker.onSuccess(DAILY, RateLimitInfo.NONE);

        clock.set(NEXT_UTC_MIDNIGHT.minusSeconds(1));
        assertThat(tracker.status(DAILY).status()).isEqualTo(ModelStatus.EXHAUSTED);
        clock.set(NEXT_UTC_MIDNIGHT);
        assertThat(tracker.status(DAILY).status()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(events).extracting(LlmModelStatusChanged::to)
                .containsExactly(ModelStatus.EXHAUSTED, ModelStatus.AVAILABLE);
    }

    @Test
    void nearLimitAlsoClearsAtMidnight() {
        counts(DAILY, 900, 0);
        tracker.onSuccess(DAILY, RateLimitInfo.NONE);
        clock.set(NEXT_UTC_MIDNIGHT.plusSeconds(5));

        assertThat(tracker.status(DAILY).status()).isEqualTo(ModelStatus.AVAILABLE);
    }

    @Test
    void resetZoneMovesTheDailyReset() {
        RegisteredModel pacific = new RegisteredModel("pacific", "p", "fake", ProviderType.FAKE, null, "p",
                ModelTier.FREE, new LlmRegistryFile.Limits(null, 10, null, null, null), null, false, "", false,
                ZoneId.of("America/Los_Angeles"), 0);
        given(calls.countByModelIdAndCreatedAtGreaterThanEqual(eq("pacific"), any())).willReturn(10L);

        tracker.onSuccess(pacific, RateLimitInfo.NONE);

        assertState(pacific, ModelStatus.EXHAUSTED, Instant.parse("2026-10-18T07:00:00Z"));
    }

    @Test
    void successAfterRateLimitIsAvailable() {
        tracker.onFailure(PLAIN, error(LlmErrorKind.RATE_LIMIT, 429, Duration.ofSeconds(10)));
        tracker.onSuccess(PLAIN, new RateLimitInfo(55, 9000, null, null));

        assertState(PLAIN, ModelStatus.AVAILABLE, null);
        assertThat(rows.get("plain").getRemainingRequests()).isEqualTo(55);
        assertThat(rows.get("plain").getRemainingTokens()).isEqualTo(9000);
    }

    @Test
    void providerHeadersWithNoRequestsLeft() {
        tracker.onSuccess(PLAIN, new RateLimitInfo(0, 100, Duration.ofSeconds(40), null));
        assertState(PLAIN, ModelStatus.RATE_LIMITED, NOW.plusSeconds(40));

        tracker.onSuccess(PLAIN, new RateLimitInfo(0, 100, Duration.ofHours(5), null));
        assertState(PLAIN, ModelStatus.EXHAUSTED, NOW.plus(Duration.ofHours(5)));
    }

    @Test
    void noEventWhenTheStatusStaysTheSame() {
        tracker.onSuccess(PLAIN, RateLimitInfo.NONE);
        tracker.onSuccess(PLAIN, RateLimitInfo.NONE);

        assertThat(events).isEmpty();
    }

    @Test
    void initializeClearsOldErrorsAndMarksModelsWithoutKey() {
        tracker.onFailure(PLAIN, error(LlmErrorKind.AUTH, 401, null));
        events.clear();

        tracker.initialize();

        assertState(PLAIN, ModelStatus.AVAILABLE, null);
        assertThat(rows.get("groq-gpt-oss-120b").getStatus()).isEqualTo(ModelStatus.ERROR);
        assertThat(rows.get("groq-gpt-oss-120b").getDetail()).startsWith("missing key");
        assertThat(rows.get("claude-haiku").getStatus()).isEqualTo(ModelStatus.ERROR);
        assertThat(rows).doesNotContainKey("ollama-local").doesNotContainKey("fake");
    }

    private void counts(RegisteredModel model, long requests, long tokens) {
        given(calls.countByModelIdAndCreatedAtGreaterThanEqual(eq(model.id()), any())).willReturn(requests);
        given(calls.sumTokens(eq(model.id()), any())).willReturn(tokens);
    }

    private void assertState(RegisteredModel model, ModelStatus status, Instant until) {
        ModelState state = tracker.status(model);
        assertThat(state.status()).as("status of %s", model.id()).isEqualTo(status);
        assertThat(state.until()).as("until of %s", model.id()).isEqualTo(until);
    }

    private static LlmCallException error(LlmErrorKind kind, Integer status, Duration retryAfter) {
        return new LlmCallException(kind, status, "HTTP " + status + ": limit", retryAfter, null, 0, null);
    }
}
