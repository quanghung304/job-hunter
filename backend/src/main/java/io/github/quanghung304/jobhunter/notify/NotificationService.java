package io.github.quanghung304.jobhunter.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.common.Sleeper;
import io.github.quanghung304.jobhunter.notify.DigestBuilder.Digest;
import io.github.quanghung304.jobhunter.notify.persistence.Notification;
import io.github.quanghung304.jobhunter.notify.persistence.NotificationRepository;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.tracking.DigestQuery;
import io.github.quanghung304.jobhunter.tracking.DigestQuery.ScoredJob;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The daily Telegram digest (PRD F2.5, {@code app.notify.digest-cron}); no instant alerts
 * (ADR-0010). A scored job is listed in at most one successful digest per profile. Every send,
 * successful or not, is recorded in {@code notification}. Nothing is sent unless
 * {@code app.notify.telegram.enabled=true} and the bot token and chat id are set.
 */
@Service
@EnableConfigurationProperties(NotifyProperties.class)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** What a digest attempt did. */
    public enum Outcome {
        SENT, NOTHING_TO_SEND, DISABLED, FAILED
    }

    public record DigestResult(Outcome outcome, List<Long> jobIds) {
    }

    private final TelegramClient telegram;
    private final ProfileProvider profiles;
    private final DigestQuery digests;
    private final NotificationRepository notifications;
    private final Clock clock;
    private final ZoneId zone;
    private final String publicBaseUrl;

    public NotificationService(NotifyProperties properties, Sleeper sleeper, ObjectMapper json,
            ProfileProvider profiles, DigestQuery digests, NotificationRepository notifications, Clock clock,
            @Value("${app.pipeline.zone:Europe/Rome}") ZoneId zone,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        NotifyProperties.Telegram settings = properties.telegram();
        this.telegram = settings.usable() ? new TelegramClient(settings, sleeper, json) : null;
        if (settings.enabled() && !settings.usable()) {
            log.warn("app.notify.telegram.enabled=true but TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is missing;"
                    + " no Telegram messages will be sent");
        } else if (!settings.enabled()) {
            log.info("Telegram notifications are off (app.notify.telegram.enabled=false)");
        }
        this.profiles = profiles;
        this.digests = digests;
        this.notifications = notifications;
        this.clock = clock;
        this.zone = zone;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Scheduled(cron = "${app.notify.digest-cron:0 30 8 * * *}", zone = "${app.pipeline.zone:Europe/Rome}")
    void scheduledDigest() {
        DigestResult result = sendDigest();
        log.info("digest outcome={} jobs={}", result.outcome(), result.jobIds().size());
    }

    /**
     * Builds and sends the digest of the current profile: jobs waiting for Analyze, and jobs scored
     * since the last successful digest with a score of at least {@code notify.digestMinScore}, NEW,
     * not marked Not eligible and not in an earlier digest.
     */
    public synchronized DigestResult sendDigest() {
        if (telegram == null) {
            return new DigestResult(Outcome.DISABLED, List.of());
        }
        Profile profile = profiles.current();
        String profileId = profile.id();
        int minScore = profile.notifySettings().digestMinScore();
        Instant since = notifications
                .findFirstByProfileIdAndKindAndSuccessTrueOrderBySentAtDesc(profileId, NotificationKind.DIGEST)
                .map(Notification::getSentAt)
                .orElse(null);
        Set<Long> alreadySent = new HashSet<>(notifications.findSentJobIds(profileId, NotificationKind.DIGEST.name()));
        List<ScoredJob> scored = digests.scoredSince(profileId, minScore, since).stream()
                .filter(job -> !alreadySent.contains(job.id()))
                .toList();
        long waiting = digests.countAwaitingAnalysis(profileId);
        Instant now = clock.instant();

        Optional<Digest> digest = DigestBuilder.build(LocalDate.ofInstant(now, zone), waiting, scored, minScore,
                publicBaseUrl);
        if (digest.isEmpty()) {
            return new DigestResult(Outcome.NOTHING_TO_SEND, List.of());
        }
        List<Long> jobIds = digest.get().jobIds();
        try {
            telegram.sendMessage(digest.get().html());
            notifications.save(new Notification(profileId, NotificationChannel.TELEGRAM, NotificationKind.DIGEST,
                    jobIds, now, true, null));
            return new DigestResult(Outcome.SENT, jobIds);
        } catch (TelegramClient.TelegramException e) {
            log.warn("digest not sent: {}", e.getMessage());
            notifications.save(new Notification(profileId, NotificationChannel.TELEGRAM, NotificationKind.DIGEST,
                    jobIds, now, false, e.getMessage()));
            return new DigestResult(Outcome.FAILED, jobIds);
        }
    }
}
