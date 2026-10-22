package io.github.quanghung304.jobhunter.notify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.quanghung304.jobhunter.tracking.BoardFixtures.HUGH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.notify.NotificationService.Outcome;
import io.github.quanghung304.jobhunter.notify.persistence.Notification;
import io.github.quanghung304.jobhunter.notify.persistence.NotificationRepository;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.tracking.BoardFixtures;
import io.github.quanghung304.jobhunter.tracking.DigestQuery;
import io.github.quanghung304.jobhunter.tracking.TrackingTestConfig;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** The digest against Postgres (example profile: digestMinScore 65) and a WireMock Bot API. */
@PostgresJpaTest
@Import({TrackingTestConfig.class, DigestQuery.class})
class NotificationServiceIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    static final String PATH = "/botTOKEN/sendMessage";
    static final Instant NOW = LlmRouterTestConfig.NOW;

    @Autowired
    DigestQuery digests;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    ProfileProvider profiles;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    BoardFixtures db;

    @BeforeEach
    void setUp() {
        db = new BoardFixtures(jdbc);
        // Fixture rows are written at NOW; digests are sent an hour later.
        clock.set(NOW.plus(Duration.ofHours(1)));
        wm.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("{\"ok\":true,\"result\":{\"message_id\":1}}")));
    }

    private NotificationService service(boolean enabled) {
        var telegram = new NotifyProperties.Telegram(enabled, "TOKEN", "-1001", URI.create(wm.baseUrl()));
        var gateway = new TelegramGateway(new NotifyProperties("-", telegram), duration -> { }, new ObjectMapper());
        return new NotificationService(gateway, profiles, digests, notifications, clock, ZoneId.of("Europe/Rome"),
                "http://localhost:8080");
    }

    private long scoredJob(String title, int score) {
        long id = db.job(title, "Acme", 1);
        db.posting(id, "himalayas");
        db.scored(id, HUGH, score);
        return id;
    }

    private List<String> sentTexts() {
        return wm.findAll(postRequestedFor(urlEqualTo(PATH))).stream()
                .map(request -> {
                    try {
                        return new ObjectMapper().readTree(request.getBodyAsString()).get("text").asText();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    @Test
    void sendsScoredNewJobsAboveTheThresholdAndTheWaitingCountThenRecordsThem() {
        long best = scoredJob("Senior Java Developer", 90);
        long good = scoredJob("Java Developer", 70);
        scoredJob("Weak Java Developer", 50);
        long ineligible = scoredJob("Ineligible Java Developer", 95);
        db.decide(ineligible, HUGH, "INELIGIBLE");
        long shortlisted = scoredJob("Shortlisted Java Developer", 88);
        db.tracking(shortlisted, HUGH, "SHORTLISTED");
        long expired = db.job("Expired Java Developer", "Acme", 1, "EXPIRED");
        db.scored(expired, HUGH, 99);
        db.passed(db.job("Waiting Java Developer", "Acme", 1), HUGH);
        db.rejected(db.job("iOS Engineer", "Acme", 1), HUGH);
        db.analyzed(db.job("Analyzed Java Developer", "Acme", 1), HUGH, "OK");

        NotificationService.DigestResult result = service(true).sendDigest();

        assertThat(result.outcome()).isEqualTo(Outcome.SENT);
        assertThat(result.jobIds()).containsExactly(best, good);
        String text = sentTexts().getFirst();
        assertThat(text).contains("1 new job waits for Analyze")
                .contains("<b>90</b> <a href=\"http://localhost:8080/jobs/" + best + "\">Senior Java Developer</a>")
                .contains("<a href=\"https://himalayas.example/jobs/" + best + "\">Posting</a>")
                .contains("Java Developer</a>")
                .doesNotContain("Weak", "Ineligible", "Shortlisted", "Expired");
        Notification row = notifications.findAll().getFirst();
        assertThat(row.isSuccess()).isTrue();
        assertThat(row.getKind()).isEqualTo(NotificationKind.DIGEST);
        assertThat(row.getProfileId()).isEqualTo(HUGH);
        assertThat(row.getJobIds()).containsExactly(best, good);
        assertThat(row.getSentAt()).isEqualTo(clock.instant());
    }

    @Test
    void aJobAppearsInAtMostOneDigest() {
        long first = scoredJob("Java Developer", 80);
        NotificationService service = service(true);
        assertThat(service.sendDigest().jobIds()).containsExactly(first);

        clock.advance(Duration.ofDays(1));
        assertThat(service.sendDigest().outcome()).as("nothing new, nothing waiting").isEqualTo(Outcome.NOTHING_TO_SEND);

        long second = scoredJob("Kotlin Developer", 75);
        touch(first, clock.instant().minusSeconds(60));
        touch(second, clock.instant().minusSeconds(60));
        clock.advance(Duration.ofHours(1));
        NotificationService.DigestResult next = service.sendDigest();

        assertThat(next.jobIds()).as("re-scored job already sent").containsExactly(second);
        assertThat(sentTexts()).hasSize(2);
        assertThat(sentTexts().get(1)).contains("Kotlin Developer").doesNotContain(">Java Developer<");
    }

    @Test
    void nothingToSendSendsNothing() {
        scoredJob("Weak Java Developer", 40);

        assertThat(service(true).sendDigest().outcome()).isEqualTo(Outcome.NOTHING_TO_SEND);

        wm.verify(0, postRequestedFor(urlEqualTo(PATH)));
        assertThat(notifications.count()).isZero();
    }

    @Test
    void aFailedSendIsRecordedAndItsJobsComeAgain() {
        long job = scoredJob("Java Developer", 80);
        wm.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}")));
        NotificationService service = service(true);

        NotificationService.DigestResult failed = service.sendDigest();

        assertThat(failed.outcome()).isEqualTo(Outcome.FAILED);
        Notification row = notifications.findAll().getFirst();
        assertThat(row.isSuccess()).isFalse();
        assertThat(row.getError()).isEqualTo("Telegram answered 400: Bad Request: chat not found");

        wm.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("{\"ok\":true,\"result\":{\"message_id\":2}}")));
        clock.advance(Duration.ofMinutes(5));
        assertThat(service.sendDigest().jobIds()).containsExactly(job);
    }

    @Test
    void disabledSendsNothing() {
        scoredJob("Java Developer", 80);

        assertThat(service(false).sendDigest().outcome()).isEqualTo(Outcome.DISABLED);

        wm.verify(0, postRequestedFor(urlEqualTo(PATH)));
        assertThat(notifications.count()).isZero();
    }

    private void touch(long jobId, Instant updatedAt) {
        jdbc.update("update job_assessment set updated_at = :t where job_id = :job",
                new MapSqlParameterSource().addValue("t", Timestamp.from(updatedAt)).addValue("job", jobId));
    }
}
