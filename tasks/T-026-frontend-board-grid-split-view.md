# T-026: Frontend — board grid, split view and eligibility decision

Phase: 1 · Depends on: T-020, T-025 · Size: M–L

## Goal
The board becomes a 12-card grid with numbered pages; opening a job shows a split view with an
independently scrolling job list and detail; every card shows its status, analysis state and the
owner's eligibility decision; the owner decides eligibility in the detail (D1–D5, ADR-0011).
Reworks the list-and-pane board that T-020 shipped.

## Read first
- `frontend.md` "`/` — Board grid", "`/jobs/:id` — split view", "JobDetail content order",
  "Behavior › Board", color tokens · `api-spec.md` "Jobs" · data-model §2, §2.1
- Mockup artboards "Board grid → job detail · desktop", "Board · mobile", "Job detail · mobile"
  (the last one predates the decision control: follow frontend.md)

## In scope
- Hooks: `useJobs(filters, page)` (paged, size 12), `useInfiniteJobs(filters)` (list panel,
  size 12, seeded with pages 1…`page`), `useJob(id)`, `useChangeStatus()`, `useSetDecision()`
  (both optimistic with rollback + toast, Undo), `useUpdateNotes()`.
- Tokens from frontend.md (new and changed values) in `tokens.css` and the Tailwind theme.
- Board grid: `FilterBar` (Status, Analysis, Decision, Checks, Score, Source, Type, Posted),
  `StatusLegend`, `LayoutToggle` (Grid/List in `localStorage` `board.layout`), `BoardGrid` /
  `JobCard`, list layout with `JobListCard`, `Pagination` (`page` in the URL, 1-based).
  Remove the density toggle.
- Split view: `JobSplitView`, `JobListPanel` (own scroll, "Load 12 more" appends without moving
  the scroll position, seeded with pages 1…page, selected card scrolled into view),
  `JobDetailPanel` (own scroll, sticky header, scroll reset when the job changes).
- `JobDetail` in the new content order incl. `EligibilityPanel` (checks table) +
  `EligibilityDecision` (segmented control).
- Tags: `StatusTag`, `AnalysisTag`, `DecisionTag`, `ChecksTag` with the token colors, each with an
  icon and a word; remove `EligibilityTag`.
- `AnalysisActions` only in the detail action row and the mobile action bar; Score unless marked
  Not eligible.
- Keyboard incl. `e`, `n`, `Esc` back to the grid.
- Mobile: one column of `JobListCard`s; the detail page gets `EligibilityDecision`.

## Out of scope
Admin page (T-021). Profile page (T-028). Editing anything except status, decision and notes.

## Acceptance criteria
- [ ] Dismissing a job removes it from the default board and it stays gone on reload.
- [ ] "Load more" in the list panel never changes its scroll position.
- [ ] Opening a job from grid page 3 shows pages 1–3 in the list panel with the job in view.
- [ ] Every status, analysis and decision tag uses its token colors and shows an icon and a word.
- [ ] Marking Not eligible can be undone; after refetch the job leaves the default view.
- [ ] Card hover/selected effects are off with reduced motion.
- [ ] Clicking "Analyze" sends exactly one request; no LLM request runs on page or list load.
- [ ] A failed change (409) restores the previous state and shows the server message.
- [ ] Filters, `page` and the selected job survive reload and can be bookmarked.
- [ ] Keyboard flow works and is ignored while typing in inputs.
- [ ] Desktop matches "Board grid → job detail · desktop"; mobile matches "Board · mobile" (owner
      checks visually at 1440 and 390 px). Exactly one primary button per view; mobile touch
      targets ≥ 44px.

## Tests
Update the T-020 tests (`BoardPage`, `JobDetailPage`, `useBoardFilters` incl. `page`), and add
`JobCard.test.tsx`, `Pagination.test.tsx`, `JobListPanel.test.tsx` (appends without changing
`scrollTop`), `EligibilityDecision.test.tsx`. Playwright flows 1, 2 and 4 (testing.md).

## Docs to update
`frontend.md` if anything changed; progress.
