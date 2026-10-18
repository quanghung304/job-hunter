# Testing strategy

Goal: `make verify` runs fully offline and deterministically (Docker is allowed for
Testcontainers). Real sources and the real LLM are exercised only by opt-in live checks
and the eval.

## Backend layers (`backend/`)

| Layer | What | Tools | Location / naming |
|---|---|---|---|
| Unit | Mappers, normalization, fingerprint, prefilter rules, eligibility rules, status machine, prompt rendering, budget guard | JUnit 5, AssertJ, parameterized tests | `src/test/java/**` (`*Test`) |
| HTTP contract | Each collector against a recorded response | WireMock serving `fixtures/<code>/*` | `*CollectorTest` |
| Integration | Repositories, ingest upsert/dedupe, pipeline end-to-end (WireMock sources, `FakeLlmClient`, 0 LLM calls) | Spring Boot test + Testcontainers PostgreSQL; `src/test/resources/config/application.yml` turns off the pipeline cron and startup catch-up so no test reaches a real source | `*IT` (failsafe) |
| API | Controllers: status codes, JSON shape, Problem Details | `@WebMvcTest` + `MockMvc` | `*ControllerTest` |
| Contract export | Writes `/v3/api-docs` to `docs/architecture/openapi.json`; fails if the file is out of date (unless `-Dapi.export=true`) | Spring Boot test | `OpenApiExportIT` |
| Architecture | Module dependency rules from `overview.md` | ArchUnit | `ArchitectureTest` |
| Live (opt-in) | One real call per collector; asserts > 0 items and mapping works. `LlmProvidersLiveTest`: one extract-v1 call per FREE model (keys from `.env`, paid calls forced off, ADR-0009) | JUnit tag `live` | excluded by default; `./mvnw verify -Plive`, or `./mvnw test -Plive -Dtest=LlmProvidersLiveTest` (ask the owner first) |
| Eval | LLM quality on labeled jobs | `EvalRunner` | `docs/ai/eval/` |

## Frontend layers (`frontend/`)

| Layer | What | Tools |
|---|---|---|
| Unit | `lib/` helpers (formatting, labels, status machine copy vs data-model §2), filter ↔ URL params | Vitest |
| Component | ScoreBadge, StatusActions, FilterBar, NotesEditor, admin tables: render states (loading/empty/error/data) and interactions | Vitest + React Testing Library + `@testing-library/user-event` |
| Page integration | Board, Job detail, Admin against a mocked API (optimistic update + rollback, keyboard shortcuts, polling) | RTL + MSW handlers in `src/test/handlers.ts` typed from `schema.d.ts` |
| E2E smoke (`make e2e`) | 1) board loads and dismiss hides a job 2) open detail, change status, add a note, reload persists 3) admin "Run pipeline" completes with fake LLM | Playwright against the real backend with `app.llm.force-model=fake` and WireMock-served source fixtures |

E2E runs in `make e2e`, not in `make verify` (it needs a running stack).

## Fixtures

- Capture a **real** response once per source (task step 1), trim to 3–10 items, keep the
  original structure, save as `backend/src/test/resources/fixtures/<code>/<name>.json`
  (or `.xml`).
- Include at least: a normal item, an item with missing optional fields, an item with HTML
  description, and (where possible) a non-remote or restricted-region item.
- Record the capture date in `docs/sources/<code>.md`.
- Do not hand-invent fixture fields. If the real response differs from the source spec,
  update the spec.
- MSW handlers return data shaped by the generated types; keep one realistic job sample in
  `frontend/src/test/fixtures/` (can be copied from a real API response in dev).

## FakeLlmClient

- Returns canned JSON from `llm-fakes/<prompt>/job-<jobId>.json` (test resources) or
  `llm-fakes/<prompt>/default.json` (main resources, so `force-model=fake` works in the app),
  where `<prompt>` is the prompt id without version (`score-v1` → `score`). Tests can also
  `enqueue` scripted steps (text, HTTP error, 429, timeout).
- Can be configured to fail N times, return invalid JSON, exceed tokens, or answer like a
  provider out of quota (429 with daily-limit body, or 401), to test retries, the budget
  guard, `QuotaTracker`, alerts and model switching. In `make e2e` this is driven by the
  test-only property `app.llm.fake.simulate=quota-after:<n>`.
- `OpenAiCompatibleLlmClient` is tested against WireMock with recorded response shapes
  from the providers' docs (success, 429 variants, 401, 404).

## Test data rules

- Use `Clock.fixed(...)` for anything time-based (backend) and `vi.setSystemTime` (frontend).
- Integration tests create their own data; no shared mutable state between tests.

## Coverage expectations

No numeric gate. Must-have: every collector mapper, every eligibility rule row in
`scoring-spec.md` §4, every allowed and forbidden status transition (backend and the
frontend copy), fingerprint examples in `data-model.md` §4, every endpoint's success and
main error case.
