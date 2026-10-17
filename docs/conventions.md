# Coding conventions

## Repository layout

```
backend/
  pom.xml, mvnw
  src/main/java/io/github/quanghung304/jobhunter/
    common/ profile/ source/<code>/ ingest/ llm/ enrich/ scoring/ tracking/ notify/ pipeline/ api/ eval/
  src/main/resources/
    application.yml, application-local.yml
    db/migration/V<n>__<snake_case>.sql
  src/test/java/...       # mirrors main
  src/test/resources/fixtures/<source-code>/*.json|*.xml
frontend/                 # see docs/architecture/frontend.md
docs/ai/prompts/          # prompt files; packaged into the backend jar as classpath:prompts/
config/, tasks/, data/ (git-ignored)
Makefile, docker-compose.yml, .env.example
```

`backend/pom.xml` adds `../docs/ai/prompts` as a resource directory with `targetPath`
`prompts`, so prompts have one source of truth. Backend paths to `config/`, `data/` and
`.env` default to `../` (the backend runs with `backend/` as working directory).

## Makefile targets

| Target | Does |
|---|---|
| `verify` | `backend: ./mvnw verify` then `frontend: npm ci && npm run lint && npm run typecheck && npm test` |
| `dev` | runs backend (`spring-boot:run`, profile `local`) and `npm run dev` together |
| `run` | `npm run build`, then `./mvnw -Pwith-frontend spring-boot:run` |
| `api` | backend `OpenApiExportIT` writes `docs/architecture/openapi.json`, then `npm run gen:api` |
| `e2e` | builds and starts the app with `app.llm.force-model=fake` and a seeded DB, runs Playwright |

## Java (backend)

- Java 21. Use `record` for DTOs, config properties and value objects. No Lombok.
- Constructor injection only; no field `@Autowired`.
- Each module exposes a small public API (e.g. `IngestService`) in its root package;
  internals go in sub-packages and are package-private where possible.
- JPA entities and repositories live in `<module>.persistence` and are used only by that
  module (ArchUnit rule); never return entities from a module's public API
  — map to records. API DTOs (`api` module) are separate from module records.
- Enums are stored as `text` (`@Enumerated(EnumType.STRING)`).
- Time: inject `java.time.Clock`; never call `Instant.now()` directly (testability).
- Null handling: `Optional` for return values that may be absent; never `Optional` fields
  or parameters.
- Collections returned are unmodifiable.
- No checked exceptions across module boundaries. Module-specific runtime exceptions
  extend `JobHunterException` and carry a stable `code` used in Problem Details.

## REST API

- Follow `docs/architecture/api-spec.md`. Controllers in `api`, one per resource
  (`JobController`, `AdminController`, `MetaController`).
- Validate request bodies with Bean Validation; errors → `ProblemDetail` via one
  `@RestControllerAdvice`.
- Annotate DTOs so the OpenAPI document has accurate required/nullable fields and enums
  (`@Schema(requiredMode = REQUIRED)`), because the frontend types are generated from it.

## TypeScript / React (frontend)

- `strict: true`; no `any` (use `unknown` + narrowing). ESLint + Prettier must pass.
- Function components and hooks only. One component per file; file name = component name.
- Server data only via TanStack Query hooks in `src/api/queries.ts`; query keys are arrays
  starting with the resource (`['jobs', filters]`, `['job', id]`, `['admin','overview']`).
- Mutations invalidate or update the affected queries; status changes are optimistic.
- Styling with Tailwind utility classes; shared visual pieces become components, not
  copied class strings. shadcn components live in `src/components/ui`.
- No `useEffect` for data fetching. No global state library.

## Naming

| Thing | Pattern | Example |
|---|---|---|
| Collector | `<Source>Collector` in `source.<code>` | `ArbeitnowCollector` |
| Source DTOs | `<Source><Thing>Dto` | `ArbeitnowJobDto` |
| Mapper | `<Source>Mapper` (pure functions, unit-tested) | `ArbeitnowMapper` |
| Config properties | `<Module>Properties` | `LlmProperties` |
| API DTOs | `<Thing>Response` / `<Thing>Request` | `JobSummaryResponse`, `StatusChangeRequest` |
| Migrations | `V<n>__<verb>_<object>.sql` | `V3__create_job_assessment.sql` |
| Prompt files | `<purpose>-v<n>.md` | `extract-v1.md` |
| React components | PascalCase `.tsx` | `ScoreBadge.tsx` |
| Hooks | `use<Thing>` | `useJobs`, `useChangeStatus` |

## Collectors

- Pattern: `Collector` fetches pages with `RestClient` → deserializes to `*Dto` → `Mapper`
  converts to `NormalizedPosting`. The mapper has no I/O.
- HTML → plain text via `common.text.HtmlText.toPlain(...)` (jsoup, keeps line breaks for
  `<p>`, `<li>`, `<br>`).
- Never throw out of `collect()`; catch, log, return `CollectStatus.FAILED` with message.
  Per-item mapping errors are warnings; the run is `PARTIAL`.
- Sleep between requests per the source's rate rule (`docs/sources/<code>.md`).
- Set the shared User-Agent.

## Logging

- INFO: one line per collector run (`source=… fetched=… new=… status=… ms=…`),
  one per pipeline stage summary, one per LLM call (`purpose model tokens cost ms`).
- DEBUG: per-item details. Never log API keys, full prompts or full descriptions above DEBUG.

## Database

- Flyway only; one migration per task that changes schema.
- Native SQL allowed for upserts (`on conflict`) and jsonb queries; keep it in repositories.
- Every query in `tracking`, `scoring`, `notify` filters by `profile_id`.

## Git

- Conventional commits with scope: `feat(source): add arbeitnow collector [T-004]`,
  `feat(fe-board): …`, `fix(ingest): …`, `docs: …`, `test: …`, `chore: …`.
- One task per branch `task/T-004-arbeitnow`; small commits.
- A commit that changes behavior also updates the docs that describe it.

## Definition of done (every task)

1. Acceptance criteria in the task file are met.
2. `make verify` passes offline (Docker allowed for Testcontainers).
3. New/changed public methods and components have tests; mappers and rules have
   table-driven tests.
4. Docs updated (see AGENTS.md step 5); API contract regenerated if endpoints changed.
5. `tasks/progress.md` entry added.
