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
- `TrackingService.changeStatus(jobId, to, note)` (validates transition, writes event) and
  `updateNotes`.
- Controllers: `JobController`, `MetaController`, `AdminController` (overview, pipeline run,
  collect, rescore dry-run/execute, retry, companies, eval export stub returning 501 until T-016).
- Response DTOs with accurate OpenAPI annotations (required fields, enums, nullability).
- `scoreBand` computed in one place (shared with Telegram formatting).
- Regenerate `docs/architecture/openapi.json`.

## Out of scope
Any frontend code. Auth.

## Acceptance criteria
- [ ] Every endpoint in api-spec returns the documented shape; errors are Problem Details
      with the documented `code`.
- [ ] Illegal transition → 409 `ILLEGAL_STATUS_TRANSITION`; unknown job → 404 `JOB_NOT_FOUND`.
- [ ] Default view excludes DISMISSED, INELIGIBLE, EXPIRED and score < 50.
- [ ] Queries filter by `profile_id` (test with two profiles' rows in the DB).
- [ ] `openapi.json` committed and `OpenApiExportIT` passes.

## Tests
`JobBoardQueryIT` (each filter, sorts, pagination, default view, profile isolation),
`JobDetailQueryIT`, `TrackingServiceIT`, `JobControllerTest`, `AdminControllerTest`,
`MetaControllerTest` (`@WebMvcTest`).

## Docs to update
`api-spec.md` if anything changed (must match `openapi.json`). progress.
