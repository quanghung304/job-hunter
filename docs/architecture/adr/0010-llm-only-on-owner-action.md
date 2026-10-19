# ADR-0010: LLM steps run only when the owner asks, one job at a time

Status: Accepted · Date: 2026-10-18 · Amends: ADR-0004 (trigger only; the steps are unchanged) · Score condition amended by ADR-0011

## Context

The first design ran extraction and scoring for every prefilter-passing job in each pipeline
run. About 1 000 collected jobs would then cost about 1 000 extraction calls and their input
tokens, and would use up free daily quotas (ADR-0008) before the owner had read any of them.

## Decision

1. The pipeline run (collect, ingest, prefilter, expiry, digest) makes **zero** LLM calls.
2. **Analyze**: the owner presses "Analyze" on one job (`POST /api/jobs/{id}/analyze`). At most
   one EXTRACT call, then eligibility in Java (no LLM).
3. **Score**: the owner presses "Score" on one job (`POST /api/jobs/{id}/score`). One SCORE call,
   only if the job is analyzed and the owner has not marked it Not eligible (ADR-0011; was:
   ELIGIBLE or UNCERTAIN).
4. Bulk LLM actions are admin-only. The admin selects jobs in a table (max 200) and runs bulk
   Analyze or bulk Score. "Re-score active jobs" and "Retry failed" are the other two. Each one
   shows the job count and the estimated calls before it runs (dry run first).
5. Results are cached per job and prompt version. Pressing a button again makes no call.
6. The budget guard, quota statuses and banner (ADR-0008) apply to every manual call. A paused
   purpose disables its buttons and links to the model switch dialog.

## Alternatives considered

- Keep automatic extraction, but only for the newest N jobs per run. This still spends quota
  on jobs the owner never opens, and N is a guess.
- Keep automatic scoring, but only for jobs that pass eligibility. Eligibility needs
  extraction first, so the same cost problem remains.

## Consequences

- New jobs wait as `NOT_ANALYZED` on the board. The default view lists scored jobs first and
  unanalyzed jobs after them.
- Telegram: the digest reports the number of new unanalyzed jobs and the scored jobs at or
  above the digest threshold. Instant alerts are removed (owner decision, 2026-10-18).
- Scoring covers only the jobs the owner analyzed. The eval (T-016) is not affected.
- No schema change. `analysisStage` is derived from existing `job_extraction` and
  `job_assessment` rows.
