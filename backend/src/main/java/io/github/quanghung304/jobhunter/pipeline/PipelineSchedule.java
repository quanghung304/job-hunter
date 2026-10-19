package io.github.quanghung304.jobhunter.pipeline;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Next fire times of the pipeline ({@code app.pipeline.cron}) and the digest
 * ({@code app.notify.digest-cron}) in {@code app.pipeline.zone}, for the admin page. A cron of
 * {@code "-"} (disabled) has no next time.
 */
@Component
public class PipelineSchedule {

    static final String DISABLED = "-";

    private final PipelineProperties properties;
    private final String digestCron;
    private final Clock clock;

    public PipelineSchedule(PipelineProperties properties,
            @Value("${app.notify.digest-cron:0 30 8 * * *}") String digestCron, Clock clock) {
        this.properties = properties;
        this.digestCron = digestCron;
        this.clock = clock;
    }

    public ZoneId zone() {
        return ZoneId.of(properties.zone());
    }

    public Optional<Instant> nextRunAt() {
        return next(properties.cron());
    }

    public Optional<Instant> nextDigestAt() {
        return next(digestCron);
    }

    /** Local midnight of today in {@link #zone()}: "today" for the admin KPIs (api-spec). */
    public Instant startOfToday() {
        return clock.instant().atZone(zone()).toLocalDate().atStartOfDay(zone()).toInstant();
    }

    private Optional<Instant> next(String cron) {
        if (cron == null || cron.isBlank() || DISABLED.equals(cron.strip())) {
            return Optional.empty();
        }
        ZonedDateTime now = clock.instant().atZone(zone());
        return Optional.ofNullable(CronExpression.parse(cron).next(now)).map(ZonedDateTime::toInstant);
    }
}
