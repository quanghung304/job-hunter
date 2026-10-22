package io.github.quanghung304.jobhunter.notify;

import io.github.quanghung304.jobhunter.llm.AssignmentChangeReason;
import io.github.quanghung304.jobhunter.llm.LlmAlert;
import io.github.quanghung304.jobhunter.llm.LlmAlertService;
import io.github.quanghung304.jobhunter.llm.LlmAssignmentSwitched;
import io.github.quanghung304.jobhunter.llm.LlmModelStatusChanged;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.notify.persistence.Notification;
import io.github.quanghung304.jobhunter.notify.persistence.NotificationRepository;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.profile.SummaryLanguage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Telegram alerts about the models that serve EXTRACT or SCORE (T-022, ADR-0008): on a transition
 * to NEAR_LIMIT, EXHAUSTED or ERROR; back to AVAILABLE only if an EXHAUSTED or ERROR alert of that
 * model went out the same (UTC) day; and when auto-fallback switched a purpose. Each
 * {@code <model>:<status>:<UTC date>} is sent at most once ({@code notification.ref}). Models
 * that serve no purpose stay silent. Sending runs on its own thread, so an LLM call never waits
 * for Telegram, and a failure is only logged and recorded.
 */
@Component
public class LlmQuotaNotifier {

    private static final Logger log = LoggerFactory.getLogger(LlmQuotaNotifier.class);
    private static final Set<ModelStatus> ANNOUNCED = Set.of(ModelStatus.NEAR_LIMIT, ModelStatus.EXHAUSTED,
            ModelStatus.ERROR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TelegramGateway telegram;
    private final NotificationRepository notifications;
    private final LlmAlertService alerts;
    private final ProfileProvider profiles;
    private final Clock clock;
    private final ZoneId zone;
    private final String adminUrl;
    private final Executor executor;

    @Autowired
    public LlmQuotaNotifier(TelegramGateway telegram, NotificationRepository notifications, LlmAlertService alerts,
            ProfileProvider profiles, Clock clock, @Value("${app.pipeline.zone:Europe/Rome}") ZoneId zone,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this(telegram, notifications, alerts, profiles, clock, zone, publicBaseUrl,
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "llm-quota-telegram");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    /** With a given executor (tests run the sends inline). */
    LlmQuotaNotifier(TelegramGateway telegram, NotificationRepository notifications, LlmAlertService alerts,
            ProfileProvider profiles, Clock clock, ZoneId zone, String publicBaseUrl, Executor executor) {
        this.telegram = telegram;
        this.notifications = notifications;
        this.alerts = alerts;
        this.profiles = profiles;
        this.clock = clock;
        this.zone = zone;
        this.adminUrl = (publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl) + "/admin#llm";
        this.executor = executor;
    }

    @EventListener
    public void onStatusChanged(LlmModelStatusChanged event) {
        if (!telegram.enabled()) {
            return;
        }
        if (ANNOUNCED.contains(event.to())
                || (event.to() == ModelStatus.AVAILABLE
                        && (event.from() == ModelStatus.EXHAUSTED || event.from() == ModelStatus.ERROR))) {
            executor.execute(() -> safely(() -> statusChanged(event)));
        }
    }

    @EventListener
    public void onSwitched(LlmAssignmentSwitched event) {
        if (telegram.enabled() && event.reason() == AssignmentChangeReason.AUTO_FALLBACK) {
            executor.execute(() -> safely(() -> autoFallback(event)));
        }
    }

    private void statusChanged(LlmModelStatusChanged event) {
        List<LlmPurpose> purposes = alerts.purposesServedBy(event.modelId());
        if (purposes.isEmpty()) {
            log.debug("llm model={} -> {} serves no purpose; no alert", event.modelId(), event.to());
            return;
        }
        String day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
        String ref = event.modelId() + ":" + event.to() + ":" + day;
        if (event.to() == ModelStatus.AVAILABLE
                && !notifications.existsByRefAndSuccessTrue(event.modelId() + ":" + ModelStatus.EXHAUSTED + ":" + day)
                && !notifications.existsByRefAndSuccessTrue(event.modelId() + ":" + ModelStatus.ERROR + ":" + day)) {
            return;
        }
        if (notifications.existsByRefAndSuccessTrue(ref)) {
            return;
        }
        boolean paused = purposes.stream().anyMatch(p -> alerts.alert(p).map(LlmAlert::paused).orElse(false));
        var context = new LlmQuotaMessages.Context(vietnamese(), alerts.label(event.modelId()), event.detail(),
                purposes, paused, event.until() == null ? null : TIME.format(event.until().atZone(zone)), adminUrl);
        String text = switch (event.to()) {
            case NEAR_LIMIT -> LlmQuotaMessages.nearLimit(context);
            case EXHAUSTED -> LlmQuotaMessages.exhausted(context);
            case ERROR -> LlmQuotaMessages.error(context);
            default -> LlmQuotaMessages.recovered(context);
        };
        send(ref, text);
    }

    private void autoFallback(LlmAssignmentSwitched event) {
        String day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
        String ref = "fallback:" + event.purpose() + ":" + event.toModelId() + ":" + day;
        if (notifications.existsByRefAndSuccessTrue(ref)) {
            return;
        }
        ModelStatus fromStatus = alerts.status(event.fromModelId()).orElse(null);
        String text = LlmQuotaMessages.autoFallback(vietnamese(), event.purpose(), alerts.label(event.fromModelId()),
                fromStatus, alerts.label(event.toModelId()), adminUrl);
        send(ref, text);
    }

    private void send(String ref, String text) {
        Instant now = clock.instant();
        try {
            telegram.send(text);
            notifications.save(Notification.llmQuota(ref, now, true, null));
        } catch (TelegramClient.TelegramException e) {
            log.warn("LLM alert {} not sent: {}", ref, e.getMessage());
            notifications.save(Notification.llmQuota(ref, now, false, e.getMessage()));
        }
    }

    private boolean vietnamese() {
        return profiles.current().summaryLanguage() == SummaryLanguage.VI;
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("LLM alert failed", e);
        }
    }
}
