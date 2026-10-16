# T-001: Backend bootstrap

Phase: 1 · Depends on: — · Size: M

## Goal
A buildable Spring Boot skeleton in `backend/` with the module packages, PostgreSQL via
Docker Compose, Flyway, OpenAPI, the test infrastructure (Testcontainers, WireMock,
ArchUnit) and a root `Makefile`. (Frontend scaffold is T-019.)

## Read first
- `AGENTS.md`, `docs/conventions.md`, `docs/testing.md`, `docs/architecture/overview.md`
  (Modules, Serving the SPA) · ADR-0001, ADR-0002, ADR-0007

## In scope
- `backend/`: Maven project with wrapper; Java 21; Spring Boot 3.x latest stable; group
  `io.github.quanghung304`, artifact `job-hunter`.
- Dependencies: web, validation, data-jpa, actuator, flyway (+ postgres module), postgresql
  driver, jackson (jsr310), jsoup, rome, springdoc-openapi (webmvc-ui); test:
  spring-boot-starter-test, testcontainers (postgresql, junit-jupiter), WireMock (artifact
  compatible with Boot 3 / Jetty 12), archunit-junit5.
- Do **not** add the Anthropic SDK yet (T-010).
- `pom.xml`: surefire for `*Test`, failsafe for `*IT`; resource dir `../docs/ai/prompts` →
  `prompts/`; profile `live` (includes JUnit tag `live`, excluded by default); profile
  `with-frontend` that copies `../frontend/dist/**` into `target/classes/static` (resources
  plugin; skip silently if the folder does not exist, with a WARN).
- Root `docker-compose.yml`: service `db` (postgres:16), volume `pgdata`, env from `.env`,
  port 5432.
- Root `Makefile` with targets `verify`, `dev`, `run`, `api`, `e2e` per conventions.md.
  Frontend parts may be no-ops printing "frontend not set up yet (T-019)" until T-019.
- `application.yml` with `app.*` defaults from config-spec §2 (only keys used by now),
  datasource from env, `server.address=127.0.0.1`; `application-local.yml` importing
  `optional:file:../.env[.properties]`.
- Module packages from overview.md, each with `package-info.java` stating its responsibility.
- `common`: `ClockConfig` (`Clock.systemUTC()`), `JobHunterException(code, message)`,
  `HtmlText.toPlain(String html)` (jsoup; p/li/br → newlines; collapse > 2 blank lines).
- `api`: `GlobalExceptionHandler` (`@RestControllerAdvice` → `ProblemDetail` with `code`),
  `GET /api/meta` returning a stub `{ "profileId": null, ... }` (filled in T-002/T-014),
  `SpaFallbackController` per overview.md, OpenAPI info (title, version).
- `OpenApiExportIT` per testing.md (writes/compares `docs/architecture/openapi.json`).
- Flyway `V1__baseline.sql` (comment only; schema in T-003).

## Out of scope
Entities, collectors, LLM, any frontend code.

## Acceptance criteria
- [x] `docker compose up -d db && cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
      starts; `GET /actuator/health` is UP; `GET /api/meta` returns JSON;
      `/swagger-ui.html` loads.
- [x] `GET /jobs/1` (a non-API path) forwards to `index.html` (404 is acceptable only
      while `static/index.html` does not exist — test with a test-only `index.html`).
- [x] `make verify` passes (backend part) without network except Docker.
- [x] Prompt files are on the classpath at `prompts/extract-v1.md`.
- [x] `docs/architecture/openapi.json` is generated and committed.

## Tests
- `HtmlTextTest`, `ApplicationIT` (context + Flyway with Testcontainers),
  `PromptsOnClasspathTest`, `SpaFallbackControllerTest`, `GlobalExceptionHandlerTest`,
  `ArchitectureTest` (rules must be real, not trivially true), `OpenApiExportIT`.

## Docs to update
`AGENTS.md` commands if anything differs. `progress.md`.
