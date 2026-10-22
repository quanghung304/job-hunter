package io.github.quanghung304.jobhunter.notify;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.llm.LlmAlertService;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.notify.persistence.NotificationRepository;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The llm module of {@link LlmRouterTestConfig} with alerts and the Telegram notifier; Telegram is
 * a mock that records the texts ({@link Sent}), and alerts are sent inline (no thread).
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({LlmRouterTestConfig.class, LlmAlertService.class})
class LlmAlertTestConfig {

    /** Texts handed to Telegram. */
    static final class Sent {
        final List<String> texts = new ArrayList<>();
    }

    @Bean
    Sent sent() {
        return new Sent();
    }

    @Bean
    TelegramGateway telegramGateway(Sent sent) {
        TelegramGateway gateway = mock(TelegramGateway.class);
        given(gateway.enabled()).willReturn(true);
        willAnswer(invocation -> sent.texts.add(invocation.getArgument(0))).given(gateway).send(anyString());
        return gateway;
    }

    /** The committed example profile (summary language vi). */
    @Bean
    ProfileProvider profileProvider() {
        return ProfileFixtures.load(ProfileFixtures.EXAMPLE);
    }

    @Bean
    LlmQuotaNotifier llmQuotaNotifier(TelegramGateway telegram, NotificationRepository notifications,
            LlmAlertService alerts, ProfileProvider profiles, MutableClock clock) {
        return new LlmQuotaNotifier(telegram, notifications, alerts, profiles, clock, ZoneId.of("Europe/Rome"),
                "http://localhost:8080", Runnable::run);
    }
}
