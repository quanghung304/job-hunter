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
- `PipelinePanel` (run button, running indicator, last report summary),
  `SourcesTable` (status color + text, last error expandable), `LlmUsageCard`
  (month-to-date vs budget meter; 14-day cost bars — a small SVG/CSS bar list is enough, no
  chart library), `FailuresTable` with Retry, `CompaniesTable`, Re-score dialog,
  Export for labeling button (shows path and count).

## Acceptance criteria
- [ ] Run button disabled while running; 409 shows "already running" toast.
- [ ] Re-score never executes without the confirm step showing the job count.
- [ ] Every status is readable without color.

## Tests
`AdminPage.test.tsx` (MSW: overview render, run → polling → done, 409, rescore confirm flow,
retry), `LlmUsageCard.test.tsx`. Playwright smoke flow 3 from testing.md.
