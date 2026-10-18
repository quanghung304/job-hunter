# T-023: LLM quota banner and model switcher — frontend

Phase: 1 · Depends on: T-022, T-019, T-021 · Size: S–M

## Goal
From any page the owner sees when LLM work is paused or near a limit, and can switch the
model for extraction or scoring in two clicks.

## Read first
- frontend.md "LLM alert banner", "/admin › LLM models", Shared components · api-spec
  "LLM models", `GET /api/status`, `LlmAlert` · ADR-0008

## In scope
- Extend `useStatus()` (from T-021): refetch every 10 s while an alert is shown.
- `LlmAlertBanner` under the app header on every page (spec in frontend.md), one row per
  alert, with "Switch model" (opens `SwitchModelDialog` preset to that purpose) and a
  "Details" link to `/admin#llm`. Dismiss hides it for the current alert (same model,
  status and `until`) in `sessionStorage` (try/catch).
- `LlmModelsCard` on `/admin` (anchor `#llm`): assignments per purpose with model, status
  tag, today's usage vs limit meter, auto-fallback toggles; models table; switch history.
- `SwitchModelDialog`: radio list of models (label, provider, tier tag FREE/PAID/LOCAL,
  status, usage today/limit, last eval summary), unusable models disabled with the reason,
  PAID confirmation line, Save →
  `PUT /api/admin/llm/assignments/{purpose}`; success toast "Extraction now uses …".
- "Test" button per model → `POST …/test`, result toast.
- MSW handlers for the new endpoints.

## Acceptance criteria
- [ ] With an EXHAUSTED EXTRACT model, every page shows the banner; switching from the banner
      updates the assignment and the banner disappears after the next status poll.
- [ ] PAID models show the cost warning and need an explicit confirmation before saving or
      "Test" (ADR-0009); with paid calls disabled they are not selectable and the dialog says
      how to enable them (`LLM_ALLOW_PAID_CALLS`).
- [ ] ERROR / EXHAUSTED models cannot be selected; the reason is visible.
- [ ] Matches frontend.md specs; readable without color.

## Tests
`LlmAlertBanner.test.tsx` (render per status, dismiss per alert identity),
`SwitchModelDialog.test.tsx` (disabled options, PAID warning, error 409),
`LlmModelsCard.test.tsx`, one Playwright flow: fake 429 → banner → switch → banner gone
(backend `fake` model can simulate quota via a test-only config flag).
