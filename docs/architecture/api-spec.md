# REST API spec

Base path `/api`. JSON only. The generated `docs/architecture/openapi.json` is the exact
contract; this file is the human-readable design. If they differ, fix the code or this file
in the same commit.

## Conventions

- Field names camelCase; timestamps ISO-8601 UTC strings; enums as upper-case strings.
- Profile: Phase 1 always uses `ProfileProvider.current()`; no profile in URLs. (Phase 4 adds
  auth and derives the profile from the user.)
- Pagination: `page` (0-based), `size` (default 25, max 100) → response
  `{ "items": [...], "page": 0, "size": 25, "totalItems": 123, "totalPages": 5 }`.
- Errors: RFC 9457 Problem Details (`application/problem+json`) via Spring's
  `ProblemDetail`: `{ "type", "title", "status", "detail", "code" }` where `code` is a stable
  machine string, e.g. `ILLEGAL_STATUS_TRANSITION`, `JOB_NOT_FOUND`, `VALIDATION_FAILED`,
  `PIPELINE_ALREADY_RUNNING`.
- No auth in Phase 1; server binds to `127.0.0.1`.
- Controllers live in the backend `api` module and call module services only.

## Jobs

### `GET /api/jobs`
Query (all optional): `status` (repeatable), `eligibility` (repeatable), `minScore`,
`maxScore`, `source` (repeatable), `employmentType` (repeatable), `postedWithinDays`, `q`
(title/company text), `includeExpired` (default false), `sort` (`score` | `posted`, default
`score`), `page`, `size`. With no filters, the default view of T-020 applies: status ∈
{NEW, SHORTLISTED}, eligibility ≠ INELIGIBLE, ACTIVE, score ≥ 50.

Item `JobSummary`:
```json
{ "id": 42, "title": "...", "company": "...", "locationText": "...",
  "score": 82, "scoreBand": "GOOD", "summary": "...",
  "eligibility": "ELIGIBLE", "remoteType": "REMOTE_RESTRICTED", "allowedRegions": ["EU"],
  "employmentTypes": ["CONTRACT"], "postedAt": "...", "firstSeenAt": "...",
  "sources": [{ "code": "himalayas", "name": "Himalayas" }],
  "status": "NEW", "jobStatus": "ACTIVE" }
```
`scoreBand`: `EXCELLENT` ≥ 85, `GOOD` 70–84, `MAYBE` 50–69, `WEAK` < 50, `NONE` if unscored.

### `GET /api/jobs/{id}`
`JobDetail` = `JobSummary` fields plus: `description`, `applyUrl`, `salary`, `tags`,
`extraction` (attributes object, scoring-spec §3), `eligibilityReasons` (string[]),
`prefilterReasons`, `scoreBreakdown`, `strengths`, `gaps`, `redFlags`, `applyAdvice`,
`postings` (`[{ sourceCode, sourceName, url, firstSeenAt, lastSeenAt }]` — the UI shows
"via <sourceName>" links), `notes`, `history` (`[{ from, to, note, at }]`),
`allowedTransitions` (statuses the UI may offer).

### `POST /api/jobs/{id}/status`
Body `{ "to": "SHORTLISTED", "note": "optional" }` → 200 with updated `JobSummary`;
409 `ILLEGAL_STATUS_TRANSITION`; 404 `JOB_NOT_FOUND`.

### `PUT /api/jobs/{id}/notes`
Body `{ "notes": "text ≤ 10 000 chars" }` → 204.

### `GET /api/meta`
Values for filter controls: `{ "sources": [{code,name}], "statuses": [...],
"employmentTypes": [...], "eligibilities": [...], "profileId": "hugh",
"summaryLanguage": "vi" }`.

## Admin

### `GET /api/admin/overview`
```json
{ "sources": [{ "code", "name", "enabled", "lastRun": { "status", "startedAt", "fetched",
  "newRaw", "newJobs", "error" } }],
  "pipelineReports": [ PipelineReport, ... up to 20 ],
  "llmUsage": { "monthToDateUsd", "monthlyBudgetUsd", "callsToday",
                "daily": [{ "date", "calls", "inputTokens", "outputTokens", "costUsd" }] },
  "failures": { "extractions": [{ jobId, title, attempts, lastError }], "scorings": [...] },
  "pipelineRunning": false }
```

### `POST /api/admin/pipeline/run`
Starts a run asynchronously → 202 `{ "accepted": true }`; 409 `PIPELINE_ALREADY_RUNNING`.
The UI polls `GET /api/admin/overview` (`pipelineRunning`, newest report).

### `POST /api/admin/collect/{sourceCode}`
Runs one collector + ingest (debug) → 200 `{ collectStatus, fetched, newRaw, newJobs, warnings }`.

### `POST /api/admin/rescore`
Body `{ "dryRun": true }` → `{ "jobCount": 57, "estimatedCalls": 57 }`; with
`dryRun: false` → 202.

### `POST /api/admin/jobs/{id}/retry`
Body `{ "purpose": "EXTRACT" | "SCORE" }` → resets attempts and re-runs for that job → 202.

### `GET /api/admin/companies`
`[{ id, name, ats, slug, enabled, lastCheckStatus, lastCheckedAt, activeJobs }]`

### `POST /api/admin/eval/export`
Writes `data/eval-candidates.jsonl` (T-016) → `{ "path": "...", "count": 80 }`.
