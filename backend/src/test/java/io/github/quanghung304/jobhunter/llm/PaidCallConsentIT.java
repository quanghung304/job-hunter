package io.github.quanghung304.jobhunter.llm;

import static io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient.Step;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignment;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * ADR-0009: without the owner's consent ({@code app.llm.allow-paid-calls=false}, the default) no
 * request ever reaches a PAID model — not as the assignment, not as a fallback, not via callModel.
 */
@PostgresJpaTest
@Import(LlmRouterTestConfig.class)
@TestPropertySource(properties = "test.llm.allow-paid=false")
class PaidCallConsentIT {

    record Score(Integer score) {
    }

    static final Map<String, String> VARIABLES = LlmRouterIT.VARIABLES;

    @Autowired
    LlmRouter router;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    BudgetGuard budget;

    @Autowired
    LlmAssignmentRepository assignments;

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
    void paidModelIsNeverCalledDirectly() {
        LlmOutcome<Score> outcome = router.callModel("paid", LlmPurpose.TEST, "score-v1", VARIABLES, null, Score.class);

        assertThat(outcome).isInstanceOfSatisfying(LlmUnavailable.class, u -> {
            assertThat(u.reason()).isEqualTo(LlmUnavailable.Reason.PAID_NOT_ALLOWED);
            assertThat(u.detail()).contains("app.llm.allow-paid-calls=false");
        });
        assertThat(fake.requests()).isEmpty();
        assertThat(calls.count()).isZero();
    }

    @Test
    void paidAssignmentIsNeverCalled() {
        assignments.saveAndFlush(new LlmAssignment(LlmPurpose.SCORE, "paid", List.of(), false, true, NOW,
                LlmAssignment.BY_USER));

        LlmOutcome<Score> outcome = router.call(LlmPurpose.SCORE, "score-v1", VARIABLES, null, Score.class);

        assertThat(outcome).isInstanceOfSatisfying(LlmUnavailable.class,
                u -> assertThat(u.reason()).isEqualTo(LlmUnavailable.Reason.PAID_NOT_ALLOWED));
        assertThat(fake.requests()).isEmpty();
    }

    @Test
    void paidFallbackIsSkippedEvenWithAllowPaid() {
        assignments.saveAndFlush(new LlmAssignment(LlmPurpose.SCORE, "primary", List.of("paid"), true, true, NOW,
                LlmAssignment.BY_USER));
        fake.enqueue(Step.dailyQuota(null));

        LlmOutcome<Score> outcome = router.call(LlmPurpose.SCORE, "score-v1", VARIABLES, null, Score.class);

        assertThat(outcome).isInstanceOf(LlmUnavailable.class);
        assertThat(fake.requests()).extracting(r -> r.model().id()).containsExactly("primary");
    }

    @Test
    void freeAndLocalModelsStillWork() {
        assertThat(router.callModel("primary", LlmPurpose.TEST, "score-v1", VARIABLES, null, Score.class))
                .isInstanceOf(LlmResult.class);
        assertThat(router.callModel("local", LlmPurpose.TEST, "score-v1", VARIABLES, null, Score.class))
                .isInstanceOf(LlmResult.class);
        assertThat(budget.check(ModelTier.PAID)).contains(LlmUnavailable.Reason.PAID_NOT_ALLOWED);
        assertThat(budget.check(ModelTier.FREE)).isEmpty();
    }
}
