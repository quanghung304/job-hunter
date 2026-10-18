package io.github.quanghung304.jobhunter.pipeline;

import io.github.quanghung304.jobhunter.source.CollectorRunner;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts runs on {@code app.pipeline.cron} and once on startup when the last successful
 * collector run is older than {@code app.pipeline.catch-up-after} (the laptop was asleep).
 */
@Component
class PipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduler.class);

    private final PipelineOrchestrator orchestrator;
    private final CollectorRunner collectors;
    private final PipelineProperties properties;
    private final Clock clock;

    PipelineScheduler(PipelineOrchestrator orchestrator, CollectorRunner collectors, PipelineProperties properties,
            Clock clock) {
        this.orchestrator = orchestrator;
        this.collectors = collectors;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.pipeline.cron:0 0 7,19 * * *}", zone = "${app.pipeline.zone:Europe/Rome}")
    void scheduled() {
        start(Trigger.SCHEDULED);
    }

    @EventListener(ApplicationReadyEvent.class)
    void catchUp() {
        if (!properties.catchUpEnabled()) {
            return;
        }
        if (catchUpNeeded(collectors.lastSuccessfulRunAt(), clock.instant())) {
            start(Trigger.CATCH_UP);
        }
    }

    boolean catchUpNeeded(Optional<Instant> lastSuccess, Instant now) {
        return lastSuccess.map(last -> last.isBefore(now.minus(properties.catchUpAfter()))).orElse(true);
    }

    private void start(Trigger trigger) {
        try {
            orchestrator.startAsync(trigger);
        } catch (PipelineAlreadyRunningException e) {
            log.info("pipeline {} run skipped: a run is already in progress", trigger);
        }
    }
}
