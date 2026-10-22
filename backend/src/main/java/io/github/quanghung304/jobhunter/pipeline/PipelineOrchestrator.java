package io.github.quanghung304.jobhunter.pipeline;

import io.github.quanghung304.jobhunter.common.ProgressBoard;
import io.github.quanghung304.jobhunter.common.ProgressBoard.Stage;
import io.github.quanghung304.jobhunter.common.RunCompleted;
import io.github.quanghung304.jobhunter.enrich.AssessmentService;
import io.github.quanghung304.jobhunter.enrich.PrefilterSummary;
import io.github.quanghung304.jobhunter.ingest.IngestResult;
import io.github.quanghung304.jobhunter.ingest.IngestService;
import io.github.quanghung304.jobhunter.ingest.JobExpiryService;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.llm.BudgetGuard;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.github.quanghung304.jobhunter.source.CollectorRegistry;
import io.github.quanghung304.jobhunter.source.CollectorRunOutcome;
import io.github.quanghung304.jobhunter.source.CollectorRunner;
import io.github.quanghung304.jobhunter.source.JobCollector;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * One pipeline pass (overview.md "Pipeline run"): collect + ingest every enabled source
 * (isolated failures), re-evaluate the prefilter after a profile change, prefilter ACTIVE jobs,
 * expire jobs not seen for {@code app.jobs.expire-after-days}, publish {@link RunCompleted}.
 * It makes <b>no LLM calls</b> (ADR-0010; an ArchUnit rule keeps the LLM services out of reach).
 * One run at a time: an in-process flag plus a Postgres advisory lock.
 */
@Service
public class PipelineOrchestrator {

    static final int REPORTS_KEPT = 20;
    static final int JOB_BATCH_SIZE = 1_000;
    /** Progress percent at the end of the collect and prefilter steps. */
    static final int COLLECT_PERCENT = 80;
    static final int PREFILTER_PERCENT = 95;

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final CollectorRegistry collectors;
    private final CollectorRunner runner;
    private final IngestService ingest;
    private final JobQueryService jobs;
    private final JobExpiryService expiry;
    private final AssessmentService assessments;
    private final ProfileProvider profiles;
    private final BudgetGuard budget;
    private final PipelineLock lock;
    private final ProgressBoard progressBoard;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int expireAfterDays;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong runNumbers = new AtomicLong();
    private final Deque<PipelineReport> reports = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pipeline");
        thread.setDaemon(true);
        return thread;
    });

    public PipelineOrchestrator(CollectorRegistry collectors, CollectorRunner runner, IngestService ingest,
            JobQueryService jobs, JobExpiryService expiry, AssessmentService assessments, ProfileProvider profiles,
            BudgetGuard budget, PipelineLock lock, ProgressBoard progressBoard, ApplicationEventPublisher events,
            Clock clock,
            @Value("${app.jobs.expire-after-days:14}") int expireAfterDays) {
        this.collectors = collectors;
        this.runner = runner;
        this.ingest = ingest;
        this.jobs = jobs;
        this.expiry = expiry;
        this.assessments = assessments;
        this.profiles = profiles;
        this.budget = budget;
        this.lock = lock;
        this.progressBoard = progressBoard;
        this.events = events;
        this.clock = clock;
        this.expireAfterDays = expireAfterDays;
    }

    /** Runs now in the calling thread. */
    public PipelineReport runOnce(Trigger trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new PipelineAlreadyRunningException();
        }
        try {
            return runLocked(trigger);
        } finally {
            running.set(false);
        }
    }

    /** Starts a run on the pipeline thread (admin button, schedule, catch-up). */
    public void startAsync(Trigger trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new PipelineAlreadyRunningException();
        }
        try {
            executor.submit(() -> {
                try {
                    runLocked(trigger);
                } catch (PipelineAlreadyRunningException e) {
                    log.info("pipeline {} run skipped: another process holds the lock", trigger);
                } catch (RuntimeException e) {
                    log.error("pipeline {} run crashed", trigger, e);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /** The last {@value #REPORTS_KEPT} reports, newest first. */
    public synchronized List<PipelineReport> recentReports() {
        return List.copyOf(reports);
    }

    public Optional<PipelineReport> lastReport() {
        return recentReports().stream().findFirst();
    }

    private PipelineReport runLocked(Trigger trigger) {
        try (PipelineLock.Handle ignored = lock.tryAcquire().orElseThrow(PipelineAlreadyRunningException::new);
                ProgressBoard.Activity progress = progressBoard.begin(Stage.COLLECT, "Starting")) {
            PipelineReport report = run(trigger, progress);
            remember(report);
            log.info("pipeline run={} trigger={} status={} sources={} newJobs={} prefilter={}/{} reassessed={}"
                            + " notAnalyzed={} expired={} llmCalls={} ms={}", report.runNumber(), trigger,
                    report.status(), report.sources().size(), report.newJobs(), report.prefilterPassed(),
                    report.prefilterEvaluated(), report.reassessed(), report.notAnalyzed(), report.expired(),
                    report.llmCalls(), report.duration().toMillis());
            report.sources().forEach(s -> log.debug("pipeline run={} {}", report.runNumber(), s));
            return report;
        }
    }

    private PipelineReport run(Trigger trigger, ProgressBoard.Activity progress) {
        long number = runNumbers.incrementAndGet();
        Instant started = clock.instant();
        Profile profile = profiles.current();
        String profileHash = profiles.profileHash();
        List<SourceReport> sources = collectAll(started.minus(Duration.ofDays(profile.prefilter().maxAgeDays())),
                progress);

        int reassessed = 0;
        PrefilterSummary prefilter = new PrefilterSummary(0, 0, 0, 0);
        int expired = 0;
        int notAnalyzed = 0;
        List<Long> newJobIds = List.of();
        String error = null;
        try {
            progress.update(Stage.PREFILTER, "Prefilter", COLLECT_PERCENT);
            reassessed = assessments.reassessPrefilter(profile, profileHash).jobs();
            prefilter = assessments.prefilterJobs(activeJobs(), profile, profileHash);
            progress.update(Stage.EXPIRE, "Expire jobs not seen for " + expireAfterDays + " days", PREFILTER_PERCENT);
            expired = expiry.expireNotSeenSince(started.minus(Duration.ofDays(expireAfterDays)));
            notAnalyzed = assessments.countNotAnalyzed(jobs.activeJobIds(), profile.id());
            newJobIds = jobs.idsFirstSeenSince(started);
        } catch (RuntimeException e) {
            log.error("pipeline run={} failed after collecting", number, e);
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        Instant finished = clock.instant();
        PipelineReport report = new PipelineReport(number, trigger, started, finished, sources, newJobIds.size(),
                reassessed, prefilter.evaluated(), prefilter.passed(), prefilter.rejected(), notAnalyzed, expired,
                budget.callsSince(started), error);
        events.publishEvent(new RunCompleted(started, finished, newJobIds));
        return report;
    }

    /** Every enabled collector in turn; one failing (or ingest failing for it) never stops the others. */
    private List<SourceReport> collectAll(Instant since, ProgressBoard.Activity progress) {
        List<SourceReport> result = new ArrayList<>();
        List<JobCollector> enabled = collectors.enabled();
        for (JobCollector collector : enabled) {
            String code = collector.sourceCode();
            progress.update(Stage.COLLECT, "Collect " + (result.size() + 1) + "/" + enabled.size() + " sources · " + code,
                    result.size() * COLLECT_PERCENT / Math.max(1, enabled.size()));
            try {
                CollectorRunOutcome outcome = runner.run(collector, since);
                if (outcome.skipped()) {
                    result.add(new SourceReport(code, null, 0, 0, 0, 0, outcome.skipReason()));
                    continue;
                }
                CollectResult collected = outcome.result();
                IngestResult ingested = ingest.ingest(collected.postings());
                runner.recordIngestCounts(outcome.runId(), ingested.newRaw(), ingested.newJobs());
                result.add(new SourceReport(code, collected.status(), collected.fetched(), ingested.newRaw(),
                        ingested.newJobs(), ingested.updatedJobs(), collected.errorMessage()));
            } catch (RuntimeException e) {
                log.warn("pipeline source={} failed", code, e);
                result.add(new SourceReport(code, CollectStatus.FAILED, 0, 0, 0, 0,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return result;
    }

    private List<JobView> activeJobs() {
        List<Long> ids = jobs.activeJobIds();
        List<JobView> views = new ArrayList<>(ids.size());
        for (int from = 0; from < ids.size(); from += JOB_BATCH_SIZE) {
            views.addAll(jobs.findAll(ids.subList(from, Math.min(from + JOB_BATCH_SIZE, ids.size()))));
        }
        return views;
    }

    private synchronized void remember(PipelineReport report) {
        reports.addFirst(report);
        while (reports.size() > REPORTS_KEPT) {
            reports.removeLast();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
