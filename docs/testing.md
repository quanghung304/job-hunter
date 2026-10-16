# Testing strategy

Goal: `make verify` runs fully offline and deterministically (Docker is allowed for
Testcontainers). Real sources and the real LLM are exercised only by opt-in live checks
and the eval.

## Backend layers (`backend/`)

| Layer | What | Tools | Location / naming |
|---|---|---|---|
| Unit | Mappers, normalization, fingerprint, prefilter rules, eligibility rules, status machine, prompt rendering, budget guard | JUnit 5, AssertJ, parameterized tests | `src/test/java/**` (`*Test`) |
| HTTP contract | Each collector against a recorded response | WireMock serving `fixtures/<code>/*` | `*CollectorTest` |
| Integration | Repositories, ingest upsert/dedupe, pipeline end-to-end with `FakeLlmClient` | Spring Boot test + Testcontainers PostgreSQL | `*IT` (failsafe) |
| API | Controllers: status codes, JSON shape, Problem Details | `@WebMvcTest` + `MockMvc` | `*ControllerTest` |
| Contract export | Writes `/v3/api-docs` to `docs/architecture/openapi.json`; fails if the file is out of date (unless `-Dapi.export=true`) | Spring Boot test | `OpenApiExportIT` |
| Architecture | Module dependency rules from `overview.md` | ArchUnit | `ArchitectureTest` |
| Live (opt-in) | One real call per collector; asserts > 0 items and mapping works | JUnit tag `live` | excluded by default; `./mvnw verify -Plive` |
| Eval | LLM quality on labeled jobs | `EvalRunner` | `docs/ai/eval/` |

## Frontend layers (`frontend/`)

| Layer | What | Tools |
|---|---|---|
| Unit | `lib/` helpers (formatting, labels, status machine copy vs data-model §2), filter ↔ URL params | Vitest |
| Component | ScoreBadge, StatusActions, FilterBar, NotesEditor, admin tables: render states (loading/empty/error/data) and interactions | Vitest + React Testing Library + `@testing-library/user-event` |
| Page integration | Board, Job detail, Admin against a mocked API (optimistic update + rollback, keyboard shortcuts, polling) | RTL + MSW handlers in `src/test/handlers.ts` typed from `schema.d.ts` |
| E2E smoke (`make e2e`) | 1) board loads and dismiss hides a job 2) open detail, change status, add a note, reload persists 3) admin "Run pipeline" completes with fake LLM | Playwright against the real backend with `app.llm.provider=fake` and WireMock-served source fixtures |

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

- Returns canned JSON keyed by (`purpose`, job external id) from
  `backend/src/test/resources/llm-fakes/`, or a default valid object.
- Can be configured to fail N times, return invalid JSON, or exceed tokens, to test retries
  and the budget guard.

## Test data rules

- Use `Clock.fixed(...)` for anything time-based (backend) and `vi.setSystemTime` (frontend).
- Integration tests create their own data; no shared mutable state between tests.

## Coverage expectations

No numeric gate. Must-have: every collector mapper, every eligibility rule row in
`scoring-spec.md` §4, every allowed and forbidden status transition (backend and the
frontend copy), fingerprint examples in `data-model.md` §4, every endpoint's success and
main error case.
