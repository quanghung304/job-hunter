# Progress log

Newest entry on top. Each entry, max ~10 lines:

```
## YYYY-MM-DD — T-XXX <title> — DONE | PARTIAL | BLOCKED
- What was built (key classes/files)
- Deviations from spec and why (and which doc was updated)
- Open issues / follow-ups (create tasks if needed)
- Next suggested task
```

## 2026-10-17 — T-008 We Work Remotely collector (RSS) — DONE
- `source.weworkremotely`: `WeWorkRemotelyCollector` (configured feeds, dedupe by guid,
  per-feed failure = warning), `WeWorkRemotelyFeedParser` (hardened JDOM + Rome; WWR's own
  elements read from JDOM; item → JSON rawPayload), `WeWorkRemotelyMapper` ("Company: Title"
  split, "Unknown" + warning), `WeWorkRemotelyItemDto`, `WeWorkRemotelyProperties`.
- Findings (weworkremotely.md rewritten): description is entity-escaped HTML, not CDATA;
  extra elements `country` (flag emoji), `state`, `skills`, `expires_at`, `media:content`
  → country/state/skills appended to the description; spec feed URLs still valid.
- Terms: RSS page, terms and sitemap are behind a Cloudflare JS challenge (not bypassed);
  the owner read the ToS in a browser: no scraping/feed clause; limited, revocable,
  non-transferable personal-use right → fine for Phase 1, no redistribution (a Phase 4 SaaS
  needs WWR's permission). Catalog and weworkremotely.md updated.
- Tests: `WeWorkRemotelyMapperTest` (7 fixture items, extras, rawPayload, title split, XXE
  rejected), `WeWorkRemotelyCollectorTest` (2 feeds with shared guids → 8 unique of 10,
  "Unknown" company, since, max-items, failing / challenge / all-failing feeds); live passed.
- Next: T-009 (prefilter).

## 2026-10-17 — T-007 Jobicy collector — DONE
- `source.jobicy`: `JobicyCollector` (one request per configured query, cursor while
  `hasMore`, dedupe by id across queries, per-query failure = warning), `JobicyMapper`
  (`parsePubDate`: ISO with offset, zone-less → UTC), DTOs, `JobicyProperties`; live test passed.
- Spec corrections (jobicy.md rewritten): industry slug `dev` does not exist → `engineering`;
  `pubDate` is ISO-8601 with offset; salary keys `salaryMin/Max/Currency/Period` and absent
  when unknown; cursor pagination, count ≤ 200; 7-day window with 3 h delay, newest first
  (so a query stops at its first job older than `since`).
- Terms (fair use): allowed without approval; keep the canonical Jobicy URL and credit.
- Tests: `JobicyMapperTest` (7 fixture items, 6 date formats, errors), `JobicyCollectorTest`
  (overlapping queries → 8 unique of 10, since, max-items, cursor, max-pages, partial and
  total failure, malformed items).
- Next: T-008 (We Work Remotely) or T-009 (prefilter).

## 2026-10-17 — T-006 Himalayas collector — DONE
- `source.himalayas`: `HimalayasCollector` (cursor pagination, optional `HIMALAYAS_API_KEY`
  bearer header), `HimalayasMapper`, `HimalayasJobDto`, `HimalayasPageDto`,
  `HimalayasProperties`; real fixtures captured 2026-10-17; live test passed once.
- Spec corrections (himalayas.md rewritten): browse is **cursor**-paginated (offset
  deprecated), ≤ 20 per request, feed ordered by last update; `guid` is the job URL;
  `salaryPeriod` field; worldwide jobs list all 37 offsets; ~160 new jobs/hour.
- Terms: §30A allows API use; obligations recorded (apply via `applicationLink`, source
  credit, no redistribution). The ATS-over-board merge rule stays (an ATS link only wins when
  we collected the job from the ATS ourselves).
- Owner decisions: walk the newest-first feed (every 2h, ≤ 400 jobs, 2 s/page, ≤ 25 pages)
  instead of keyword search; optional API key via env (`.env.example`, config-spec, owner
  task added).
- Tests: `HimalayasMapperTest` (11 fixture items + edge cases), `HimalayasCollectorTest`
  (WireMock: cursors, since, max-items/pages, empty page, API key header, 500, 429, malformed,
  duplicate guids).
- Next: T-007 (Jobicy), T-008 (WWR) or T-009 (prefilter).

## 2026-10-17 — Free-tier LLMs, quota alerts, model switching (docs) — DONE
- ADR-0008: model registry `config/llm.yaml` (Groq free by default; Cerebras, GitHub
  Models, OpenRouter, Ollama, Claude Haiku as options), OpenAI-compatible client,
  per-purpose assignments switchable at runtime, quota tracking, Telegram + banner alerts.
- Updated: PRD (F2.8, F2.9, G6, G7), config-spec §2/§3/§5, data-model (`llm_call` columns,
  `llm_assignment`, `llm_assignment_event`, `llm_model_state`, `eval_run`, `notification`),
  overview, api-spec, frontend.md, scoring-spec §6/§8, testing, runbook, eval README,
  AGENTS.md, ADR-0004, `.env.example`. Tasks: T-010 rewritten, T-013/T-014/T-016/T-021
  adjusted, new T-022 (backend) and T-023 (frontend).
- Banner, switch dialog and LLM models card are specified in frontend.md but not drawn in
  the mockups yet.

## 2026-10-17 — T-005 Ingest: normalize, fingerprint, dedupe, upsert — DONE
- `common.text.TextNormalizer`, `common.Hashing` (profile reuses it), `ingest.Fingerprint`,
  `JobMerger`, `IngestService` (batches of 50, `JobDescriptionChanged` event), `IngestResult`.
- `POST /api/admin/collect/{sourceCode}` in `AdminController` (runs now, ignores
  min-interval; TODO T-013 move to pipeline); `UnknownSourceException` (404
  `SOURCE_NOT_FOUND`); `CollectorRunner.runNow/defaultRequest`; `openapi.json` regenerated.
- Refinements (data-model §4 rewritten with examples): extended legal suffixes removed from
  the end repeatedly; generic gender tags; re-posting looks up the newest job of the
  fingerprint family; merge fills missing fields and unites tags; EXPIRED → ACTIVE on sighting.
- Real run against Arbeitnow: 2643 fetched, 200 postings → 194 raw, 184 jobs. An immediate
  second run hit Cloudflare 429 on page 4 → PARTIAL as designed; warnings now carry only
  "HTTP 429 …" (`HttpErrors`), Arbeitnow `page-delay` raised to 2s.
- Tests: TextNormalizer (45 rows), Fingerprint (20 same/different pairs), JobMerger,
  IngestServiceIT (13 cases), AdminControllerTest.
- Next: T-006 / T-007 / T-008 (collectors) or T-009 (prefilter, needs T-002 + T-005).

## 2026-10-17 — T-004 Collector framework + Arbeitnow collector — DONE
- `source` API: `JobCollector`, `CollectRequest`, `CollectResult` (+ `fetched`; overview.md
  updated), `NormalizedPosting`; `CollectorRegistry`, `CollectorRunner` (virtual-thread
  timeout, never throws, writes `collector_run`, min-interval skip, `recordIngestCounts`),
  `HttpClients` (+ `app.http.*`, config-spec updated), `Politeness`/`Sleeper`.
- `ArbeitnowCollector`/`Mapper`/`JobDto`/`PageDto`; real fixtures captured 2026-10-17.
- Spec corrections (arbeitnow.md): 300 items/page; items not strictly ordered by `created_at`
  → old items are skipped and paging stops only when a whole page is older than `since`;
  end of data = empty page; page number incremented locally; added `max-pages` (20) cap.
- `HtmlText` now decodes double-escaped HTML (seen in Arbeitnow descriptions).
- Owner decision: `remote: false` + location containing "remote" counts as remote
  (`ArbeitnowMapper.isRemote`, used by mapper and `remote-only`); arbeitnow.md updated.
- Tests: mapper (fixture table), collector (WireMock: paging, since, max-items/pages, 500,
  timeout, non-JSON, later-page error, malformed → PARTIAL), runner, registry, runner IT;
  live test passed once.
- Next: T-005 (ingest).

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
