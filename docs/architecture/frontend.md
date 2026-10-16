# Frontend design

React SPA in `frontend/` (ADR-0007). Single user, desktop first, usable at 360 px.

## Stack

| Concern | Choice |
|---|---|
| Build | Vite, TypeScript `strict: true` |
| Routing | React Router (data router not required) |
| Server state | TanStack Query (no Redux/Zustand; local UI state with `useState`) |
| API client | `openapi-fetch` with types from `openapi-typescript` (`src/api/schema.d.ts`, generated) |
| UI | Tailwind CSS + shadcn/ui components (copied into `src/components/ui`), lucide icons |
| Forms | plain controlled inputs (only notes and filters; no form library) |
| Lint/format | ESLint (typescript-eslint, react-hooks), Prettier |
| Tests | Vitest + React Testing Library + MSW; Playwright for 3 smoke flows |

Use current stable major versions at bootstrap time and record them in `progress.md`.

## Structure

```
frontend/
  src/
    api/            client.ts (openapi-fetch instance), schema.d.ts (generated), queries.ts (hooks)
    components/ui/  shadcn components (generated, minimal edits)
    components/     ScoreBadge, StatusActions, EligibilityChips, SourceLinks, EmptyState, ErrorState
    features/board/ BoardPage, FilterBar, JobRow, useBoardFilters (URL search params)
    features/job/   JobDetailPage, ScoreBreakdown, ExtractionPanel, NotesEditor, HistoryList
    features/admin/ AdminPage, SourcesTable, LlmUsageCard, FailuresTable, CompaniesTable, PipelinePanel
    lib/            format.ts (dates, money), labels.ts (enum → label)
    App.tsx, main.tsx, routes.tsx
  e2e/              Playwright specs
```

## Pages

### `/` Board
- Filter bar: status (multi), eligibility, min score slider, source, employment type,
  posted within (3/7/14/30 days), text search (debounced 300 ms). Filters live in URL search
  params so views are bookmarkable; "Reset" returns to the default view (api-spec).
- List rows: score badge (color by band), title, company, one-line summary, chips (remote
  type + regions, employment types), posted relative date, source names, status actions
  (Shortlist / Dismiss / Applied — only those in the server's allowed transitions for the
  row's status; for list rows use the static status machine copy in `lib/statusMachine.ts`,
  kept identical to data-model §2 and covered by a test).
- Optimistic status update with rollback on error (toast with the problem `detail`).
- Keyboard: `j`/`k` move selection, `Enter` open, `s` shortlist, `d` dismiss, `a` applied,
  `/` focus search. Ignore keys while typing in inputs.
- Pagination: "Load more" (infinite query).

### `/jobs/:id` Job detail
- Header: title, company, score badge, status actions, "Open posting" / "Apply" buttons.
- Panels: Why it fits (summary, strengths, gaps, red flags, apply advice), Score breakdown
  (5 bars with max per part), Eligibility (result + reasons, human labels), Extracted facts
  (table), Description (pre-wrapped text, collapsible), Sources ("via X" links), Notes
  (textarea, autosave 1 s after typing stops), History.

### `/admin` Admin
- Run pipeline button (disabled while running; polls overview every 3 s while running).
- Sources table, last pipeline reports, LLM usage (month-to-date vs budget as a meter;
  14-day bar chart of cost), failures with Retry, companies, Re-score (dry run → confirm
  dialog with count), Export for labeling.

## Rules

- All data access through hooks in `src/api/queries.ts`; components never call `fetch`.
- Never hand-edit `schema.d.ts`; run `npm run gen:api`.
- Loading, empty and error states for every query (shared components).
- Accessibility: buttons are `<button>`, labels for inputs, visible focus, color is never
  the only signal (score badge shows the number).
- No external analytics, no fonts/scripts from third-party CDNs (works offline).
- Text in English; job summaries arrive in the profile's language from the backend.
