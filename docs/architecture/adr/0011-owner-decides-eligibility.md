# ADR-0011: The app checks eligibility criteria; the owner decides

Status: Accepted · Date: 2026-10-19 · Amends: ADR-0004 (step 3 and the scoring condition), ADR-0010 (score condition)

## Context
The evaluator in scoring-spec §4 rolled the per-rule results into one verdict
(ELIGIBLE / UNCERTAIN / INELIGIBLE), and that verdict hid jobs and blocked scoring. Many
postings are ambiguous ("full-time" templates that hire contractors, regions not stated), and
the owner often knows better than a rule. A wrong automatic INELIGIBLE silently loses a job.

## Decision
1. The evaluator returns one check per criterion: `{code, critical, result: OK | UNKNOWN | FAIL,
   reason, value}`. It stores and returns **no overall verdict**.
2. The owner sets a decision per job and profile: `ELIGIBLE`, `INELIGIBLE` or none (undecided).
   It is stored in `job_tracking.eligibility_decision`, logged as a `job_tracking_event` of kind
   `ELIGIBILITY`, and can be changed or cleared at any time (Undo in the UI).
3. The decision can be set before analysis (from the description alone).
4. Score is allowed for an analyzed job whose decision is not `INELIGIBLE`.
5. The default board view hides jobs marked `INELIGIBLE`. Jobs with UNKNOWN or FAIL checks stay
   visible and show "Check N criteria" until the owner decides.
6. Profile changes re-evaluate the checks (no LLM) and keep the owner's decisions.

## Alternatives considered
- Keep the verdict and add an owner override — two sources of truth for one fact, and the
  verdict still hides jobs before the owner sees them.
- Verdict only for FAIL on critical rules — still an automatic decision on ambiguous text.

## Consequences
- The board shows more jobs with failed checks; filters `checks` and `decision` keep it manageable.
- The scoring prompt input changes from "result + reasons" to the list of checks. score-v1 is
  already released (T-012), so the change is a new version, score-v2; it becomes the default
  after the eval run (AGENTS.md).
- The eval keeps its ELIGIBLE/UNCERTAIN/INELIGIBLE labels and measures check quality through a
  rollup that only the eval uses (scoring-spec §7).
- Schema: T-011 and T-014 already shipped the verdict model (V3/V4). T-025 adds a new migration:
  `job_assessment.eligibility` and `eligibility_reasons` are replaced by `eligibility_checks`;
  `job_tracking` gains the decision columns and `job_tracking_event` gains `kind`.
- Existing verdicts are not migrated into owner decisions: the app never decided for the owner,
  so every job starts undecided; checks are re-evaluated from the stored extractions (no LLM).
