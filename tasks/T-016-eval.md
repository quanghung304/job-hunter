# T-016: Eval harness + export for labeling

Phase: 1 · Depends on: T-012 and a few days of collected data · Size: M

## Goal
The owner can export candidate jobs for labeling and run an offline eval that reports the
metrics in scoring-spec §7.

## Read first
- `docs/ai/eval/README.md` · `docs/ai/scoring-spec.md` §7

## In scope
- Implement `POST /api/admin/eval/export` (replacing the 501 stub from T-014; the button
  already exists from T-021) "Export for labeling": sample ~80 ACTIVE jobs stratified by source
  and prefilter result (PASS majority, some REJECT), write `data/eval-candidates.jsonl` in the
  label format with `label` fields empty.
- `EvalRunner` (main class, non-web Spring context with profile `eval`): args `--extract`,
  `--score`, `--model`, `--limit`, `--yes`. Loads `docs/ai/eval/labeled-jobs.jsonl`, runs
  prefilter → extraction → eligibility → scoring in memory (no DB writes to job tables;
  LLM calls logged with purpose EVAL), prints estimated cost and requires `--yes`.
- Report: metrics vs targets, eligibility confusion matrix, extraction field accuracy for
  labeled `allowedRegions`/`employmentTypes`, list of disagreements (id, label, predicted,
  reasons, score). Write to stdout and `data/eval/<timestamp>.md`.
- Create an empty `docs/ai/eval/labeled-jobs.jsonl` with 2 example lines marked
  `"id": "example-*"` (ignored by the runner).

## Acceptance criteria
- [ ] Runner works with `app.llm.provider=fake` (for tests) and real provider.
- [ ] Metrics computed exactly as defined (unit-tested with hand-made predictions).

## Tests
`EvalMetricsTest`, `EvalRunnerIT` (fake LLM, 6 labeled lines), `LabelingExportIT`.
