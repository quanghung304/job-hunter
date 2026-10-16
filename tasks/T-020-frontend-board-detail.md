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
- Board: `FilterBar` (URL search params ⇄ filters, debounced search, reset), `JobRow`,
  `ScoreBadge`, `EligibilityChips`, `StatusActions`, `SourceLinks`, load more,
  keyboard shortcuts per frontend.md.
- Job detail page with all panels in frontend.md; notes autosave (1 s debounce, "Saved" hint);
  `allowedTransitions` from the server drives the buttons.
- Responsive down to 360 px.

## Out of scope
Admin page (T-021). Editing anything except status and notes.

## Acceptance criteria
- [ ] Dismissing a job removes it from the default board immediately and it stays gone on reload.
- [ ] A failed status change (409) restores the previous state and shows the server message.
- [ ] Filters survive reload and can be bookmarked.
- [ ] Keyboard flow works and is ignored while typing in inputs.
- [ ] "via <Source>" links open the original posting in a new tab (`rel="noopener"`).

## Tests
`statusMachine.test.ts` (matches data-model §2 matrix), `useBoardFilters.test.ts`,
`ScoreBadge.test.tsx`, `StatusActions.test.tsx`, `BoardPage.test.tsx` (MSW: load, filter,
optimistic dismiss + rollback, keyboard), `JobDetailPage.test.tsx` (panels, status change,
notes autosave with fake timers). Playwright smoke flows 1 and 2 from testing.md.
