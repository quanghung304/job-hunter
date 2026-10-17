package io.github.quanghung304.jobhunter.source;

import io.github.quanghung304.jobhunter.source.persistence.CollectorRun;
import io.github.quanghung304.jobhunter.source.persistence.CollectorRunRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs one collector with a timeout, never throws for collector failures, and records the run
 * in {@code collector_run}. Ingest counts are added afterwards by whoever ran ingest
 * ({@link #recordIngestCounts}), so this module does not depend on {@code ingest}.
 */
@Component
public class CollectorRunner {

    static final int MAX_ERROR_LENGTH = 2000;
    private static final int MAX_WARNINGS_IN_MESSAGE = 5;

    private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

    private final CollectorRunRepository runs;
    private final SourcesProperties sources;
    private final Clock clock;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CollectorRunner(CollectorRunRepository runs, SourcesProperties sources, Clock clock,
            @Value("${app.pipeline.collector-timeout:2m}") Duration timeout) {
        this.runs = runs;
        this.sources = sources;
        this.clock = clock;
        this.timeout = timeout;
    }

    /** Runs with {@code max-items} from the source settings and no keywords. */
    public CollectorRunOutcome run(JobCollector collector, Instant since) {
        int maxItems = sources.source(collector.sourceCode())
                .map(SourcesProperties.Source::maxItems)
                .orElse(SourcesProperties.DEFAULT_MAX_ITEMS);
        return run(collector, new CollectRequest(since, List.of(), maxItems));
    }

    public CollectorRunOutcome run(JobCollector collector, CollectRequest request) {
        String code = collector.sourceCode();
        Optional<String> skip = minIntervalSkipReason(code);
        if (skip.isPresent()) {
            log.info("source={} skipped: {}", code, skip.get());
            return CollectorRunOutcome.skipped(skip.get());
        }

        Instant started = clock.instant();
        CollectorRun run = runs.save(new CollectorRun(code, started));
        CollectResult result = collectWithTimeout(collector, request);

        Instant finished = clock.instant();
        run.setFinishedAt(finished);
        run.setStatus(result.status());
        run.setFetchedCount(result.fetched());
        run.setErrorMessage(errorMessage(result));
        runs.save(run);
        log.info("source={} fetched={} postings={} status={} ms={}", code, result.fetched(),
                result.postings().size(), result.status(), Duration.between(started, finished).toMillis());
        return CollectorRunOutcome.ran(run.getId(), result);
    }

    public void recordIngestCounts(long runId, int newRaw, int newJobs) {
        CollectorRun run = runs.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No collector_run with id " + runId));
        run.setNewRawCount(newRaw);
        run.setNewJobCount(newJobs);
        runs.save(run);
        log.debug("source={} run={} newRaw={} newJobs={}", run.getSourceCode(), runId, newRaw, newJobs);
    }

    private Optional<String> minIntervalSkipReason(String code) {
        Duration minInterval = sources.source(code).map(SourcesProperties.Source::minInterval).orElse(null);
        if (minInterval == null || minInterval.isZero()) {
            return Optional.empty();
        }
        Instant threshold = clock.instant().minus(minInterval);
        return runs.findFirstBySourceCodeAndStatusOrderByStartedAtDesc(code, CollectStatus.SUCCESS)
                .filter(last -> last.getStartedAt().isAfter(threshold))
                .map(last -> "last successful run at " + last.getStartedAt() + " is within min-interval " + minInterval);
    }

    private CollectResult collectWithTimeout(JobCollector collector, CollectRequest request) {
        Future<CollectResult> future;
        try {
            future = executor.submit(() -> collector.collect(request));
        } catch (RuntimeException e) {
            return CollectResult.failed(0, "could not start collector: " + e);
        }
        try {
            CollectResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return result != null ? result : CollectResult.failed(0, "collector returned no result");
        } catch (TimeoutException e) {
            future.cancel(true);
            return CollectResult.failed(0, "timed out after " + timeout);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("source={} collector threw", collector.sourceCode(), cause);
            return CollectResult.failed(0, "unexpected " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return CollectResult.failed(0, "interrupted");
        }
    }

    static String errorMessage(CollectResult result) {
        String message;
        if (result.errorMessage() != null) {
            message = result.errorMessage();
        } else if (!result.warnings().isEmpty()) {
            List<String> warnings = result.warnings();
            message = warnings.size() + " warning(s): "
                    + String.join("; ", warnings.subList(0, Math.min(MAX_WARNINGS_IN_MESSAGE, warnings.size())))
                    + (warnings.size() > MAX_WARNINGS_IN_MESSAGE ? "; ..." : "");
        } else {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH - 3) + "...";
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
