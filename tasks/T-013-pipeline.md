# T-013: Pipeline orchestration, scheduling, expiry

Phase: 1 · Depends on: T-012 · Size: M

## Goal
One `PipelineOrchestrator.runOnce()` executes the full flow of overview.md "Pipeline run",
on a schedule, with catch-up on startup, a lock against concurrent runs, and job expiry.

## Read first
- `docs/architecture/overview.md` (Pipeline run, Scheduling) · config-spec §2

## In scope
- `PipelineOrchestrator.runOnce(Trigger)` → `PipelineReport` (per-source counts, prefilter
  pass/reject counts, extractions, eligibility counts, scores, LLM calls/cost, duration,
  budget-stopped flag). Steps:
  1. collect + ingest each enabled collector (sequential, isolated failures);
  2. `reassessPrefilter` if profile hash changed;
  3. prefilter new/unassessed ACTIVE jobs;
  4. NO LLM work here: extraction and scoring are owner actions per job (ADR-0010).
     The report counts `NOT_ANALYZED` jobs per profile instead;
  5. (reserved; no step)
  6. expiry: `job.status = EXPIRED` where `last_seen_at` < now − `app.jobs.expire-after-days`
     (ATS jobs are refreshed every run, board jobs may simply drop off the feed — same rule);
  7. publish `RunCompleted(newJobIds)` event for notify (T-015): digest data only.
- Postgres advisory lock (`pg_try_advisory_lock`) — a second concurrent run returns
  "already running".
- `pipeline_run` is not a table: keep the last 20 `PipelineReport`s in memory and log each
  report as one INFO line + DEBUG details. (Admin page shows them.)
- `@Scheduled(cron = "${app.pipeline.cron}", zone = "${app.pipeline.zone}")`.
- Startup catch-up: after `ApplicationReadyEvent`, if no SUCCESS `collector_run` within
  `catch-up-after`, run once (async).
- `POST /api/admin/pipeline/run` per api-spec: starts the run asynchronously (single-thread
  executor) → 202, or 409 `PIPELINE_ALREADY_RUNNING`. Expose `isRunning()` and the last 20
  reports for `GET /api/admin/overview` (endpoint itself is built in T-014).
  `POST /api/admin/collect/{sourceCode}` from T-005 stays as a debug endpoint.

## Acceptance criteria
- [ ] A run over 1 000 new jobs makes 0 LLM calls (ADR-0010). An ArchUnit rule or test
      confirms the pipeline has no path to `ExtractionService` or `ScoringService`.
- [ ] A collector throwing/hanging does not stop the others; report shows it as FAILED.
- [ ] Expired jobs are excluded from the board and from analysis actions.

## Tests
`PipelineOrchestratorIT` end-to-end with WireMock fixtures for two sources + FakeLlmClient
(first run with 0 LLM calls, failing source, profile change, expiry with fixed Clock),
`PipelineLockIT`.
