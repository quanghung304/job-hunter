# AGENTS.md — Job Hunter

Read this file first in every session. It is the entry point; details live in `docs/`.

## What this project is

A locally-run tool that collects remote job postings from public APIs/feeds, filters and
scores them against one candidate profile with an LLM, and lets the owner track them
(F1 + F2). Later phases add CV tailoring, application tracking and assisted apply, and
possibly a multi-user micro-SaaS. The current phase is **Phase 1 (MVP: F1 + F2)**.

## Repository layout

```
backend/    Spring Boot app (REST API under /api, serves the built SPA)
frontend/   React SPA (Vite + TypeScript)
config/     profile.example.yaml, companies.yaml (profile.yaml is git-ignored)
docs/       product, architecture, sources, ai (prompts + eval)
tasks/      backlog, task specs, progress log
data/       local runtime output (git-ignored)
Makefile    shortcuts for both toolchains
```

## Stack

- Backend: Java 21, Spring Boot 3.x, Maven (`backend/mvnw`), PostgreSQL 16 (Docker Compose),
  Flyway, Spring Data JPA, `RestClient`, Jackson, Rome (RSS), jsoup, springdoc-openapi,
  Anthropic Java SDK behind our own `LlmClient`
- Frontend: React + TypeScript + Vite, React Router, TanStack Query, Tailwind + shadcn/ui,
  `openapi-fetch` with generated types
- Notifications: Telegram Bot API
- Tests: JUnit 5, AssertJ, WireMock, Testcontainers, ArchUnit · Vitest, React Testing
  Library, MSW, Playwright

## Commands

| Purpose | Command |
|---|---|
| Start DB | `docker compose up -d db` |
| All checks (backend + frontend) | `make verify` |
| Backend build + tests | `cd backend && ./mvnw verify` |
| Frontend tests / lint / typecheck | `cd frontend && npm test && npm run lint && npm run typecheck` |
| Dev mode (two processes) | `make dev` → backend :8080, Vite :5173 (proxies `/api`) |
| Run as one app | `make run` → builds SPA into the backend, http://localhost:8080 |
| Regenerate API contract + TS types | `make api` (see "API changes" below) |
| Run one pipeline pass | `curl -X POST localhost:8080/api/admin/pipeline/run` |
| Run eval | see `docs/ai/eval/README.md` |

## Document map

| Need | Read |
|---|---|
| Why, scope, non-goals | `docs/product/PRD.md`, `docs/product/roadmap.md` |
| Backend components and boundaries | `docs/architecture/overview.md` |
| REST endpoints | `docs/architecture/api-spec.md` (+ generated `openapi.json`) |
| Frontend structure, pages, rules | `docs/architecture/frontend.md` |
| Tables, enums, status lifecycle | `docs/architecture/data-model.md` |
| `profile.yaml` / `application.yml` fields | `docs/architecture/config-spec.md` |
| Past decisions (do not re-litigate) | `docs/architecture/adr/` |
| Source list and status | `docs/sources/catalog.md` + `docs/sources/<source>.md` |
| LLM extraction, eligibility, scoring | `docs/ai/scoring-spec.md`, `docs/ai/prompts/` |
| Coding rules | `docs/conventions.md` |
| How to test | `docs/testing.md` |
| How to run / fix in operation | `docs/runbook.md` |
| What to do next | `tasks/backlog.md`, then the task file `tasks/T-xxx-*.md` |
| What was done last | `tasks/progress.md` |

## How to work on a task

1. Read `tasks/progress.md`, then the task file you were given. Read only the docs it links.
2. If the task conflicts with a doc or ADR, **stop and ask** — do not silently pick one.
3. Implement within the task's "In scope" list. Do not touch files listed under "Do not touch".
4. Write the tests the task lists. `make verify` must pass with no network access
   (Docker for Testcontainers is allowed).
5. Update docs that your change made stale **in the same commit** (schema → `data-model.md`,
   endpoint → `api-spec.md` + `openapi.json`, new config key → `config-spec.md`,
   new source → `catalog.md`, new decision → new ADR).
6. Append an entry to `tasks/progress.md` and tick the task in `tasks/backlog.md`.

## API changes

1. Change the controller/DTOs in `backend` and `api-spec.md`.
2. `make api` runs the backend test that exports `/v3/api-docs` to
   `docs/architecture/openapi.json`, then `npm run gen:api` in `frontend`.
3. Fix TypeScript errors; commit backend, spec, `openapi.json` and `schema.d.ts` together.

## Hard rules

- Never commit secrets. Keys come from environment variables only (see `docs/runbook.md`).
  `config/profile.yaml`, `.env` and `data/` are git-ignored.
- Never call a real external API or a real LLM in tests. Use fixtures, WireMock,
  `FakeLlmClient`, MSW.
- A new collector is not done until it has a captured real response fixture and a mapper test.
- Respect each source's rules in `docs/sources/<source>.md` (rate limit, attribution, User-Agent).
  Do not add LinkedIn, Indeed, Glassdoor or any source marked `forbidden` in the catalog.
- Do not scrape HTML pages when the source offers an API or feed.
- All LLM calls go through `LlmClient`; every call is logged in `llm_call` and counted
  against the budget guard. Prompts are versioned files; never inline prompt text in Java.
- Changing a prompt means creating a new version file (`score-v2.md`), never editing a
  released one, then running the eval and recording results in `docs/ai/eval/results.md`.
- Store times as UTC `Instant`/`timestamptz`. Do not use `LocalDateTime` for persisted data.
- Database changes only via new Flyway migrations. Never edit an applied migration.
- Frontend: never hand-edit `schema.d.ts`; components never call `fetch` directly.
- No features from later phases unless the task says so (see PRD non-goals).

## Communication

The owner (Hugh) reads Vietnamese and English. Write code, comments, docs and commit
messages in English. Summaries to the owner at the end of a session may be in Vietnamese.
When a decision is needed, present options with a recommendation instead of guessing.
