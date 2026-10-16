# T-010: LLM client, prompt loader, call log, budget guard

Phase: 1 · Depends on: T-003 · Size: M

## Goal
A provider-independent `LlmClient` with an Anthropic implementation and a fake, versioned
prompt loading/rendering, logging of every call to `llm_call`, and a budget guard.

## Read first
- ADR-0004 · `docs/ai/prompts/README.md` · `docs/ai/scoring-spec.md` §6, §8
- config-spec §2 (`app.llm.*`) · `docs/testing.md` (FakeLlmClient)

## In scope
- `PromptRepository`: loads `classpath:prompts/<id>.md`, parses front matter and the
  `## system` / `## user` sections, caches; `PromptTemplate.render(Map<String,String>)`
  fails on missing placeholder; escapes `</` inside values.
- `LlmClient.completeJson(LlmRequest, Class<T>)`: request = prompt id, variables, model,
  purpose, jobId; result = parsed object, raw text, token usage, latency.
- `AnthropicLlmClient`: uses the official Anthropic Java SDK. **Before coding, read the
  current SDK README/docs** for: Maven coordinates and latest version, Messages API usage,
  usage/token fields, retry settings, and whether structured JSON output or prompt caching
  is available; note findings in the task's progress entry. Temperature/max tokens from
  front matter.
- JSON extraction: first top-level JSON object in the text; Jackson parse; Bean Validation
  on the target record; on failure one corrective retry with the error message.
- Retries: 2 s, 8 s on 429/5xx/network.
- `LlmCallLogger`: writes `llm_call` with cost from `app.llm.pricing.<model>`
  (if pricing missing → cost null + WARN once).
- `BudgetGuard`: per-run call counter (`max-calls-per-run`) and month-to-date cost
  (sum of `llm_call.cost_usd` since the 1st of the month in `app.pipeline.zone`) vs
  `monthly-budget-usd`. `tryAcquire()` returns false when exhausted.
- `FakeLlmClient` (`app.llm.provider=fake`) per testing.md.

## Out of scope
Extraction/scoring logic (T-011/T-012).

## Acceptance criteria
- [ ] With `provider=fake`, the app runs without an API key.
- [ ] A rendered prompt with a missing variable throws `PromptRenderException`.
- [ ] Every call (success or failure) produces exactly one `llm_call` row.
- [ ] Budget guard blocks calls after limits in tests.

## Tests
`PromptRepositoryTest` (parse real `extract-v1.md`, `score-v1.md`), `PromptTemplateTest`,
`JsonExtractorTest` (prose around JSON, code fences, nested braces, invalid),
`AnthropicLlmClientTest` (WireMock stub of the Messages endpoint if the SDK allows a base URL;
otherwise test via an adapter interface), `BudgetGuardIT`, `FakeLlmClientTest`.
