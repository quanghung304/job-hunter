# Architecture overview

A single Spring Boot process (modular monolith, `backend/`) with a PostgreSQL database,
exposing a REST API under `/api` and serving the React SPA (`frontend/`, built into the
backend's static resources). Base package: `io.github.quanghung304.jobhunter`.
API contract: `api-spec.md`. Frontend: `frontend.md`. Decision: ADR-0007.

```
 Browser (React SPA) ──/api──► backend (Spring Boot) ──► PostgreSQL
                                    │
                                    ├──► job sources (public APIs/RSS)
                                    ├──► Anthropic API (via LlmClient)
                                    └──► Telegram Bot API
```

## Pipeline

```
 Scheduler / admin trigger
          │
          ▼
 ┌──────────────┐  NormalizedPosting  ┌──────────────┐   job (upserted)   ┌──────────────┐
 │  Collectors  │ ──────────────────► │    Ingest    │ ─────────────────► │  Prefilter   │
 │ (per source) │                     │ raw → dedupe │                    │ (rules, $0)  │
 └──────────────┘                     └──────────────┘                    └──────┬───────┘
                                                                                 │ pass
                                                                                 ▼
 ┌──────────────┐   job_score         ┌──────────────┐   job_extraction   ┌──────────────┐
 │   Scoring    │ ◄────────────────── │ Eligibility  │ ◄───────────────── │  Extraction  │
 │ (LLM, per    │   ELIGIBLE /        │ (rules vs    │                    │ (LLM, per    │
 │  profile)    │   UNCERTAIN only    │  profile)    │                    │  job, cached)│
 └──────┬───────┘                     └──────────────┘                    └──────────────┘
        │
        ▼
 ┌──────────────┐      ┌──────────────┐
 │   Tracking   │ ───► │    Notify    │  Telegram digest
 │ status, UI   │      └──────────────┘
 └──────────────┘
```

## Modules (packages)

| Package | Responsibility | May depend on |
|---|---|---|
| `common` | Clock, `Sleeper`, ids, text utils (HTML → text, normalization), errors, shared value enums (`common.model`: `EmploymentType`, `Seniority`, `SalaryPeriod`, `LanguageLevel`, `RemoteHint`), the `RunCompleted` event (pipeline → notify) | — |
| `profile` | Load and validate `profile.yaml`; `ProfileProvider`, `Profile` record | common |
| `source` | `JobCollector` interface, one sub-package per source, `CollectorRegistry`, `collector_run` logging | common |
| `ingest` | Store `raw_posting`, compute fingerprint, upsert `job`, link sources, expiry; `JobQueryService` returns read-only `JobView`s to other modules | common, source (DTOs only) |
| `llm` | `LlmClient` interface; `AnthropicLlmClient`, `OpenAiCompatibleLlmClient`, `FakeLlmClient`; `ModelRegistry` (`config/llm.yaml`); `LlmRouter` (purpose → assigned model → client, fallback); `QuotaTracker` + `llm_model_state`; `llm_assignment`; prompt loading; `llm_call` log; budget guard; publishes `LlmModelStatusChanged` events (ADR-0008) | common |
| `enrich` | Prefilter rules, extraction (LLM), eligibility evaluator; owns `job_assessment` via `AssessmentService` | common, profile, llm, ingest (read) |
| `scoring` | Scoring (LLM, owner action); writes scores through `AssessmentService` | common, profile, ingest (read), llm, enrich |
| `tracking` | `job_tracking` status machine, notes, history; `JobBoardQuery` (read-only SQL joining job + assessment + tracking for the UI) | common, profile, ingest (read), enrich (read) |
| `notify` | Telegram client, digest builder, LLM quota alerts (listens to `LlmModelStatusChanged`), sent-notification log | common, profile, tracking (read), llm (events, read) |
| `pipeline` | `PipelineOrchestrator` (+ Postgres advisory lock, last 20 `PipelineReport`s in memory), scheduler and startup catch-up, admin trigger; the only place that calls modules in sequence; never reaches the LLM services (ArchUnit) | all above |
| `api` | REST controllers (`/api/**`), request/response DTOs, `ProblemDetail` error mapping, OpenAPI config, SPA fallback controller | tracking, scoring, enrich, ingest, pipeline, source, llm (usage stats), profile |
| `eval` | Offline eval runner over labeled jobs | enrich, scoring, llm |

Rules:
- Modules call each other through public service interfaces in the module's root package,
  never through another module's repositories or entities.
- Only `pipeline` orchestrates. Collectors never call ingest; enrich never calls scoring.
- `api` contains no business logic; it maps HTTP ⇄ service calls and DTOs.

## Serving the SPA

- `make run` builds `frontend/` (`npm run build` → `frontend/dist`) and the Maven profile
  `with-frontend` copies `dist` into `backend/target/classes/static`.
- `SpaFallbackController` forwards any GET that is not `/api/**`, `/actuator/**`,
  `/v3/api-docs/**` or a file with an extension to `/index.html`, so React Router deep links
  (`/jobs/42`) work on reload.
- Dev: Vite on :5173 proxies `/api` to :8080; the backend needs no CORS config.
- `server.address=127.0.0.1` (no auth in Phase 1).

## Key interfaces (shape, not final code)

```java
public interface JobCollector {
    String sourceCode();                       // e.g. "arbeitnow"; matches catalog
    CollectResult collect(CollectRequest req); // never throws for source errors; reports them
}
public record CollectRequest(Instant since, List<String> keywords, int maxItems) {}
public record CollectResult(List<NormalizedPosting> postings, int fetched,  // fetched includes skipped items
                            List<String> warnings, CollectStatus status, String errorMessage) {}

// llm module: callers use the router; clients send exactly one HTTP request each
public class LlmRouter {
    <T> LlmOutcome<T> call(LlmPurpose purpose, String promptId, Map<String, String> variables,
                           Long jobId, Class<T> responseType); // LlmResult | LlmUnavailable | LlmFailed
}
interface LlmClient { LlmResponse send(LlmRequest request); }  // only LlmRouter calls it (ADR-0009)

public interface ProfileProvider { Profile current(); }
```

`NormalizedPosting` fields are defined in `data-model.md` §3.

## Pipeline run (one pass, zero LLM calls)

1. For each enabled collector (sequentially; each with its own timeout and try/catch):
   collect → ingest. Record `collector_run`.
2. Prefilter new or changed ACTIVE jobs for the current profile (rules, no LLM).
3. Mark expired jobs.
4. Build digest data: new jobs not yet analyzed, and scored jobs above the digest threshold.
   The daily digest is a separate scheduled job.

A run never calls an LLM (ADR-0010). Running it twice in a row changes nothing unless new jobs arrived.

## LLM work on owner action (ADR-0010)

- **Analyze** (`POST /api/jobs/{id}/analyze`): extraction (one EXTRACT call, cached per
  prompt version), then eligibility (Java). Each call goes through `LlmRouter`.
- **Score** (`POST /api/jobs/{id}/score`): one SCORE call, only for an analyzed job that is
  ELIGIBLE or UNCERTAIN.
- **Bulk** (admin only, with a dry run showing the count first): "Re-score active jobs",
  "Retry failed", and bulk analysis and scoring of jobs the admin selects in a table.
- Before each call, `LlmRouter` resolves the assigned model for the purpose. If that model is
  `RATE_LIMITED`, `EXHAUSTED` or `ERROR`, it uses the first usable fallback when `autoFallback`
  is on (PAID only if `allowPaid`). Otherwise the call is refused with `LLM_PAUSED` and the job
  stays unanalyzed. Extraction and scoring pause independently.
- The budget guard applies to each call. At the limit, the call is refused with `BUDGET_REACHED`.

## Scheduling

- Pipeline: cron from `app.pipeline.cron` (default `0 0 7,19 * * *`, Europe/Rome).
- Digest: `app.notify.digest-cron` (default `0 30 8 * * *`).
- On startup, if the last successful run is older than `app.pipeline.catch-up-after`
  (default 6h), run once immediately (laptop was asleep).
- A DB advisory lock prevents two pipeline runs at once.

## Cross-cutting

- Config: `@ConfigurationProperties` records, validated at startup (`config-spec.md`).
- Logging: SLF4J, one line per collector run and per LLM call with ids; no job
  descriptions or prompt bodies at INFO level.
- Errors: collectors return failed `CollectResult`; LLM failures mark the job for retry
  with backoff (max 3 attempts per prompt version), never crash the run.
- HTTP: one `RestClient` bean per source with timeouts (connect 5s, read 20s) and a
  User-Agent `job-hunter/0.1 (+personal use; contact: <owner email from env>)`.
