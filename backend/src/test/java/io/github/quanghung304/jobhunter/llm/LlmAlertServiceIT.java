package io.github.quanghung304.jobhunter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient.Step;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmCallRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmModelState;
import io.github.quanghung304.jobhunter.llm.persistence.LlmModelStateRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Alerts, owner switches and the "Test" call (T-022) against Postgres and the fake LLM; models from
 * {@code llm-registry/router-test.yaml} (EXTRACT and SCORE: primary, fallbacks backup and paid).
 */
@PostgresJpaTest
@Import({LlmRouterTestConfig.class, LlmAlertService.class, LlmAdminService.class, LlmUsageService.class})
class LlmAlertServiceIT {

    record Ok(Boolean ok) {
    }

    @Autowired
    LlmAlertService alerts;

    @Autowired
    LlmAdminService admin;

    @Autowired
    LlmRouter router;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    BudgetGuard budget;

    @Autowired
    LlmModelStateRepository states;

    @Autowired
    LlmAssignmentRepository assignments;

    @Autowired
    LlmCallRepository calls;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setUp() {
        fake.reset();
        clock.set(LlmRouterTestConfig.NOW);
        budget.beginRun();
    }

    private LlmOutcome<Ok> call(LlmPurpose purpose) {
        return router.call(purpose, LlmAdminService.TEST_PROMPT, Map.of(), null, Ok.class);
    }

    @Test
    void noAlertWhileTheModelsWork() {
        assertThat(call(LlmPurpose.EXTRACT)).isInstanceOf(LlmResult.class);

        assertThat(alerts.alerts()).isEmpty();
    }

    @Test
    void aDailyQuotaPausesBothPurposesOfTheModelWithSuggestions() {
        fake.enqueue(Step.dailyQuota(null));

        assertThat(call(LlmPurpose.EXTRACT)).isInstanceOf(LlmUnavailable.class);

        List<LlmAlert> list = alerts.alerts();
        assertThat(list).extracting(LlmAlert::purpose).containsExactly(LlmPurpose.EXTRACT, LlmPurpose.SCORE);
        LlmAlert extract = list.getFirst();
        assertThat(extract.modelId()).isEqualTo("primary");
        assertThat(extract.modelLabel()).isEqualTo("Primary");
        assertThat(extract.status()).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(extract.until()).isEqualTo(java.time.Instant.parse("2026-10-18T00:00:00Z"));
        assertThat(extract.paused()).isTrue();
        assertThat(extract.switchedFrom()).isNull();
        assertThat(extract.suggestions()).as("fallbacks first, then LOCAL; no key → left out")
                .extracting(LlmAlert.Suggestion::modelId).containsExactly("backup", "paid", "local");
    }

    @Test
    void switchingClearsTheAlertRecordsHistoryAndTheNextCallUsesTheNewModelWithoutAnyCallBySwitching() {
        fake.enqueue(Step.dailyQuota(null));
        call(LlmPurpose.EXTRACT);
        int requestsBefore = fake.requests().size();

        LlmAdminService.SwitchResult result = admin.switchModel(LlmPurpose.EXTRACT, "backup", null, null, null, false);

        assertThat(fake.requests()).as("a switch makes no LLM call").hasSize(requestsBefore);
        assertThat(result.assignment().assignment().modelId()).isEqualTo("backup");
        assertThat(result.assignment().assignment().updatedBy()).isEqualTo(AssignmentService.BY_USER);
        assertThat(result.assignment().assignment().fallbacks()).containsExactly("backup", "paid");
        assertThat(result.warning()).isNull();
        assertThat(alerts.alerts()).extracting(LlmAlert::purpose).containsExactly(LlmPurpose.SCORE);
        assertThat(admin.overview().history()).singleElement().satisfies(event -> {
            assertThat(event.purpose()).isEqualTo(LlmPurpose.EXTRACT);
            assertThat(event.fromModelId()).isEqualTo("primary");
            assertThat(event.toModelId()).isEqualTo("backup");
            assertThat(event.reason()).isEqualTo(AssignmentChangeReason.USER);
        });

        assertThat(call(LlmPurpose.EXTRACT)).isInstanceOfSatisfying(LlmResult.class,
                r -> assertThat(r.modelId()).isEqualTo("backup"));
    }

    @Test
    void aModelInErrorCannotBeAssigned() {
        assertThatThrownBy(() -> admin.switchModel(LlmPurpose.EXTRACT, "nokey", null, null, null, false))
                .isInstanceOfSatisfying(ModelUnusableException.class, e -> assertThat(e.httpStatus()).isEqualTo(409));
        assertThatThrownBy(() -> admin.switchModel(LlmPurpose.EXTRACT, "nope", null, null, null, false))
                .isInstanceOfSatisfying(UnknownModelException.class, e -> assertThat(e.httpStatus()).isEqualTo(400));
        assertThatThrownBy(() -> admin.switchModel(LlmPurpose.EXTRACT, "backup", List.of("nope"), null, null, false))
                .isInstanceOf(UnknownModelException.class);
        assertThat(assignments.findById(LlmPurpose.EXTRACT).orElseThrow().getModelId()).isEqualTo("primary");
    }

    @Test
    void aPaidModelOrPaidFallbackNeedsAConfirmationAndWarns() {
        assertThatThrownBy(() -> admin.switchModel(LlmPurpose.SCORE, "paid", null, null, null, false))
                .isInstanceOfSatisfying(PaidConfirmationRequiredException.class, e -> {
                    assertThat(e.model().modelId()).isEqualTo("paid");
                    assertThat(e.estimatedCostPerCallUsd()).as("no cost history yet").isNull();
                });
        assertThatThrownBy(() -> admin.switchModel(LlmPurpose.SCORE, "primary", null, true, true, false))
                .as("turning on paid fallback").isInstanceOf(PaidConfirmationRequiredException.class);

        LlmAdminService.SwitchResult result = admin.switchModel(LlmPurpose.SCORE, "paid", null, null, null, true);

        assertThat(result.warning()).isEqualTo(LlmAdminService.PAID_WARNING);
        assertThat(result.assignment().assignment().modelId()).isEqualTo("paid");
        assertThat(admin.switchModel(LlmPurpose.SCORE, "paid", List.of("backup"), null, null, false).warning())
                .as("same PAID model, only fallbacks changed: no new confirmation").isNotNull();
    }

    @Test
    void theTestCallClearsAnErrorAndIsLoggedAsTest() {
        LlmModelState broken = new LlmModelState("backup", ModelStatus.ERROR, clock.instant());
        broken.setDetail("HTTP 401 invalid api key");
        states.saveAndFlush(broken);

        LlmAdminService.TestResult result = admin.test("backup", false);

        assertThat(result.ok()).isTrue();
        assertThat(result.status()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(fake.requests()).singleElement().satisfies(request -> {
            assertThat(request.purpose()).isEqualTo(LlmPurpose.TEST);
            assertThat(request.model().id()).isEqualTo("backup");
        });
        assertThat(calls.findAll()).singleElement().satisfies(row -> assertThat(row.getPurpose())
                .isEqualTo(LlmPurpose.TEST));
        assertThatThrownBy(() -> admin.test("nope", false))
                .isInstanceOfSatisfying(UnknownModelException.class, e -> assertThat(e.httpStatus()).isEqualTo(404));
    }

    @Test
    void aFailedTestKeepsTheStatusAndSaysWhy() {
        fake.enqueue(Step.httpError(401, "{\"error\":{\"message\":\"invalid api key\"}}", null));

        LlmAdminService.TestResult result = admin.test("backup", false);

        assertThat(result.ok()).isFalse();
        assertThat(result.status()).isEqualTo(ModelStatus.ERROR);
        assertThat(result.detail()).contains("NO_USABLE_MODEL");
    }

    @Test
    void afterAutoFallbackTheAlertSuggestsSwitchingBackOnceThePreviousModelWorks() {
        var row = assignments.findById(LlmPurpose.SCORE).orElseGet(() -> {
            alerts.alerts();
            return assignments.findById(LlmPurpose.SCORE).orElseThrow();
        });
        row.setAutoFallback(true);
        assignments.saveAndFlush(row);
        fake.enqueue(Step.dailyQuota(Duration.ofMinutes(30)));

        assertThat(call(LlmPurpose.SCORE)).isInstanceOfSatisfying(LlmResult.class,
                r -> assertThat(r.modelId()).isEqualTo("backup"));
        assertThat(alerts.alert(LlmPurpose.SCORE)).as("primary still EXHAUSTED: nothing to switch back to").isEmpty();

        clock.advance(Duration.ofMinutes(31));

        assertThat(alerts.alert(LlmPurpose.SCORE)).hasValueSatisfying(alert -> {
            assertThat(alert.modelId()).isEqualTo("backup");
            assertThat(alert.status()).isEqualTo(ModelStatus.AVAILABLE);
            assertThat(alert.paused()).isFalse();
            assertThat(alert.switchedFrom()).isEqualTo("primary");
            assertThat(alert.suggestions()).extracting(LlmAlert.Suggestion::modelId).containsExactly("primary");
        });
    }
}
