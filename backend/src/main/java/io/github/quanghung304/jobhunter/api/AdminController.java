package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.ingest.IngestResult;
import io.github.quanghung304.jobhunter.ingest.IngestService;
import io.github.quanghung304.jobhunter.pipeline.PipelineOrchestrator;
import io.github.quanghung304.jobhunter.pipeline.Trigger;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final PipelineOrchestrator pipeline;

    public AdminController(CollectorRegistry registry, CollectorRunner runner, IngestService ingestService,
            ProfileProvider profileProvider, Clock clock, PipelineOrchestrator pipeline) {
        this.registry = registry;
        this.runner = runner;
        this.ingestService = ingestService;
        this.profileProvider = profileProvider;
        this.clock = clock;
        this.pipeline = pipeline;
    }

    /**
     * Starts one pipeline pass asynchronously (collect, ingest, prefilter, expiry; no LLM calls,
     * ADR-0010). 409 {@code PIPELINE_ALREADY_RUNNING} while a run is in progress.
     */
    @PostMapping("/pipeline/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcceptedResponse runPipeline() {
        pipeline.startAsync(Trigger.MANUAL);
        return new AcceptedResponse(true);
    }

    /**
     * Runs one collector now (ignoring min-interval and the enabled flag), ingests its postings
     * and records the ingest counts. Postings older than {@code prefilter.maxAgeDays} are not
     * collected. A debug endpoint for one source; the full pass is {@code POST /pipeline/run}.
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
