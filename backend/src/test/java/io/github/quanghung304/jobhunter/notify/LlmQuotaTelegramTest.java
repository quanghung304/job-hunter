package io.github.quanghung304.jobhunter.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import io.github.quanghung304.jobhunter.llm.AssignmentChangeReason;
import io.github.quanghung304.jobhunter.llm.LlmAlert;
import io.github.quanghung304.jobhunter.llm.LlmAlertService;
import io.github.quanghung304.jobhunter.llm.LlmAssignmentSwitched;
import io.github.quanghung304.jobhunter.llm.LlmModelStatusChanged;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import io.github.quanghung304.jobhunter.llm.ModelStatus;
import io.github.quanghung304.jobhunter.notify.persistence.Notification;
import io.github.quanghung304.jobhunter.notify.persistence.NotificationRepository;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.profile.ProfileSnapshot;
import io.github.quanghung304.jobhunter.profile.SummaryLanguage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The quota alerts' texts (vi/en), dedupe per model, status and UTC day, recovery and silence rules. */
class LlmQuotaTelegramTest {

    static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    static final Instant MIDNIGHT = Instant.parse("2026-10-23T00:00:00Z");
    static final String GROQ = "groq-gpt-oss-120b";

    final List<String> sent = new ArrayList<>();
    final List<Notification> rows = new ArrayList<>();
    TelegramGateway telegram;
    LlmAlertService alerts;
    NotificationRepository repository;

    @BeforeEach
    void setUp() {
        telegram = mock(TelegramGateway.class);
        given(telegram.enabled()).willReturn(true);
        willAnswer(invocation -> sent.add(invocation.getArgument(0))).given(telegram).send(anyString());
        repository = mock(NotificationRepository.class);
        given(repository.save(any(Notification.class))).willAnswer(invocation -> {
            rows.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        given(repository.existsByRefAndSuccessTrue(anyString())).willAnswer(invocation -> rows.stream()
                .anyMatch(row -> row.isSuccess() && invocation.getArgument(0).equals(row.getRef())));
        alerts = mock(LlmAlertService.class);
        given(alerts.purposesServedBy(GROQ)).willReturn(List.of(LlmPurpose.EXTRACT));
        given(alerts.purposesServedBy("unused-model")).willReturn(List.of());
        given(alerts.label(GROQ)).willReturn("GPT-OSS 120B (Groq)");
        given(alerts.label("backup-model")).willReturn("Qwen 3.8 27B (Groq)");
        given(alerts.status(GROQ)).willReturn(Optional.of(ModelStatus.EXHAUSTED));
        given(alerts.alert(LlmPurpose.EXTRACT)).willReturn(Optional.of(new LlmAlert(LlmPurpose.EXTRACT, GROQ,
                "GPT-OSS 120B (Groq)", ModelStatus.EXHAUSTED, null, MIDNIGHT, true, null, List.of())));
    }

    private LlmQuotaNotifier notifier(SummaryLanguage language) {
        Profile example = ProfileFixtures.example();
        Profile profile = new Profile(example.id(), language, example.candidate(), example.preferences(),
                example.prefilter(), example.notifySettings(), example.tracks());
        ProfileProvider profiles = () -> new ProfileSnapshot(profile, "hash", NOW);
        return new LlmQuotaNotifier(telegram, repository, alerts, profiles, Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Rome"), "http://localhost:8080/", Runnable::run);
    }

    private static LlmModelStatusChanged change(ModelStatus from, ModelStatus to, String detail, Instant until) {
        return new LlmModelStatusChanged(GROQ, from, to, detail, until);
    }

    @Test
    void exhaustedInVietnamese() {
        notifier(SummaryLanguage.VI).onStatusChanged(change(ModelStatus.AVAILABLE, ModelStatus.EXHAUSTED,
                "Daily request limit 1000/1000", MIDNIGHT));

        assertThat(sent).containsExactly("⚠️ <b>GPT-OSS 120B (Groq)</b> hết hạn mức hôm nay (1000/1000 requests)."
                + " Nút Analyze tạm khoá. Mở lại lúc 02:00."
                + " Đổi model: <a href=\"http://localhost:8080/admin#llm\">Admin</a>");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getRef()).isEqualTo(GROQ + ":EXHAUSTED:2026-10-22");
            assertThat(row.getKind()).isEqualTo(NotificationKind.LLM_QUOTA);
            assertThat(row.getProfileId()).isNull();
            assertThat(row.getJobIds()).isEmpty();
            assertThat(row.isSuccess()).isTrue();
        });
    }

    @Test
    void exhaustedInEnglish() {
        notifier(SummaryLanguage.EN).onStatusChanged(change(ModelStatus.AVAILABLE, ModelStatus.EXHAUSTED,
                "Daily request limit 1000/1000", MIDNIGHT));

        assertThat(sent).containsExactly("⚠️ <b>GPT-OSS 120B (Groq)</b> reached its limit today (1000/1000 requests)."
                + " Analyze is paused. Resets at 02:00."
                + " Switch model: <a href=\"http://localhost:8080/admin#llm\">Admin</a>");
    }

    @Test
    void theSameModelAndStatusIsAnnouncedOncePerDay() {
        LlmQuotaNotifier notifier = notifier(SummaryLanguage.VI);
        LlmModelStatusChanged exhausted = change(ModelStatus.AVAILABLE, ModelStatus.EXHAUSTED, "quota", MIDNIGHT);

        notifier.onStatusChanged(exhausted);
        notifier.onStatusChanged(change(ModelStatus.EXHAUSTED, ModelStatus.AVAILABLE, "reset", null));
        notifier.onStatusChanged(exhausted);

        assertThat(sent).hasSize(2);
        assertThat(sent.get(1)).isEqualTo("✅ <b>GPT-OSS 120B (Groq)</b> dùng được trở lại.");
    }

    @Test
    void nearLimitAndErrorTexts() {
        LlmQuotaNotifier vi = notifier(SummaryLanguage.VI);
        vi.onStatusChanged(change(ModelStatus.AVAILABLE, ModelStatus.NEAR_LIMIT, "Daily request limit 812/1000 (81%)",
                MIDNIGHT));
        vi.onStatusChanged(change(ModelStatus.NEAR_LIMIT, ModelStatus.ERROR, "HTTP 401 <invalid> & key", null));
        LlmQuotaNotifier en = notifier(SummaryLanguage.EN);
        rows.clear();
        en.onStatusChanged(change(ModelStatus.AVAILABLE, ModelStatus.NEAR_LIMIT, "Daily request limit 812/1000 (81%)",
                MIDNIGHT));

        assertThat(sent).containsExactly(
                "⚠️ <b>GPT-OSS 120B (Groq)</b> đã dùng 81% hạn mức ngày (812/1000).",
                "⚠️ <b>GPT-OSS 120B (Groq)</b> lỗi: HTTP 401 &lt;invalid&gt; &amp; key (kiểm tra API key)."
                        + " Đổi model: <a href=\"http://localhost:8080/admin#llm\">Admin</a>",
                "⚠️ <b>GPT-OSS 120B (Groq)</b> used 81% of its daily limit (812/1000).");
    }

    @Test
    void recoveryIsOnlyAnnouncedAfterAnExhaustedOrErrorAlertThatDay() {
        LlmQuotaNotifier notifier = notifier(SummaryLanguage.EN);

        notifier.onStatusChanged(change(ModelStatus.NEAR_LIMIT, ModelStatus.AVAILABLE, "reset", null));
        notifier.onStatusChanged(change(ModelStatus.EXHAUSTED, ModelStatus.AVAILABLE, "reset", null));

        assertThat(sent).isEmpty();
    }

    @Test
    void rateLimitsAndModelsServingNoPurposeStaySilent() {
        LlmQuotaNotifier notifier = notifier(SummaryLanguage.EN);

        notifier.onStatusChanged(change(ModelStatus.AVAILABLE, ModelStatus.RATE_LIMITED, "429", NOW.plusSeconds(60)));
        notifier.onStatusChanged(new LlmModelStatusChanged("unused-model", null, ModelStatus.ERROR, "missing key", null));

        assertThat(sent).isEmpty();
        assertThat(rows).isEmpty();
    }

    @Test
    void autoFallbackIsAnnounced() {
        notifier(SummaryLanguage.VI).onSwitched(new LlmAssignmentSwitched(LlmPurpose.EXTRACT, GROQ, "backup-model",
                AssignmentChangeReason.AUTO_FALLBACK));
        notifier(SummaryLanguage.EN).onSwitched(new LlmAssignmentSwitched(LlmPurpose.SCORE, GROQ, "backup-model",
                AssignmentChangeReason.AUTO_FALLBACK));
        notifier(SummaryLanguage.EN).onSwitched(new LlmAssignmentSwitched(LlmPurpose.SCORE, GROQ, "backup-model",
                AssignmentChangeReason.USER));

        assertThat(sent).containsExactly(
                "↪️ Đã tự chuyển Extraction sang <b>Qwen 3.8 27B (Groq)</b> vì <b>GPT-OSS 120B (Groq)</b> hết hạn mức."
                        + " Chuyển lại: <a href=\"http://localhost:8080/admin#llm\">Admin</a>",
                "↪️ Switched Scoring to <b>Qwen 3.8 27B (Groq)</b> because <b>GPT-OSS 120B (Groq)</b> reached its"
                        + " limit. Switch back: <a href=\"http://localhost:8080/admin#llm\">Admin</a>");
    }

    @Test
    void aFailedSendIsRecordedAndMayBeRetriedLater() {
        willThrow(new TelegramClient.TelegramException("Telegram answered 400: chat not found")).given(telegram)
                .send(anyString());
        LlmQuotaNotifier notifier = notifier(SummaryLanguage.EN);

        notifier.onStatusChanged(change(ModelStatus.AVAILABLE, ModelStatus.ERROR, "bad key", null));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.isSuccess()).isFalse();
            assertThat(row.getError()).isEqualTo("Telegram answered 400: chat not found");
        });
    }

    @Test
    void nothingIsSentWhenTelegramIsOff() {
        given(telegram.enabled()).willReturn(false);

        notifier(SummaryLanguage.EN).onStatusChanged(change(ModelStatus.AVAILABLE, ModelStatus.ERROR, "x", null));

        assertThat(sent).isEmpty();
        assertThat(rows).isEmpty();
    }
}
