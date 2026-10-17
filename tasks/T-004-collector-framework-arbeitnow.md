# T-004: Collector framework + Arbeitnow collector

Phase: 1 · Depends on: T-003 · Size: M

## Goal
A reusable collector framework and the first working collector (Arbeitnow), producing
`NormalizedPosting`s and recording `collector_run` rows.

## Read first
- `docs/architecture/overview.md` (Key interfaces, Cross-cutting HTTP)
- `docs/architecture/data-model.md` §3 · `docs/sources/arbeitnow.md`
- `docs/conventions.md` (Collectors) · `docs/testing.md` (Fixtures)

## In scope
- `source` API: `JobCollector`, `CollectRequest`, `CollectResult`, `CollectStatus`,
  `NormalizedPosting`, `RemoteHint`, `SalaryPeriod`.
- `CollectorRegistry` (all `JobCollector` beans, filtered by `app.sources.<code>.enabled`).
- `CollectorRunner`: runs one collector with timeout (`app.pipeline.collector-timeout`),
  catches everything, writes `collector_run` (start, finish, status, fetched_count, error).
  Returns the run id. `CollectorRunner.recordIngestCounts(runId, int newRaw, int newJobs)`
  is called afterwards by whoever ran ingest (temporary admin endpoint in T-005, pipeline
  in T-013) — `source` must not depend on `ingest`.
- `min-interval` check: skip a collector if its last SUCCESS run is more recent.
- `HttpClients` factory: `RestClient` with timeouts and the User-Agent from `OWNER_CONTACT_EMAIL`.
- `Politeness` helper: sleeps between requests (inject a `Sleeper` for tests).
- `ArbeitnowCollector`, `ArbeitnowJobDto`, `ArbeitnowMapper` per the source spec.

## Steps
1. **Capture a real response**: fetch page 1 once (curl), trim to ~6 items covering:
   remote true, remote false, HTML description, missing location. Save to
   `backend/src/test/resources/fixtures/arbeitnow/page1.json`; also a page with `links.next: null`.
   Compare with `docs/sources/arbeitnow.md`; fix the spec where it differs.
2. Build the framework classes.
3. Build the collector, mapper; config `app.sources.arbeitnow.*`.
4. Add `ArbeitnowCollectorLiveTest` tagged `live`.

## Out of scope
Persisting postings (T-005), other sources.

## Acceptance criteria
- [x] Collector stops on: no next page, item older than `since`, `max-items`.
- [x] `remote-only` option skips non-remote items.
- [x] A 500 or timeout yields `CollectResult(FAILED, message)`; no exception escapes.
- [x] `collector_run` row written for success and failure.

## Tests
- `ArbeitnowMapperTest` (each fixture item → expected record; HTML stripped; epoch → Instant).
- `ArbeitnowCollectorTest` with WireMock: pagination, stop conditions, 500, timeout, malformed item → PARTIAL.
- `CollectorRunnerTest` (timeout, exception, min-interval skip).

## Docs to update
`docs/sources/arbeitnow.md` (verified shape, fixture date) · catalog "Verified" · progress.
