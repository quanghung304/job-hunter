# ADR-0008: Multiple LLM providers, quota tracking and runtime model switching

Status: Accepted · Date: 2026-10-17 · Refines: ADR-0004 (which model runs each step)

## Context
The tool makes ~100–250 LLM calls a day (extraction + scoring). Several providers offer
free tiers that cover this volume (Groq, Cerebras, GitHub Models; OpenRouter with a top-up)
and a local option (Ollama), all through OpenAI-compatible chat-completions endpoints.
Free tiers have per-minute and per-day limits, return HTTP 429 when exhausted, and change
without notice. Paid models (Claude Haiku) cost little but not zero. The owner wants to be
told when a model runs out and to switch to another model without editing files or
restarting.

Privacy constraint: the scoring prompt contains a summary of the owner's CV. Providers that
train on free-tier data (e.g. Gemini free tier, Mistral Experiment tier) are not added to
the registry by default.

## Decision
1. **Model registry** in `config/llm.yaml` (committed, no secrets): providers (type, base
   URL, env var holding the key) and models (provider, provider model id, free/paid,
   known limits, prices). Spec: `config-spec.md` §5.
2. **Two client implementations** behind `LlmClient`: `AnthropicLlmClient` (Anthropic SDK)
   and `OpenAiCompatibleLlmClient` (plain `RestClient` against `/chat/completions`, used for
   Groq, Cerebras, OpenRouter, GitHub Models, Ollama). Plus `FakeLlmClient` for tests.
   `LlmRouter` picks the client and model for each call from the **active assignment**.
3. **Assignments per purpose** (`EXTRACT`, `SCORE`) are stored in the DB (`llm_assignment`)
   and changed from the Admin page at runtime. Defaults come from `config/llm.yaml`.
   Each assignment has an ordered **fallback list** and an `autoFallback` flag
   (default **off**; falling back to a paid model additionally requires `allowPaid`).
4. **Quota tracking** (`QuotaTracker`): counts calls and tokens per model per minute/day
   from `llm_call`, reads provider rate-limit headers when present, and keeps a status per
   model in `llm_model_state`: `AVAILABLE`, `NEAR_LIMIT` (≥ warn threshold of a known
   limit), `RATE_LIMITED` (429 with a short reset), `EXHAUSTED` (daily/monthly limit hit or
   429 with reset > 1h), `ERROR` (auth/model-not-found), with `until` when known.
5. **Alerts**: on transition to `NEAR_LIMIT`, `EXHAUSTED` or `ERROR` the app sends a Telegram
   message and shows a banner in the web UI with a "Switch model" action. One alert per
   model per status per day.
6. When the assigned model is unusable and no fallback applies, LLM work for that purpose
   pauses; jobs stay `PENDING` and are processed by a later run. Nothing is lost.

## Alternatives considered
- Single provider (Anthropic only) — simplest, small cost; no free option.
- Automatic silent fallback to any model — risks surprise costs and unnoticed quality drops.
- Switching by editing config + restart — what the owner explicitly did not want.

## Consequences
- New tables `llm_assignment`, `llm_model_state`; `llm_call` records `model_id`.
- Changing the scoring model changes score quality; existing scores are kept and labelled
  with their model. Run the eval (`docs/ai/eval/`) before making a model the default.
- Free-tier limits in `config/llm.yaml` are best-effort hints; real enforcement is the
  provider's 429, which the tracker handles.
