# T-021: Frontend — admin page

Phase: 1 · Depends on: T-014, T-019 · Size: S–M

## Goal
One page to see whether the system is healthy and to trigger maintenance actions.

## Read first
- `docs/architecture/frontend.md` (Admin) · `api-spec.md` (Admin) · PRD F2.6

## In scope
- Hooks: `useAdminOverview()` (refetch every 3 s while `pipelineRunning`, else every 60 s),
  `useRunPipeline()`, `useRescore()` (dry run → confirm dialog → execute), `useRetry()`,
  `useCompanies()`, `useEvalExport()`.
- Layout and components exactly per frontend.md "/admin": title row with the three
  actions, `PipelineBanner` (uses `pipelineProgress`), `KpiRow` (uses `kpis`),
  `SourcesTable` (status tag + text, long errors truncated with full text in `title`),
  `LlmUsageCard` (meter + 14-day CSS bars, no chart library), `RunsList`, `FailuresList`
  with Retry, `CompaniesTable`, `RescoreDialog` (shows `jobCount` and `estimatedCostUsd`),
  Export for labeling (toast with path and count).
- App header status line reads from `GET /api/status` via `useStatus()` (finish what T-019
  stubbed). The LLM banner and LLM models card come in T-023.

## Acceptance criteria
- [ ] Run button disabled while running; 409 shows "already running" toast.
- [ ] Re-score never executes without the confirm step showing the job count, the SCORE model
      and the estimated cost; a PAID model is called out explicitly (ADR-0009).
- [ ] Every status is readable without color.
- [ ] Matches the "Admin · desktop" mockup at 1440 px; tables scroll horizontally on mobile.

## Tests
`AdminPage.test.tsx` (MSW: overview render, run → polling → done, 409, rescore confirm flow,
retry), `LlmUsageCard.test.tsx`. Playwright smoke flow 3 from testing.md.
