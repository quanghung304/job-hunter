package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.ingest.IngestResult;
import io.github.quanghung304.jobhunter.ingest.IngestService;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectorRegistry;
import io.github.quanghung304.jobhunter.source.CollectorRunOutcome;
import io.github.quanghung304.jobhunter.source.CollectorRunner;
import io.github.quanghung304.jobhunter.source.JobCollector;
import io.github.quanghung304.jobhunter.source.UnknownSourceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin operations (api-spec.md, "Admin"). */
@RestController
@RequestMapping(path = "/api/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminController {

    private final CollectorRegistry registry;
    private final CollectorRunner runner;
    private final IngestService ingestService;
    private final ProfileProvider profileProvider;
    private final Clock clock;

    public AdminController(CollectorRegistry registry, CollectorRunner runner, IngestService ingestService,
            ProfileProvider profileProvider, Clock clock) {
        this.registry = registry;
        this.runner = runner;
        this.ingestService = ingestService;
        this.profileProvider = profileProvider;
        this.clock = clock;
    }

    /**
     * Runs one collector now (ignoring min-interval and the enabled flag), ingests its postings
     * and records the ingest counts. Postings older than {@code prefilter.maxAgeDays} are not
     * collected. TODO(T-013): move this sequence into the pipeline module.
     */
    @PostMapping("/collect/{sourceCode}")
    public CollectRunResponse collect(@PathVariable String sourceCode) {
        JobCollector collector = registry.find(sourceCode).orElseThrow(() -> new UnknownSourceException(sourceCode));
        Instant since = clock.instant().minus(Duration.ofDays(profileProvider.current().prefilter().maxAgeDays()));

        CollectorRunOutcome outcome = runner.runNow(collector, runner.defaultRequest(sourceCode, since));
        CollectResult collected = outcome.result();
        IngestResult ingested = ingestService.ingest(collected.postings());
        runner.recordIngestCounts(outcome.runId(), ingested.newRaw(), ingested.newJobs());

        return new CollectRunResponse(outcome.runId(), collected.status(), collected.fetched(), ingested.newRaw(),
                ingested.newJobs(), ingested.updatedJobs(), ingested.skippedInvalid(), collected.warnings(),
                collected.errorMessage());
    }
}
