package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Cost guards (scoring-spec §8, config-spec §2, ADR-0009), checked before every HTTP attempt:
 * <ul>
 *   <li>at most {@code app.llm.max-calls-per-run} attempts per owner action (all models; a bulk action
 *       is one action, ADR-0010 — {@link #beginRun()} starts it);
 *   <li>no PAID call unless the owner consented with {@code app.llm.allow-paid-calls=true}
 *       (default false);
 *   <li>no PAID call once the month-to-date PAID cost (UTC month, from {@code llm_call.cost_usd})
 *       reached {@code app.llm.monthly-budget-usd}.
 * </ul>
 * Hitting a guard is not a failure: the router answers {@link LlmUnavailable}.
 */
@Component
public class BudgetGuard {

    private final LlmCallRepository calls;
    private final LlmProperties properties;
    private final Clock clock;
    private final AtomicInteger callsThisRun = new AtomicInteger();

    public BudgetGuard(LlmCallRepository calls, LlmProperties properties, Clock clock) {
        this.calls = calls;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new pipeline run: resets the per-run counter. */
    public void beginRun() {
        callsThisRun.set(0);
    }

    public int callsThisRun() {
        return callsThisRun.get();
    }

    /** Whether the per-run limit is reached. */
    public Optional<LlmUnavailable.Reason> checkRun() {
        return callsThisRun.get() >= properties.maxCallsPerRun()
                ? Optional.of(LlmUnavailable.Reason.RUN_CALL_LIMIT)
                : Optional.empty();
    }

    /** Whether a call to a model of this tier may start (per-run limit, paid consent, monthly PAID budget). */
    public Optional<LlmUnavailable.Reason> check(ModelTier tier) {
        Optional<LlmUnavailable.Reason> run = checkRun();
        if (run.isPresent()) {
            return run;
        }
        if (tier == ModelTier.PAID && !properties.allowPaidCalls()) {
            return Optional.of(LlmUnavailable.Reason.PAID_NOT_ALLOWED);
        }
        if (tier == ModelTier.PAID && monthToDatePaidCost().compareTo(properties.monthlyBudgetUsd()) >= 0) {
            return Optional.of(LlmUnavailable.Reason.MONTHLY_BUDGET);
        }
        return Optional.empty();
    }

    /** Counts one HTTP attempt against the per-run limit. */
    public void recordCall() {
        callsThisRun.incrementAndGet();
    }

    /**
     * Estimated USD cost of {@code calls} calls of this purpose on this model: 0 for FREE/LOCAL;
     * for PAID the average {@code cost_usd} of its successful calls in the last 30 days × calls,
     * empty without history (api-spec, re-score and bulk dry runs).
     */
    public Optional<BigDecimal> estimateCostUsd(ModelChoice model, LlmPurpose purpose, int calls) {
        if (!model.paid()) {
            return Optional.of(BigDecimal.ZERO);
        }
        BigDecimal average = calls == 0 ? BigDecimal.ZERO
                : this.calls.averageCost(model.modelId(), purpose, clock.instant().minus(Duration.ofDays(30)));
        return Optional.ofNullable(average).map(a -> a.multiply(BigDecimal.valueOf(calls)).setScale(4, RoundingMode.HALF_UP));
    }

    public BigDecimal monthToDatePaidCost() {
        Instant monthStart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        return calls.sumCost(ModelTier.PAID, monthStart);
    }
}
