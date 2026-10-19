package io.github.quanghung304.jobhunter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PipelineScheduleTest {

    // 2026-10-17 09:00 in Rome (CEST, UTC+2)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T07:00:00Z"), ZoneOffset.UTC);

    private static PipelineSchedule schedule(String cron, String digestCron) {
        return new PipelineSchedule(new PipelineProperties(cron, "Europe/Rome", Duration.ofHours(6), true), digestCron,
                CLOCK);
    }

    @Test
    void nextFireTimesInTheConfiguredZone() {
        PipelineSchedule schedule = schedule("0 0 7,19 * * *", "0 30 8 * * *");

        assertThat(schedule.nextRunAt()).contains(Instant.parse("2026-10-17T17:00:00Z"));
        assertThat(schedule.nextDigestAt()).contains(Instant.parse("2026-10-18T06:30:00Z"));
        assertThat(schedule.zone().getId()).isEqualTo("Europe/Rome");
        assertThat(schedule.startOfToday()).isEqualTo(Instant.parse("2026-10-16T22:00:00Z"));
    }

    @Test
    void disabledCronHasNoNextTime() {
        PipelineSchedule schedule = schedule("-", "-");

        assertThat(schedule.nextRunAt()).isEmpty();
        assertThat(schedule.nextDigestAt()).isEmpty();
    }
}
