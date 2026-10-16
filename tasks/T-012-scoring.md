# T-012: Scoring

Phase: 1 · Depends on: T-011 · Size: M

## Goal
ELIGIBLE and UNCERTAIN jobs get a 0–100 score with breakdown, strengths, gaps, red flags,
summary and apply advice, stored in `job_assessment`.

## Read first
- `docs/ai/scoring-spec.md` §5, §6 · `docs/ai/prompts/score-v1.md`
- T-002 (`candidateBlock()`)

## In scope
- `ScoreResult` record + validation (ranges; lists ≤ 3; summary ≤ 200 chars — truncate
  rather than fail for text length).
- Sum check: if `score` ≠ sum of breakdown, use the sum, log WARN.
- `ScoringService.score(jobId, profile)`: requires OK extraction and eligibility in
  {ELIGIBLE, UNCERTAIN}; variables: `summaryLanguage`, `candidateBlock`, title, company,
  `extractionJson` (pretty JSON of attributes), eligibility + reasons (one per line),
  description truncated to 8 000 chars. Stores via `AssessmentService.recordScore(...)`.
  Honors `BudgetGuard`.
- As in T-011, a pure `computeScore(JobView, JobAttributes, EligibilityResult, Profile)`
  without DB writes, wrapped by `score(...)` (reused by the eval).
- `ScoringService.rescoreActive(profile)`: returns the count first (dry run) and executes
  only when confirmed (used by admin page).

## Acceptance criteria
- [ ] INELIGIBLE or prefilter-rejected jobs are never sent to the LLM.
- [ ] Scoring the same job/profile/prompt version twice makes no second call.
- [ ] Summary language follows the profile (`vi` in the example profile).

## Tests
`ScoreResultValidationTest`, `ScoringServiceIT` (FakeLlmClient: valid, wrong sum, invalid JSON,
budget exhausted, ineligible skipped), `ScoringPromptRenderingTest` (all placeholders filled,
no contact data in candidate block).
