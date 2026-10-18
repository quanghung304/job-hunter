package io.github.quanghung304.jobhunter.llm;

import static io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig.MAX_CALLS_PER_RUN;
import static io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCall;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@PostgresJpaTest
@Import(LlmRouterTestConfig.class)
class BudgetGuardIT {

    record Score(Integer score) {
    }

    static final Map<String, String> VARIABLES = LlmRouterIT.VARIABLES;

    @Autowired
    BudgetGuard budget;

    @Autowired
    LlmRouter router;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    LlmCallRepository calls;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void reset() {
        fake.reset();
        clock.set(NOW);
        budget.beginRun();
    }

    @Test
    void perRunLimitStopsAllModelsUntilTheNextRun() {
        for (int i = 0; i < MAX_CALLS_PER_RUN; i++) {
            assertThat(call("primary")).isInstanceOf(LlmResult.class);
        }

        assertThat(call("primary")).isInstanceOfSatisfying(LlmUnavailable.class,
                u -> assertThat(u.reason()).isEqualTo(LlmUnavailable.Reason.RUN_CALL_LIMIT));
        assertThat(call("local")).isInstanceOf(LlmUnavailable.class);
        assertThat(router.call(LlmPurpose.SCORE, "score-v1", VARIABLES, null, Score.class))
                .isInstanceOfSatisfying(LlmUnavailable.class,
                        u -> assertThat(u.reason()).isEqualTo(LlmUnavailable.Reason.RUN_CALL_LIMIT));
        assertThat(fake.requests()).hasSize(MAX_CALLS_PER_RUN);

        budget.beginRun();
        assertThat(call("primary")).isInstanceOf(LlmResult.class);
    }

    @Test
    void monthlyBudgetBlocksPaidButNotFreeOrLocal() {
        paidCost("0.60", NOW.minusSeconds(3600));
        paidCost("0.40", Instant.parse("2026-10-01T00:00:00Z"));
        paidCost("5.00", Instant.parse("2026-09-30T23:59:59Z"));

        assertThat(budget.monthToDatePaidCost()).isEqualByComparingTo("1.00");
        assertThat(call("paid")).isInstanceOfSatisfying(LlmUnavailable.class,
                u -> assertThat(u.reason()).isEqualTo(LlmUnavailable.Reason.MONTHLY_BUDGET));
        assertThat(call("primary")).isInstanceOf(LlmResult.class);
        assertThat(call("local")).isInstanceOf(LlmResult.class);
        assertThat(fake.requests()).extracting(r -> r.model().id()).containsExactly("primary", "local");
    }

    @Test
    void paidCallsWorkBelowTheBudget() {
        paidCost("0.99", NOW.minusSeconds(60));

        assertThat(call("paid")).isInstanceOf(LlmResult.class);
        assertThat(budget.monthToDatePaidCost()).as("cost of the call is added").isGreaterThan(new BigDecimal("0.99"));
    }

    @Test
    void checkIsEmptyWhenNothingIsUsed() {
        assertThat(budget.check(ModelTier.PAID)).isEmpty();
        assertThat(budget.check(ModelTier.FREE)).isEmpty();
        assertThat(budget.callsThisRun()).isZero();
    }

    private LlmOutcome<Score> call(String modelId) {
        return router.callModel(modelId, LlmPurpose.TEST, "score-v1", VARIABLES, null, Score.class);
    }

    private void paidCost(String usd, Instant at) {
        calls.saveAndFlush(LlmCall.builder().purpose(LlmPurpose.SCORE).model("paid", "paid-model", ModelTier.PAID)
                .promptVersion("score-v1").tokens(1, 1).costUsd(new BigDecimal(usd)).success(true).createdAt(at)
                .build());
    }
}
