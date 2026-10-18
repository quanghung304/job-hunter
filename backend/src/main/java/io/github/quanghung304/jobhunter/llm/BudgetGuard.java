package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Cost guards (scoring-spec §8, config-spec §2, ADR-0009), checked before every HTTP attempt:
 * <ul>
 *   <li>at most {@code app.llm.max-calls-per-run} attempts per pipeline run (all models);
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

    public BigDecimal monthToDatePaidCost() {
        Instant monthStart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        return calls.sumCost(ModelTier.PAID, monthStart);
    }
}
