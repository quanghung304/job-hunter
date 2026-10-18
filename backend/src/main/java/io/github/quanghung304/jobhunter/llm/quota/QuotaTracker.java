package io.github.quanghung304.jobhunter.llm.quota;

import io.github.quanghung304.jobhunter.llm.LlmErrorKind;
import io.github.quanghung304.jobhunter.llm.LlmModelStatusChanged;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.llm.client.LlmCallException;
import io.github.quanghung304.jobhunter.llm.client.ProviderErrors;
import io.github.quanghung304.jobhunter.llm.client.RateLimitInfo;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmModelState;
import io.github.quanghung304.jobhunter.llm.persistence.LlmModelStateRepository;
import io.github.quanghung304.jobhunter.llm.registry.LlmRegistryFile;
import io.github.quanghung304.jobhunter.llm.registry.ModelRegistry;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code llm_model_state} per the status rules in data-model.md ({@code llm_model_state})
 * and publishes {@link LlmModelStatusChanged} on every transition:
 * <ul>
 *   <li>429 with reset ≤ 1 h → RATE_LIMITED until the reset (60 s if unknown);
 *   <li>429 for a daily/monthly quota or reset &gt; 1 h, or our own count reaching a known
 *       {@code rpd}/{@code tokensPerDay}/{@code requestsPerMonth} → EXHAUSTED until the reset or
 *       the next day (month) start in UTC (or the model's {@code resetZone});
 *   <li>count ≥ {@code nearLimitPercent} of a known daily/monthly limit → NEAR_LIMIT until the next day start;
 *   <li>401/403, 404 or a missing key → ERROR (cleared by a successful call or a restart);
 *   <li>back to AVAILABLE when {@code until} passes or a call succeeds below the thresholds.
 * </ul>
 * Counts come from {@code llm_call}, so the call must be logged before {@link #onSuccess}.
 */
@Component
public class QuotaTracker {

    /** Wait assumed for a 429 without {@code retry-after}. */
    static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(60);
    static final int DETAIL_MAX = 200;

    private static final Logger log = LoggerFactory.getLogger(QuotaTracker.class);

    private final LlmModelStateRepository states;
    private final LlmCallRepository calls;
    private final ModelRegistry registry;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public QuotaTracker(LlmModelStateRepository states, LlmCallRepository calls, ModelRegistry registry,
            ApplicationEventPublisher events, Clock clock) {
        this.states = states;
        this.calls = calls;
        this.registry = registry;
        this.events = events;
        this.clock = clock;
    }

    /**
     * On startup: ERROR clears with a restart, then models without a key become ERROR again.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        for (LlmModelState state : states.findAll()) {
            if (state.getStatus() == ModelStatus.ERROR) {
                transition(state.getModelId(), ModelStatus.AVAILABLE, null, "cleared on restart", null);
            }
        }
        registry.all().stream()
                .filter(RegisteredModel::missingKey)
                .forEach(model -> markMissingKey(model));
    }

    /** The current status; RATE_LIMITED/EXHAUSTED/NEAR_LIMIT whose {@code until} passed become AVAILABLE. */
    public ModelState status(RegisteredModel model) {
        Optional<LlmModelState> row = states.findById(model.id());
        if (row.isEmpty()) {
            return new ModelState(model.id(), ModelStatus.AVAILABLE, null, null);
        }
        LlmModelState state = row.get();
        if (state.getStatus() != ModelStatus.ERROR && state.getStatus() != ModelStatus.AVAILABLE
                && state.getUntil() != null && !clock.instant().isBefore(state.getUntil())) {
            state = transition(model.id(), ModelStatus.AVAILABLE, null, "reset at " + state.getUntil(), null);
        }
        return view(state);
    }

    /** All known states, for the admin page (T-022). */
    public List<ModelState> all() {
        List<ModelState> result = new ArrayList<>();
        for (RegisteredModel model : registry.all()) {
            result.add(status(model));
        }
        return List.copyOf(result);
    }

    public void markMissingKey(RegisteredModel model) {
        ModelState current = status(model);
        if (current.status() != ModelStatus.ERROR) {
            transition(model.id(), ModelStatus.ERROR, null, "missing key: environment variable for provider "
                    + model.providerId() + " is empty", null);
        }
    }

    /** After a 2xx answer (already logged in {@code llm_call}). */
    public void onSuccess(RegisteredModel model, RateLimitInfo rateLimit) {
        Instant now = clock.instant();
        Verdict verdict = verdictFromCounts(model, now)
                .or(() -> verdictFromHeaders(rateLimit, now))
                .orElse(new Verdict(ModelStatus.AVAILABLE, null, null));
        transition(model.id(), verdict.status(), verdict.until(), verdict.detail(), rateLimit);
    }

    /** After a failed attempt; only quota and provider-health errors change the status. */
    public void onFailure(RegisteredModel model, LlmCallException error) {
        Instant now = clock.instant();
        Duration retryAfter = error.retryAfter();
        String detail = error.getMessage();
        switch (error.kind()) {
            case RATE_LIMIT -> {
                Duration wait = retryAfter != null ? retryAfter : DEFAULT_RATE_LIMIT_WAIT;
                ModelStatus status = wait.compareTo(ProviderErrors.EXHAUSTED_AFTER) > 0
                        ? ModelStatus.EXHAUSTED : ModelStatus.RATE_LIMITED;
                transition(model.id(), status, now.plus(wait), detail, error.rateLimit());
            }
            case QUOTA_EXHAUSTED -> {
                Instant until = retryAfter != null && retryAfter.isPositive()
                        ? now.plus(retryAfter)
                        : detail.toLowerCase(Locale.ROOT).contains("month")
                                ? nextMonthStart(model, now) : nextDayStart(model, now);
                transition(model.id(), ModelStatus.EXHAUSTED, until, detail, error.rateLimit());
            }
            case AUTH, MODEL_NOT_FOUND -> transition(model.id(), ModelStatus.ERROR, null, detail, error.rateLimit());
            default -> {
                // timeouts, 5xx, bad requests and invalid output say nothing about quota
            }
        }
    }

    private Optional<Verdict> verdictFromCounts(RegisteredModel model, Instant now) {
        LlmRegistryFile.Limits limits = model.limits();
        Instant dayStart = dayStart(model, now);
        Instant monthStart = monthStart(model, now);
        List<Usage> usages = new ArrayList<>();
        if (limits.rpd() != null) {
            usages.add(new Usage("Daily request limit", calls.countByModelIdAndCreatedAtGreaterThanEqual(model.id(), dayStart),
                    limits.rpd(), nextDayStart(model, now)));
        }
        if (limits.tokensPerDay() != null) {
            usages.add(new Usage("Daily token limit", calls.sumTokens(model.id(), dayStart), limits.tokensPerDay(),
                    nextDayStart(model, now)));
        }
        if (limits.requestsPerMonth() != null) {
            usages.add(new Usage("Monthly request limit",
                    calls.countByModelIdAndCreatedAtGreaterThanEqual(model.id(), monthStart), limits.requestsPerMonth(),
                    nextMonthStart(model, now)));
        }
        Optional<Usage> exhausted = usages.stream().filter(u -> u.used() >= u.limit()).findFirst();
        if (exhausted.isPresent()) {
            Usage u = exhausted.get();
            return Optional.of(new Verdict(ModelStatus.EXHAUSTED, u.resetAt(), u.name() + " " + u.used() + "/" + u.limit()));
        }
        int percent = registry.nearLimitPercent();
        return usages.stream()
                .filter(u -> u.used() * 100 >= (long) u.limit() * percent)
                .findFirst()
                .map(u -> new Verdict(ModelStatus.NEAR_LIMIT, u.resetAt(), u.name() + " " + u.used() + "/" + u.limit()
                        + " (" + (u.used() * 100 / u.limit()) + "%)"));
    }

    /** The provider says no requests are left in its window. */
    private static Optional<Verdict> verdictFromHeaders(RateLimitInfo rateLimit, Instant now) {
        if (rateLimit == null || rateLimit.remainingRequests() == null || rateLimit.remainingRequests() > 0
                || rateLimit.resetRequests() == null) {
            return Optional.empty();
        }
        Duration reset = rateLimit.resetRequests();
        ModelStatus status = reset.compareTo(ProviderErrors.EXHAUSTED_AFTER) > 0
                ? ModelStatus.EXHAUSTED : ModelStatus.RATE_LIMITED;
        return Optional.of(new Verdict(status, now.plus(reset), "provider reports 0 requests remaining"));
    }

    private LlmModelState transition(String modelId, ModelStatus to, Instant until, String detail,
            RateLimitInfo rateLimit) {
        Instant now = clock.instant();
        LlmModelState state = states.findById(modelId).orElse(null);
        ModelStatus from = state == null ? null : state.getStatus();
        if (state == null) {
            state = new LlmModelState(modelId, to, now);
        }
        state.setStatus(to);
        state.setUntil(until);
        state.setDetail(shorten(detail));
        if (rateLimit != null) {
            state.setRemainingRequests(rateLimit.remainingRequests());
            state.setRemainingTokens(rateLimit.remainingTokens());
        }
        state.setUpdatedAt(now);
        state = states.save(state);
        ModelStatus effectiveFrom = from == null ? ModelStatus.AVAILABLE : from;
        if (effectiveFrom != to) {
            log.info("llm model={} status {} -> {} until={} detail={}", modelId, effectiveFrom, to, until, state.getDetail());
            events.publishEvent(new LlmModelStatusChanged(modelId, from, to, state.getDetail(), until));
        }
        return state;
    }

    private static ModelState view(LlmModelState state) {
        return new ModelState(state.getModelId(), state.getStatus(), state.getUntil(), state.getDetail());
    }

    private static String shorten(String detail) {
        if (detail == null) {
            return null;
        }
        String oneLine = detail.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= DETAIL_MAX ? oneLine : oneLine.substring(0, DETAIL_MAX);
    }

    private static ZoneId resetZone(RegisteredModel model) {
        return model.resetZone() != null ? model.resetZone() : ZoneOffset.UTC;
    }

    private Instant dayStart(RegisteredModel model, Instant now) {
        return LocalDate.ofInstant(now, resetZone(model)).atStartOfDay(resetZone(model)).toInstant();
    }

    private Instant nextDayStart(RegisteredModel model, Instant now) {
        return LocalDate.ofInstant(now, resetZone(model)).plusDays(1).atStartOfDay(resetZone(model)).toInstant();
    }

    private Instant monthStart(RegisteredModel model, Instant now) {
        return LocalDate.ofInstant(now, resetZone(model)).withDayOfMonth(1).atStartOfDay(resetZone(model)).toInstant();
    }

    private Instant nextMonthStart(RegisteredModel model, Instant now) {
        return LocalDate.ofInstant(now, resetZone(model)).withDayOfMonth(1).plusMonths(1)
                .atStartOfDay(resetZone(model)).toInstant();
    }

    private record Usage(String name, long used, long limit, Instant resetAt) {
    }

    private record Verdict(ModelStatus status, Instant until, String detail) {
    }
}
