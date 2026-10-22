package io.github.quanghung304.jobhunter.notify;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.BudgetGuard;
import io.github.quanghung304.jobhunter.llm.LlmAlert;
import io.github.quanghung304.jobhunter.llm.LlmAlertService;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.LlmRouter;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.llm.LlmUnavailable;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient.Step;
import io.github.quanghung304.jobhunter.notify.persistence.NotificationRepository;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * T-022 acceptance: a daily-quota 429 on the EXTRACT model sends one Telegram message and the
 * alert says EXTRACT is paused with suggestions; a second 429 the same day sends no new alert.
 */
@PostgresJpaTest
@Import(LlmAlertTestConfig.class)
class LlmQuotaAlertIT {

    record Ok(Boolean ok) {
    }

    @Autowired
    LlmRouter router;

    @Autowired
    LlmAlertService alerts;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    BudgetGuard budget;

    @Autowired
    MutableClock clock;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    LlmAlertTestConfig.Sent sent;

    @BeforeEach
    void setUp() {
        fake.reset();
        sent.texts.clear();
        clock.set(LlmRouterTestConfig.NOW);
        budget.beginRun();
    }

    private void extract() {
        router.call(LlmPurpose.EXTRACT, "test-v1", Map.of(), null, Ok.class);
    }

    @Test
    void aDailyQuotaSendsOneMessageAndPausesExtractUntilTheReset() {
        fake.enqueue(Step.dailyQuota(Duration.ofHours(2)));

        extract();

        assertThat(sent.texts).singleElement().asString()
                .startsWith("⚠️ <b>Primary</b> hết hạn mức")
                .contains("Nút Analyze và Score tạm khoá.")
                .contains("Mở lại lúc 16:00.");
        LlmAlert alert = alerts.alert(LlmPurpose.EXTRACT).orElseThrow();
        assertThat(alert.status()).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(alert.paused()).isTrue();
        assertThat(alert.suggestions()).isNotEmpty();
        assertThat(notifications.existsByRefAndSuccessTrue("primary:EXHAUSTED:2026-10-17")).isTrue();
    }

    @Test
    void aSecondQuotaTheSameDaySendsNoNewAlert() {
        fake.enqueue(Step.dailyQuota(Duration.ofHours(2)));
        extract();

        clock.advance(Duration.ofHours(2).plusMinutes(1));
        fake.enqueue(Step.dailyQuota(Duration.ofHours(2)));
        extract();

        assertThat(fake.requests()).as("the model was tried again after its reset").hasSize(2);
        assertThat(sent.texts).filteredOn(text -> text.contains("hết hạn mức")).hasSize(1);
        assertThat(sent.texts).as("the reset itself is announced, after an exhausted alert that day")
                .anyMatch(text -> text.equals("✅ <b>Primary</b> dùng được trở lại."));
        assertThat(router.call(LlmPurpose.EXTRACT, "test-v1", Map.of(), null, Ok.class))
                .isInstanceOf(LlmUnavailable.class);
    }
}
