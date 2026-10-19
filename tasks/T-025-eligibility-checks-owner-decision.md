# T-025: Eligibility checks and owner decision — backend

Phase: 1 · Depends on: T-011, T-012, T-014 · Size: M–L

## Goal
The evaluator returns one check per criterion and no overall verdict; the owner marks a job
Eligible, Not eligible or leaves it undecided; Score is allowed unless the owner marked the job
Not eligible (ADR-0011). Reworks what T-011, T-012 and T-014 shipped with the verdict model.

## Read first
- ADR-0011 · scoring-spec §1, §4, §5, §7 · data-model `job_assessment`, `job_tracking`,
  `job_tracking_event`, §2.1 · api-spec "Jobs", `POST /api/admin/jobs/score`, `GET /api/meta`
- `docs/ai/prompts/score-v2.md` (draft) and the prompts README
- overview.md module table: `scoring` may now read `tracking` (owner decision); update
  `ArchitectureTest` accordingly.

## In scope
- Migration (next free `V<n>`): add `job_assessment.eligibility_checks jsonb`, drop
  `eligibility` and `eligibility_reasons`; add `job_tracking.eligibility_decision` (check
  `ELIGIBLE`, `INELIGIBLE`) and `eligibility_decided_at`; add `job_tracking_event.kind`
  (`STATUS` default, `ELIGIBILITY`) with check constraints per kind; index
  `job_tracking(profile_id, eligibility_decision)`. Never edit V3/V4.
- Checks for existing rows: after the migration, every row with an OK extraction and no checks is
  evaluated once from the stored attributes (Java, no LLM). No owner decisions are created.
- `EligibilityEvaluator.evaluate(JobAttributes, JobView, Profile)` returns `List<EligibilityCheck>`
  (record `code, critical, result, reason, value`) in §4 table order, including
  `EXTRACTION_CONFIDENCE`. No verdict anywhere (`Eligibility` enum removed from the evaluator
  output). `AssessmentService.recordEligibility(...)` → `recordChecks(...)`.
  `reassessPrefilter` also re-evaluates the checks of rows with an OK extraction (keeps scores);
  T-027 calls it on `ProfileChanged`.
- `TrackingService.setEligibilityDecision(jobId, profileId, decision, note)`: any value to any
  value, also before analysis; the same value again is a no-op (no event); writes a
  `job_tracking_event` of kind `ELIGIBILITY` (`from`/`to` = `ELIGIBLE`, `INELIGIBLE`,
  `UNDECIDED`). Dismissing does not change the decision.
- Scoring: `score(...)` requires an OK extraction and decision ≠ INELIGIBLE (read from
  `tracking`) → otherwise 409 `JOB_MARKED_INELIGIBLE` (replaces `JOB_NOT_ELIGIBLE`). Prompt
  variable `eligibilityChecks` (one line per check: `CODE: RESULT — value (reason)`);
  `computeScore(JobView, JobAttributes, List<EligibilityCheck>, Profile)`. `scoreSelected` skips
  jobs not analyzed, marked ineligible (`markedIneligible`) or already scored; "Re-score active
  jobs" covers scored, analyzed jobs not marked ineligible.
- Prompt: `app.llm.score-prompt` default becomes `score-v2` (application.yml, config-spec,
  prompts README: score-v2 released). Record in `docs/ai/eval/results.md` that the eval for
  score-v2 is pending until T-016 exists; running it needs the owner's consent (ADR-0009).
- API (api-spec): `GET /api/jobs` filters `decision` and `checks` (replace `eligibility`), new
  default view, `JobSummary.eligibilityDecision` and `checks`; `JobDetail.eligibilityChecks`,
  `eligibilityDecidedAt`, history items with `kind`; `PUT /api/jobs/{id}/eligibility`;
  `/api/meta` `decisions` and `checkResults`; bulk score skipped `markedIneligible`;
  `analysisStage` ANALYZED = checks present and not scored. `make api`; keep the frontend
  compiling with the minimal type changes (the UI work is T-026).

## Out of scope
Board grid and decision UI (T-026). Profile saving (T-027). Telegram (T-015 reads the decision).

## Acceptance criteria
- [ ] No API response, table or log contains an overall eligibility verdict.
- [ ] Every check has a result, value and reason as in scoring-spec §4; `EXTRACTION_CONFIDENCE`
      is UNKNOWN for `confidence LOW`.
- [ ] Default view excludes DISMISSED, jobs marked INELIGIBLE, EXPIRED and non-PASS prefilter
      jobs; APPLIED, INTERVIEWING, OFFER, REJECTED and WITHDRAWN are included; unscored jobs come
      after scored ones.
- [ ] Setting the same decision twice writes one event; clearing writes `UNDECIDED`.
- [ ] A job marked Not eligible is never sent to the SCORE model (single, bulk, re-score).
- [ ] After the migration, analyzed jobs have checks again with 0 LLM calls.
- [ ] `make verify` passes; `openapi.json` and `schema.d.ts` regenerated.

## Tests
`EligibilityEvaluatorTest` rewritten as checks (one row per §4 cell): US-only → REGION FAIL;
`[EU]` → REGION OK; `[]` → REGION UNKNOWN; `[FULL_TIME]` only, no hours → EMPLOYMENT_TYPE
UNKNOWN with `FULL_TIME_ONLY_SOFT`; `[FULL_TIME]` + "permanent" → EMPLOYMENT_TYPE FAIL; German C1
required → LANGUAGE FAIL; CET±2 with Rome in summer and winter → TIMEZONE OK; EST overlap 4h
with d=6 → OK; PST-only with d=6 → FAIL; `confidence LOW` → EXTRACTION_CONFIDENCE UNKNOWN; no
verdict produced. Migration IT (old rows get checks, 0 LLM calls). `TrackingServiceIT` decision
cases. `ScoringServiceIT` "marked ineligible skipped". `JobBoardQueryIT` decision/checks filters
and the new default view. Controller tests for `PUT /eligibility` and `JOB_MARKED_INELIGIBLE`.

## Docs to update
data-model (if the migration differs), api-spec, prompts README, config-spec, eval results,
progress.
