# ADR-0009: Paid LLM calls need the owner's consent

Status: Accepted · Date: 2026-10-18 · Refines: ADR-0008 (fallback to paid models)

## Context
The default models are free tiers (ADR-0008), but the registry also holds paid models
(Claude Haiku, PAID OpenRouter models). A paid call can be started by the scheduled
pipeline, an auto-fallback, an admin action (switch model, "Test", re-score, eval) or by a
developer or coding agent running something locally. The owner wants no money spent on
LLM calls without having agreed to it — neither while the system is used nor while it is
developed.

## Decision
1. **Default deny.** `app.llm.allow-paid-calls` (env `LLM_ALLOW_PAID_CALLS`) is `false` by
   default. While it is false, `BudgetGuard` refuses every call to a PAID model before any
   request is sent; the router answers `LlmUnavailable(PAID_NOT_ALLOWED)` (not a job failure).
   This applies to assignments, fallbacks (even with `allowPaid`), `callModel` (eval, "Test")
   and every retry. Setting it to `true` is the owner's standing consent; the monthly budget
   (`app.llm.monthly-budget-usd`) still caps it.
2. **One path.** Only `LlmRouter` calls `LlmClient.send` (ArchUnit rule), so no code can skip
   the guard. The tier comes from `config/llm.yaml`; a model of an `anthropic` provider must
   be `PAID` (registry validation), so a paid API cannot be mislabelled FREE there.
3. **Per-action confirmation in features.** Any UI/API action that may start PAID calls shows
   the model, the number of calls and the estimated cost, and runs only after an explicit
   confirmation (`confirmPaid: true` or a dry run followed by a confirmed request). Switching
   an assignment, enabling "Allow paid fallback", the "Test" button on a PAID model, re-score
   and eval runs on a PAID model all follow this. If paid calls are not allowed at all, the
   action is refused (409 `PAID_CALLS_DISABLED`) and the UI says how to enable them.
4. **Development.** Tests never reach a real LLM (`FakeLlmClient`, WireMock). Coding agents
   must ask the owner before running anything that could call a paid LLM API (live checks,
   eval, the running app with a paid key and `allow-paid-calls=true`, `curl` to a provider),
   and must never set `allow-paid-calls=true` or a paid key on their own.

## Alternatives considered
- Only the monthly budget — limits the amount but still spends without asking.
- A consent row in the DB with expiry, toggled from the Admin page — better UX; can be added
  later (T-022/T-023) on top of the same guard. The config flag works before the UI exists.
- Remove paid models from the registry — loses a useful fallback the owner may want.

## Consequences
- Out of the box, Claude Haiku is listed but never called; the Admin page must show why.
- Tasks that add paid-capable actions (T-012 re-score, T-016 eval, T-021–T-023 admin/LLM UI)
  must implement the confirmation of point 3 and test the refusal path.
- AGENTS.md carries point 4 as a hard rule.
