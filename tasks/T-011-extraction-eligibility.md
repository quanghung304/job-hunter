# T-011: Extraction + eligibility

Phase: 1 · Depends on: T-009, T-010 · Size: M

## Goal
For jobs that passed the prefilter, the LLM extracts structured attributes once per job and
prompt version, and the deterministic eligibility evaluator classifies them for the profile.

## Read first
- `docs/ai/scoring-spec.md` §3, §4, §6 · `docs/ai/prompts/extract-v1.md`
- config-spec §1.1 · data-model `job_extraction`, `job_assessment`

## In scope
- `JobAttributes` record mirroring §3 exactly, with Bean Validation (enum values, list caps,
  string lengths); unknown JSON fields ignored.
- `ExtractionService.extract(jobId)`: skip if an OK extraction exists for the configured
  prompt version; otherwise build variables (description truncation: first 9 000 + last
  3 000 chars with "[…]" between when longer than 12 000), call `LlmRouter.call(EXTRACT, …)`, store
  `job_extraction` (OK/FAILED, attempts). Honors `BudgetGuard`.
- Split compute from persistence: `ExtractionService.extractAttributes(JobView)` returns
  `JobAttributes` without DB writes (reused by the eval in T-016); `extract(jobId)` wraps it.
- Listener for `JobDescriptionChanged`: delete/invalidate extractions for that job and reset
  its assessments to PENDING.
- `AnalysisService.analyze(jobId)` (owner action, ADR-0010): extraction if needed, then
  eligibility. Called only by the analyze endpoint (T-014), never by the pipeline.
- `AnalysisService.analyzeMany(jobIds, dryRun)` for the admin bulk action (api-spec
  `POST /api/admin/jobs/analyze`): max 200 ids; skips jobs that already have a current
  extraction and expired jobs; stops at budget or pause and reports the rest.
- `EligibilityEvaluator.evaluate(JobAttributes, JobView, Profile)` — pure, implements every
  row in §4 including `FULL_TIME_ONLY_SOFT`, DST-aware timezone check with injected `Clock`,
  language level comparison (`NATIVE` > `C2` > … > `A1`), uses `RegionMatcher` (T-002).
- `AssessmentService.recordEligibility(...)`.

## Out of scope
Scoring (T-012). Pipeline wiring (T-013) — expose service methods only.

## Acceptance criteria
- [ ] Re-running extraction for the same job and version makes no LLM call.
- [ ] Analyzing 3 jobs makes at most 3 EXTRACT calls; the pipeline makes none (ADR-0010).
- [ ] Bulk analysis never sends a job that already has a current extraction.
- [ ] Invalid LLM JSON → one corrective retry → FAILED after max attempts (3 across runs).
- [ ] Every cell of the §4 table has a test row.

## Tests
- `JobAttributesValidationTest`.
- `ExtractionServiceIT` with `FakeLlmClient` (success, invalid then valid, permanent failure,
  budget exhausted, description change invalidation).
- `EligibilityEvaluatorTest` parameterized (≥ 30 rows), including:
  US-only → INELIGIBLE; `[EU]` → ELIGIBLE; `[]` → UNCERTAIN; `[FULL_TIME]` only, no hours →
  UNCERTAIN with `FULL_TIME_ONLY_SOFT`; `[FULL_TIME]` + "permanent" → INELIGIBLE;
  German C1 required → INELIGIBLE; CET±2 with Rome in summer and winter → OK;
  EST overlap 4h with d=6 → OK; PST-only with d=6 → FAIL; `confidence LOW` caps at UNCERTAIN.
