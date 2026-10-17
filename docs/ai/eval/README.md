# Eval

Measures extraction, eligibility and scoring quality against jobs labeled by the owner.
Run it before switching any prompt version or model, and after changing eligibility rules.

## Files

- `labeled-jobs.jsonl` — one labeled job per line (created in T-016, filled by the owner).
- `results.md` — append one section per eval run.
- Job texts are stored inside the JSONL (snapshot), so the eval does not depend on the DB.

## Label format (one JSON object per line)

```json
{
  "id": "eval-001",
  "source": "himalayas",
  "url": "https://...",
  "title": "...", "company": "...", "locationText": "...", "remoteHint": "REMOTE",
  "employmentHint": "Contract", "description": "full plain text snapshot",
  "label": {
    "eligibility": "ELIGIBLE | INELIGIBLE | UNCERTAIN",
    "relevance": "HIGH | MEDIUM | LOW",
    "allowedRegions": ["EU"],          // optional: checks extraction
    "employmentTypes": ["CONTRACT"],   // optional
    "note": "why"
  },
  "labeledAt": "2026-10-20"
}
```

## Building the set (owner task, ~1–2 hours)

1. Run Phase 1 collectors for a few days (extraction/scoring may be on or off).
2. Admin → "Export for labeling" (T-016) writes `data/eval-candidates.jsonl` with ~80 jobs
   sampled across sources and prefilter outcomes.
3. Label at least 40: ≥ 10 HIGH, ≥ 10 LOW, ≥ 8 INELIGIBLE (mix of US-only, on-site,
   full-time-only, language). Copy labeled lines into `labeled-jobs.jsonl`.
4. Keep the profile used for labeling in mind; if the profile changes a lot, relabel relevance.

## Running

```bash
cd backend && ./mvnw -q exec:java -Dexec.mainClass=io.github.quanghung304.jobhunter.eval.EvalRunner \
  -Dexec.args="--extract=extract-v1 --score=score-v1 --extract-model=groq-llama-70b --score-model=groq-llama-70b"
```

Model arguments are registry ids from `config/llm.yaml`. Uses the real LLM (free-tier
quota or money: prints the call count and estimated cost first and asks for `--yes`).
Each run is also stored in the `eval_run` table so the Admin page can show the latest
result next to each model. Compare models before changing a default.
Outputs metrics from `scoring-spec.md` §7, a confusion matrix for eligibility, and a list of
disagreements with reasons, to stdout and `data/eval/<timestamp>.md`.

## results.md entry template

```
## 2026-10-25 — extract-v1 / score-v1 / groq-llama-70b + groq-llama-70b — 42 jobs
| Metric | Value | Target |
| false-eligible | 2.4% | ≤ 5% |
| ... |
Notes: what changed, notable disagreements.
```
