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
 │   Tracking   │ ───► │    Notify    │  Telegram digest / alerts
 │ status, UI   │      └──────────────┘
 └──────────────┘
```

## Modules (packages)

| Package | Responsibility | May depend on |
|---|---|---|
| `common` | Clock, ids, text utils (HTML → text, normalization), errors | — |
| `profile` | Load and validate `profile.yaml`; `ProfileProvider`, `Profile` record | common |
| `source` | `JobCollector` interface, one sub-package per source, `CollectorRegistry`, `collector_run` logging | common |
| `ingest` | Store `raw_posting`, compute fingerprint, upsert `job`, link sources, expiry | common, source (DTOs only) |
| `llm` | `LlmClient` interface, Anthropic implementation, `FakeLlmClient`, prompt loading, `llm_call` log, budget guard | common |
| `enrich` | Prefilter rules, extraction (LLM), eligibility evaluator; owns `job_assessment` via `AssessmentService` | common, profile, llm, ingest (read) |
| `scoring` | Scoring (LLM); writes scores through `AssessmentService` | common, profile, llm, enrich |
| `tracking` | `job_tracking` status machine, notes, history; `JobBoardQuery` (read-only SQL joining job + assessment + tracking for the UI) | common, profile, ingest (read), enrich (read) |
| `notify` | Telegram client, digest and alert builders, sent-notification log | common, profile, tracking (read) |
| `pipeline` | `PipelineOrchestrator`, scheduler, admin trigger; the only place that calls modules in sequence | all above |
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
public record CollectResult(List<NormalizedPosting> postings, List<String> warnings,
                            CollectStatus status, String errorMessage) {}

public interface LlmClient {
    <T> LlmResult<T> completeJson(LlmRequest request, Class<T> responseType);
}

public interface ProfileProvider { Profile current(); }
```

`NormalizedPosting` fields are defined in `data-model.md` §3.

## Pipeline run (one pass)

1. For each enabled collector (sequentially; each with its own timeout and try/catch):
   collect → ingest. Record `collector_run`.
2. Select jobs needing work for the current profile:
   - no `job_extraction` for the current extraction prompt version → prefilter → extract
   - extraction exists but no eligibility/score for current versions → evaluate → score
3. Respect the budget guard: stop LLM work when the per-run or monthly limit is hit;
   leftover jobs are processed on the next run. Order of LLM work: newest `posted_at`
   first, because fresh jobs matter most.
4. Mark expired jobs.
5. Send instant alerts for new scores ≥ alert threshold. The daily digest is a separate
   scheduled job.

A run is idempotent: running twice in a row does no LLM work the second time.

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
