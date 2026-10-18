# T-014: REST API for board, job detail and admin

Phase: 1 · Depends on: T-013 · Size: M

## Goal
All endpoints in `api-spec.md` exist, are documented in `openapi.json`, and are tested,
so the frontend tasks can build against a stable contract.

## Read first
- `docs/architecture/api-spec.md` (all) · data-model §2 (status machine)
- `docs/conventions.md` (REST API) · ADR-0007

## In scope
- `JobBoardQuery` (in `tracking`): paged list for the current profile joining job, latest
  assessment (current score prompt version), extraction, tracking status; all filters and
  sorts in api-spec; default view when no filters; text search via ILIKE on title/company.
- `JobDetailQuery`: everything `JobDetail` needs, including `allowedTransitions` from
  `TrackingStatusMachine`.
- `POST /api/jobs/{id}/analyze` and `POST /api/jobs/{id}/score` (owner actions, ADR-0010)
  calling `AnalysisService` / `ScoringService`, with the 409 codes in api-spec. Repeat calls
  for the same prompt version make no LLM call. `analysisStage` on `JobSummary`.
- `TrackingService.changeStatus(jobId, to, note)` (validates transition, writes event) and
  `updateNotes`.
- Overview extras used by the UI: `kpis`, `schedule` (next cron fire times), and
  `pipelineProgress` (the orchestrator publishes stage + counters while running);
  re-score dry run returns `estimatedCostUsd`.
- `GET /api/status` (api-spec) with `llmAlerts` always `[]` for now — T-022 fills it.
  `llmAlerts` in the overview likewise `[]`.
- Controllers: `JobController`, `MetaController`, `AdminController` (overview, pipeline run,
  collect, bulk analyze and score of selected jobs (`POST /api/admin/jobs/analyze` and `/score`, dry-run/execute), rescore dry-run/execute, retry, companies, eval export stub returning 501 until T-016).
- Response DTOs with accurate OpenAPI annotations (required fields, enums, nullability).
- `scoreBand` computed in one place (shared with Telegram formatting).
- Regenerate `docs/architecture/openapi.json`.

## Out of scope
Any frontend code. Auth.

## Acceptance criteria
- [ ] Every endpoint in api-spec returns the documented shape; errors are Problem Details
      with the documented `code`.
- [ ] Illegal transition → 409 `ILLEGAL_STATUS_TRANSITION`; unknown job → 404 `JOB_NOT_FOUND`.
- [ ] Default view excludes DISMISSED, INELIGIBLE, EXPIRED and non-PASS prefilter jobs;
      unscored jobs are included after scored ones.
- [ ] Queries filter by `profile_id` (test with two profiles' rows in the DB).
- [ ] `openapi.json` committed and `OpenApiExportIT` passes.

## Tests
`JobBoardQueryIT` (each filter, sorts, pagination, default view, profile isolation),
`JobDetailQueryIT`, `TrackingServiceIT`, `JobControllerTest`, `AdminControllerTest`,
`MetaControllerTest` (`@WebMvcTest`).

## Docs to update
`api-spec.md` if anything changed (must match `openapi.json`). progress.
