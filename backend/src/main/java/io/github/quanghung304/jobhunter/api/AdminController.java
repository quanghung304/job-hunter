package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.ingest.IngestResult;
import io.github.quanghung304.jobhunter.ingest.IngestService;
import io.github.quanghung304.jobhunter.pipeline.PipelineOrchestrator;
import io.github.quanghung304.jobhunter.pipeline.Trigger;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import io.github.quanghung304.jobhunter.scoring.BulkActionRunner;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectorRegistry;
import io.github.quanghung304.jobhunter.source.CollectorRunOutcome;
import io.github.quanghung304.jobhunter.source.CollectorRunner;
import io.github.quanghung304.jobhunter.source.JobCollector;
import io.github.quanghung304.jobhunter.source.UnknownSourceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final BulkActionRunner bulkActions;
    private final AdminViews views;

    public AdminController(CollectorRegistry registry, CollectorRunner runner, IngestService ingestService,
            ProfileProvider profileProvider, Clock clock, PipelineOrchestrator pipeline, BulkActionRunner bulkActions,
            AdminViews views) {
        this.registry = registry;
        this.runner = runner;
        this.ingestService = ingestService;
        this.profileProvider = profileProvider;
        this.clock = clock;
        this.pipeline = pipeline;
        this.bulkActions = bulkActions;
        this.views = views;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return views.overview();
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

    @Operation(summary = "Bulk Analyze of selected jobs (ADR-0010)",
            description = "dryRun=true answers 200 with the counts; dryRun=false starts the run (202). "
                    + "Errors: 400 TOO_MANY_JOBS, 409 PAID_CALLS_DISABLED, 409 LLM_PAUSED, 409 BUDGET_REACHED.")
    @ApiResponse(responseCode = "200", description = "Dry run",
            content = @Content(schema = @Schema(implementation = BulkAnalyzeResponse.class)))
    @ApiResponse(responseCode = "202", description = "Run started; progress in the overview (stage ANALYZE)",
            content = @Content(schema = @Schema(implementation = AcceptedResponse.class)))
    @PostMapping(path = "/jobs/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> analyzeJobs(@Valid @RequestBody BulkJobsRequest request) {
        var plan = BulkAnalyzeResponse.of(bulkActions.analyze(request.jobIds(), request.dryRun()));
        return request.dryRun() ? ResponseEntity.ok(plan) : accepted();
    }

    @Operation(summary = "Bulk Score of selected jobs (ADR-0010)",
            description = "dryRun=true answers 200 with the counts, model and cost; dryRun=false starts the run (202). "
                    + "Errors: 400 TOO_MANY_JOBS, 409 PAID_CALLS_DISABLED, 409 LLM_PAUSED, 409 BUDGET_REACHED.")
    @ApiResponse(responseCode = "200", description = "Dry run",
            content = @Content(schema = @Schema(implementation = BulkScoreResponse.class)))
    @ApiResponse(responseCode = "202", description = "Run started; progress in the overview (stage SCORE)",
            content = @Content(schema = @Schema(implementation = AcceptedResponse.class)))
    @PostMapping(path = "/jobs/score", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> scoreJobs(@Valid @RequestBody BulkJobsRequest request) {
        var plan = BulkScoreResponse.of(
                bulkActions.score(request.jobIds(), profileProvider.current(), request.dryRun()));
        return request.dryRun() ? ResponseEntity.ok(plan) : accepted();
    }

    @Operation(summary = "Re-score active jobs (ADR-0010)",
            description = "Always run the dry run first and execute only after the owner confirms the count. "
                    + "Errors: 409 PAID_CALLS_DISABLED, 409 LLM_PAUSED, 409 BUDGET_REACHED.")
    @ApiResponse(responseCode = "200", description = "Dry run",
            content = @Content(schema = @Schema(implementation = RescoreResponse.class)))
    @ApiResponse(responseCode = "202", description = "Run started; progress in the overview (stage SCORE)",
            content = @Content(schema = @Schema(implementation = AcceptedResponse.class)))
    @PostMapping(path = "/rescore", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> rescore(@Valid @RequestBody DryRunRequest request) {
        var plan = RescoreResponse.of(bulkActions.rescore(profileProvider.current(), request.dryRun()));
        return request.dryRun() ? ResponseEntity.ok(plan) : accepted();
    }

    @Operation(summary = "Retry failed LLM work for one job",
            description = "Resets the attempts for the current prompt version and runs Analyze or Score in the "
                    + "background. 404 JOB_NOT_FOUND.")
    @PostMapping(path = "/jobs/{id}/retry", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcceptedResponse retry(@PathVariable long id, @Valid @RequestBody RetryRequest request) {
        bulkActions.retry(id, request.purpose().llmPurpose(), profileProvider.current());
        return new AcceptedResponse(true);
    }

    @GetMapping("/companies")
    public List<CompanyResponse> companies() {
        return views.companies();
    }

    @Operation(summary = "Export eval candidates for labeling (T-016)",
            description = "Not implemented yet: 501 NOT_IMPLEMENTED until T-016.")
    @ApiResponse(responseCode = "501", description = "Not implemented yet",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @PostMapping("/eval/export")
    public EvalExportResponse exportEval() {
        throw new NotImplementedException("The eval export comes with T-016");
    }

    private static ResponseEntity<Object> accepted() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AcceptedResponse(true));
    }
}
