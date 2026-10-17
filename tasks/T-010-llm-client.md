# T-010: LLM clients, model registry, router, call log, budget guard

Phase: 1 · Depends on: T-003 · Size: M–L (split into 10a clients + prompts, 10b registry + router + quota basics if needed)

## Goal
Provider-independent LLM access: a model registry from `config/llm.yaml`, an Anthropic
client and an OpenAI-compatible client (Groq, Cerebras, OpenRouter, GitHub Models, Ollama),
a fake client, a router that resolves the assigned model per purpose, versioned prompts,
logging of every call, quota status tracking, and a budget guard for paid models.
(Alerts and the switch UI/API are T-022/T-023.)

## Read first
- ADR-0004, **ADR-0008** · `docs/ai/prompts/README.md` · `docs/ai/scoring-spec.md` §6, §8
- config-spec §2 (`app.llm.*`), **§5 (`llm.yaml`)**, §3 (env vars)
- data-model `llm_call`, `llm_assignment`, `llm_assignment_event`, `llm_model_state`
- `docs/testing.md` (FakeLlmClient)

## In scope
- Migration for `llm_assignment`, `llm_assignment_event`, `llm_model_state` and the new
  `llm_call` columns (if T-003 already created `llm_call`, add a new migration — never edit
  an applied one).
- `ModelRegistry`: loads and validates `config/llm.yaml` (records per config-spec §5); marks
  models whose key env var is empty as `ERROR: missing key`.
- `PromptRepository` / `PromptTemplate` (front matter, `## system` / `## user`, fails on
  missing placeholder, escapes `</` in values).
- `LlmClient.completeJson(LlmRequest, Class<T>)` — request = prompt id, variables, resolved
  model, purpose, jobId; result = parsed object, raw text, token usage, latency, rate-limit
  info (remaining/reset when the provider sends it).
- `AnthropicLlmClient` (official Anthropic Java SDK). **Before coding, read the current SDK
  docs**: Maven coordinates/version, Messages API, usage fields, base URL override (for
  WireMock), rate-limit headers, JSON output options, prompt caching. Note findings in
  `progress.md`.
- `OpenAiCompatibleLlmClient` with `RestClient`: `POST {baseUrl}/chat/completions`, body
  `{model, messages:[system,user], temperature, max_tokens, response_format?}`,
  `Authorization: Bearer <key>` when a key is set. Parse `choices[0].message.content` and
  `usage`. **Before coding, read each enabled provider's docs** (Groq, Cerebras, OpenRouter,
  GitHub Models, Ollama) for: exact base URL, current model ids, whether `response_format`
  json mode is supported, rate-limit header names and 429 body format. Record them as
  comments in `config/llm.yaml` and correct wrong values there.
- `FakeLlmClient` per testing.md; `app.llm.force-model=fake` overrides all assignments.
- `LlmRouter.call(purpose, …)`: reads the assignment (seed from `defaults` if the table is
  empty), checks model status, applies fallback/pause rules (overview "Pipeline run" step 3),
  returns `LlmResult` or `LlmUnavailable(purpose, reason)` (not an exception the caller must
  treat as a job failure).
- `QuotaTracker` (basic part): updates `llm_model_state` from responses per data-model status
  rules — 429 → RATE_LIMITED/EXHAUSTED with `until`; 401/403/404/missing key → ERROR; counts
  vs known `limits` → NEAR_LIMIT/EXHAUSTED; success or `until` passed → AVAILABLE. Publishes
  `LlmModelStatusChanged(modelId, from, to, detail, until)` on every transition. Client-side
  pacing to respect `rpm` (simple per-model rate limiter).
- JSON extraction (first top-level object, Jackson, Bean Validation, one corrective retry).
- Retries: 2 s, 8 s on 5xx/network; 429 handling per scoring-spec §6.
- `LlmCallLogger`: one `llm_call` row per HTTP attempt outcome, with `model_id`, `tier`,
  `http_status`, `error_kind`, cost for PAID models from registry pricing.
- `BudgetGuard`: per-run call counter (all models) and month-to-date PAID cost vs
  `monthly-budget-usd`.

## Out of scope
Extraction/scoring logic (T-011/T-012). Telegram/UI alerts and the switch API (T-022, T-023).

## Acceptance criteria
- [ ] With `app.llm.force-model=fake` the app runs without any API key.
- [ ] Switching the assignment row in the DB to another model changes the model used by the
      next call without restart.
- [ ] A 429 with a daily-limit body marks the model EXHAUSTED with `until` and does not count
      a job attempt; with `autoFallback=false` the purpose pauses; with `true` the next usable
      fallback is used (PAID only when `allowPaid`).
- [ ] A missing key env var shows the model as ERROR and it is never called.
- [ ] Every call produces exactly one `llm_call` row.
- [ ] Budget guard blocks PAID calls after the monthly limit but not FREE/LOCAL ones.

## Tests
`ModelRegistryTest` (valid file, bad provider ref, missing key), `PromptRepositoryTest`
(real `extract-v1.md`, `score-v1.md`), `PromptTemplateTest`, `JsonExtractorTest`,
`OpenAiCompatibleLlmClientTest` (WireMock: success, json mode, 429 with `retry-after`,
429 daily-quota body, 401, 404, 500, timeout), `AnthropicLlmClientTest` (WireMock if base URL
can be overridden, else adapter), `QuotaTrackerTest` (status table from data-model with a
fixed Clock, near-limit threshold, midnight reset), `LlmRouterIT` (assignment seed, pause,
auto-fallback, allowPaid), `BudgetGuardIT`, `FakeLlmClientTest`.
