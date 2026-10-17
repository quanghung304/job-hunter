# Progress log

Newest entry on top. Each entry, max ~10 lines:

```
## YYYY-MM-DD — T-XXX <title> — DONE | PARTIAL | BLOCKED
- What was built (key classes/files)
- Deviations from spec and why (and which doc was updated)
- Open issues / follow-ups (create tasks if needed)
- Next suggested task
```

## 2026-10-17 — UI design approved — DONE
- Mockups approved in the Claude Design canvas "Job Hunter UI mockups" (5 artboards).
- `frontend.md` now holds design tokens, typography, component specs, layouts and
  behavior ("inbox triage", split view ≥ 1024px). Added `o` and `Esc` shortcuts.
- `api-spec.md`: overview gained `kpis`, `schedule`, `pipelineProgress`; re-score dry run
  gained `estimatedCostUsd`. T-014, T-019, T-020, T-021 updated accordingly.

## 2026-10-17 — T-003 Database schema and persistence layer — DONE
- Flyway V2–V5 (all Phase 1 tables, §5 indexes + a few FK/lookup indexes, check constraints
  on every enum column). Entities/repositories in `<module>.persistence`: source (CollectorRun,
  Company), ingest (Job, RawPosting), enrich (JobExtraction, JobAssessment), tracking
  (JobTracking with `JobTrackingId`, JobTrackingEvent), notify (Notification), llm (LlmCall).
  Cross-module references are plain `Long` ids. Module enums in each module's root package.
- `TrackingStatusMachine` + `IllegalStatusTransitionException` (409); test covers all 64 pairs.
- New ArchUnit rule: `<module>.persistence` is private to its module (with a violating fixture).
- Schema deviations (data-model.md updated): `notification.profile_id` added (needed for
  "alerted once per profile"); `job_assessment` unique is `nulls not distinct`;
  `collector_run.status` null while running; `llm_call.job_id` FK on delete set null.
- Tests: 9 `*RepositoryIT` (round trip incl. jsonb/arrays, unique, check, FK) on Testcontainers.
- Next: T-004 (or T-010 / T-019 in parallel).

## 2026-10-16 — T-002 Profile loading and validation — DONE
- `profile`: `Profile` (nested records, defaults in compact constructors), `ProfileProvider`,
  `YamlProfileProvider` (Jackson YAML + Bean Validation, all problems listed by field path,
  unknown keys rejected), `RegionMatcher`/`RegionVerdict`, `CandidateBlock`,
  `InvalidProfileFailureAnalyzer` (short "APPLICATION FAILED TO START" report).
- Shared enums in `common.model` (`EmploymentType`, `Seniority`, `SalaryPeriod`, `LanguageLevel`)
  because `source` may not depend on `profile`; overview.md updated.
- Deviations: record component is `notifySettings` (`notify` is illegal), YAML key unchanged;
  `app.profile.path` bound as String (Spring's String→Path rejects `../`); `CandidateBlock`
  also redacts e-mails/phone numbers in free text; `/api/meta` now returns `profileId` and
  `summaryLanguage` (openapi.json regenerated); config-spec/runbook notes added.
- Region membership known for EU/EEA/EUROPE/EMEA only; other tokens match via acceptRegions.
- Owner: copy `config/profile.example.yaml` to `config/profile.yaml`, or the app will not start.
- Next: T-003.

## 2026-10-16 — T-001 Backend bootstrap — DONE
- `backend/`: Spring Boot 3.5.16 (latest 3.x), Java 21, Maven wrapper 3.9.16; springdoc 2.9.1,
  wiremock-jetty12 3.13.2, archunit 1.5.1, jsoup 1.23.2, rome 2.1.0; profiles `live`, `with-frontend`.
- `common`: `ClockConfig`, `JobHunterException` (+ overridable `httpStatus()`, default 400),
  `common.text.HtmlText`. `api`: `GlobalExceptionHandler`, `MetaController` (stub),
  `SpaFallbackController` (regex segments, depth ≤ 4), `OpenApiConfig` (fixed server `/`).
- Root `docker-compose.yml`, `Makefile`, `.gitattributes`; `V1__baseline.sql`; `openapi.json`.
- Tests: 49 unit + 7 IT green; `ArchitectureTest` encodes the overview.md module table and
  proves itself against a violating fixture (`io.github.quanghung304.archfixture`).
- Deviation: api-spec.md now documents error codes for framework/unexpected errors.
- Note: `app.*` has no keys yet (none are read); each task adds its own.
- Next: T-002 / T-003 (T-019 can start in parallel).

## 2026-10-16 — Switch UI to React SPA — DONE
- ADR-0007 supersedes ADR-0006 (UI part): monorepo `backend/` + `frontend/`, REST API under
  `/api`, OpenAPI contract at `docs/architecture/openapi.json`, React + Vite + TanStack Query.
- Added `api-spec.md`, `frontend.md`; updated AGENTS, overview, conventions, testing, runbook.
- Old T-014 (Thymeleaf UI) replaced by T-014 (REST API), T-019 (FE bootstrap),
  T-020 (board + detail), T-021 (admin).
- Next: T-001.

## 2026-10-16 — Documentation set — DONE
- PRD, roadmap, architecture, data model, config spec, ADR-0001..0006, source specs,
  scoring spec, prompts v1 (draft), eval plan, task specs T-001..T-018.
- Source field shapes are **unverified**; each collector task starts by capturing a real response.
- Next: T-001.
