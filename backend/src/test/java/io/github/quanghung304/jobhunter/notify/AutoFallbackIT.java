package io.github.quanghung304.jobhunter.notify;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.AssignmentChangeReason;
import io.github.quanghung304.jobhunter.llm.AssignmentService;
import io.github.quanghung304.jobhunter.llm.BudgetGuard;
import io.github.quanghung304.jobhunter.llm.LlmAlertService;
import io.github.quanghung304.jobhunter.llm.LlmAssignmentSwitched;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmResult;
import io.github.quanghung304.jobhunter.llm.LlmRouter;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient.Step;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEvent;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentEventRepository;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentRepository;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Auto-fallback (T-010 router, T-022 messages): the assignment moves, the event is written and announced. */
@PostgresJpaTest
@Import(LlmAlertTestConfig.class)
@RecordApplicationEvents
class AutoFallbackIT {

    record Ok(Boolean ok) {
    }

    @Autowired
    LlmRouter router;

    @Autowired
    LlmAlertService alerts;

    @Autowired
    AssignmentService assignmentService;

    @Autowired
    LlmAssignmentRepository assignments;

    @Autowired
    LlmAssignmentEventRepository events;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    BudgetGuard budget;

    @Autowired
    MutableClock clock;

    @Autowired
    LlmAlertTestConfig.Sent sent;

    @Autowired
    ApplicationEvents published;

    @BeforeEach
    void setUp() {
        fake.reset();
        sent.texts.clear();
        clock.set(LlmRouterTestConfig.NOW);
        budget.beginRun();
        assignmentService.current(LlmPurpose.EXTRACT);
        var row = assignments.findById(LlmPurpose.EXTRACT).orElseThrow();
        row.setAutoFallback(true);
        assignments.saveAndFlush(row);
    }

    @Test
    void theRouterSwitchesToTheFallbackRecordsItAndAnnouncesIt() {
        fake.enqueue(Step.dailyQuota(Duration.ofHours(1)));

        var outcome = router.call(LlmPurpose.EXTRACT, "test-v1", Map.of(), null, Ok.class);

        assertThat(outcome).isInstanceOfSatisfying(LlmResult.class, r -> assertThat(r.modelId()).isEqualTo("backup"));
        var row = assignments.findById(LlmPurpose.EXTRACT).orElseThrow();
        assertThat(row.getModelId()).isEqualTo("backup");
        assertThat(row.getUpdatedBy()).isEqualTo(AssignmentService.BY_AUTO_FALLBACK);
        assertThat(events.findByPurposeOrderByIdAsc(LlmPurpose.EXTRACT))
                .extracting(LlmAssignmentEvent::getFromModelId, LlmAssignmentEvent::getToModelId,
                        LlmAssignmentEvent::getReason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("primary", "backup",
                        AssignmentChangeReason.AUTO_FALLBACK));
        assertThat(published.stream(LlmAssignmentSwitched.class)).containsExactly(new LlmAssignmentSwitched(
                LlmPurpose.EXTRACT, "primary", "backup", AssignmentChangeReason.AUTO_FALLBACK));
        assertThat(sent.texts).contains("↪️ Đã tự chuyển Extraction sang <b>Backup</b> vì <b>Primary</b> hết hạn mức."
                + " Chuyển lại: <a href=\"http://localhost:8080/admin#llm\">Admin</a>");
        assertThat(alerts.alert(LlmPurpose.EXTRACT)).as("primary still exhausted: no switch-back yet").isEmpty();
    }
}
