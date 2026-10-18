# T-020: Frontend — board and job detail

Phase: 1 · Depends on: T-014, T-019 · Size: M

## Goal
The owner reviews jobs on the board, opens details, changes status and writes notes,
fast enough to clear ~50 jobs in a few minutes with the keyboard.

## Read first
- `docs/architecture/frontend.md` (Board, Job detail, Rules) · `api-spec.md` (Jobs, Meta)
- data-model §2 · scoring-spec §5 (score bands) · PRD F2.1–F2.4

## In scope
- Hooks: `useJobs(filters)` (infinite), `useJob(id)`, `useChangeStatus()` (optimistic on
  list and detail caches, rollback + toast on error), `useUpdateNotes()`.
- `lib/statusMachine.ts` mirroring data-model §2; `lib/labels.ts` for enums and reason codes
  (prefilter, eligibility) with human-readable English labels; `lib/format.ts`.
- Desktop (≥ 1024px) **split view** per frontend.md "Board": `FilterBar`, `JobList` +
  `JobRow` (shortlist/dismiss icon buttons, selected row, density toggle in localStorage),
  `JobDetailPane`; `/jobs/:id` keeps search params and renders the split view with that job
  selected; sticky keyboard hint footer; load more.
- `JobDetail` with the exact content order in frontend.md "JobDetail content order"
  (`WhyItFits`, `ScoreBreakdown`, `EligibilityPanel`, `FactsPanel`, `DescriptionPanel`,
  `NotesEditor`, `HistoryList`); `allowedTransitions` from the server drives the buttons.
- Mobile (< 1024px) per frontend.md "Mobile": list-only board, horizontal filter pills +
  "Filters · N" bottom sheet, full-page detail with back bar and fixed `MobileActionBar`.
  "Job N of M" uses the cached list; hide it when the page was opened directly.
- Analysis actions (ADR-0010): a row and the detail pane show "Analyze" for `NOT_ANALYZED`
  jobs and "Score" for `ANALYZED` eligible jobs. One click sends one request, with a spinner
  while it runs. When the purpose is paused, the button is disabled and links to the switch
  dialog (T-023). No new keyboard key.
- `useBoardKeyboard` with every key in frontend.md "Behavior › Board" (incl. `o`, `Esc`).
- Undo toast after each status change; row collapse animation (respect reduced motion).

## Out of scope
Admin page (T-021). Editing anything except status and notes.

## Acceptance criteria
- [ ] Dismissing a job removes it from the default board immediately and it stays gone on reload.
- [ ] Clicking "Analyze" sends exactly one request; no LLM request runs on page or list load.
- [ ] A failed status change (409) restores the previous state and shows the server message.
- [ ] Filters survive reload and can be bookmarked.
- [ ] Keyboard flow works and is ignored while typing in inputs.
- [ ] "via <Source>" links open the original posting in a new tab (`rel="noopener"`).
- [ ] Desktop board and job detail match the "Board + job detail · desktop" mockup; mobile
      matches "Board · mobile" and "Job detail · mobile" (owner checks visually at 1440 and 390 px).
- [ ] Exactly one primary button per view; mobile touch targets ≥ 44px.

## Tests
`statusMachine.test.ts` (matches data-model §2 matrix), `useBoardFilters.test.ts`,
`ScoreBadge.test.tsx`, `StatusActions.test.tsx`, `BoardPage.test.tsx` (MSW: load, filter,
optimistic dismiss + rollback, keyboard), `JobDetailPage.test.tsx` (panels, status change,
notes autosave with fake timers). Playwright smoke flows 1 and 2 from testing.md.
