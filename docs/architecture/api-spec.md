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
  `PIPELINE_ALREADY_RUNNING`. Domain errors carry the code of their `JobHunterException`
  (HTTP status from `httpStatus()`, default 400). Framework errors use `VALIDATION_FAILED`
  for Bean Validation failures and otherwise the HTTP status name (`NOT_FOUND`,
  `BAD_REQUEST`, `METHOD_NOT_ALLOWED`, ...). Unexpected errors are 500 `INTERNAL_ERROR`
  with a generic `detail` (the exception message is only logged).
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
  "pipelineRunning": false,
  "pipelineProgress": { "stage": "SCORE", "text": "Collect 7/9 sources · scoring 14 / 27", "percent": 62 } | null,
  "schedule": { "nextRunAt": "...", "nextDigestAt": "...", "zone": "Europe/Rome" },
  "kpis": { "activeJobs": 1384, "newToday": 42, "prefilterRejectedToday": 31,
            "scoredToday": 9, "alertsToday": 2, "toReview": 37,
            "sourceCount": 9, "companyBoardCount": 6 },
  "llmAlerts": [ LlmAlert, ... ] }
```

`LlmAlert` (also returned by `GET /api/status`, below):
```json
{ "purpose": "EXTRACT", "modelId": "groq-gpt-oss-120b", "modelLabel": "GPT-OSS 120B (Groq)",
  "status": "EXHAUSTED", "detail": "Daily request limit 1000/1000", "until": "2026-10-18T00:00:00Z",
  "paused": true, "pendingJobs": 23,
  "suggestions": [{ "modelId": "openrouter-nemotron-super", "label": "...", "tier": "FREE", "status": "AVAILABLE" }] }
```
One entry per purpose whose assigned model is not `AVAILABLE`. `paused` = LLM work for that
purpose is waiting. `suggestions` = usable models from the assignment's fallbacks, then
other usable registry models (FREE/LOCAL before PAID).

The app header's status line ("Last run 07:00 · 42 new · 9 scored") uses the newest
`pipelineReports` entry. "Today" = since local midnight in `app.pipeline.zone`.
`toReview` = count of the board's default view.

### `POST /api/admin/pipeline/run`
Starts a run asynchronously → 202 `{ "accepted": true }`; 409 `PIPELINE_ALREADY_RUNNING`.
The UI polls `GET /api/admin/overview` (`pipelineRunning`, newest report).

### `POST /api/admin/collect/{sourceCode}`
Runs one collector + ingest (debug) → 200 `{ runId, collectStatus, fetched, newRaw, newJobs,
updatedJobs, skippedInvalid, warnings, error }` (`error` null unless FAILED). Ignores
`min-interval` and the `enabled` flag; collects postings newer than `prefilter.maxAgeDays`.
404 `SOURCE_NOT_FOUND` for an unknown source code.

### `POST /api/admin/rescore`
Body `{ "dryRun": true }` → `{ "jobCount": 57, "estimatedCalls": 57, "estimatedCostUsd": 0.09 }`
(cost = average `cost_usd` of SCORE calls in the last 30 days × calls; null if no history); with
`dryRun: false` → 202.

### `POST /api/admin/jobs/{id}/retry`
Body `{ "purpose": "EXTRACT" | "SCORE" }` → resets attempts and re-runs for that job → 202.

### `GET /api/admin/companies`
`[{ id, name, ats, slug, enabled, lastCheckStatus, lastCheckedAt, activeJobs }]`

### `GET /api/status`
Lightweight, polled by the app header on every page every 60 s (every 10 s while a banner
is shown): `{ "lastRun": { "startedAt", "newJobs", "scored", "status" } | null,
"pipelineRunning": false, "llmAlerts": [LlmAlert] }`.

## LLM models (ADR-0008)

### `GET /api/admin/llm`
```json
{ "assignments": [
    { "purpose": "EXTRACT", "modelId": "groq-gpt-oss-120b", "fallbacks": ["openrouter-nemotron-super", "claude-haiku"],
      "autoFallback": false, "allowPaid": false, "updatedAt": "...", "updatedBy": "user" },
    { "purpose": "SCORE", ... } ],
  "models": [
    { "modelId": "groq-gpt-oss-120b", "label": "GPT-OSS 120B (Groq)", "provider": "groq",
      "providerModel": "openai/gpt-oss-120b", "tier": "FREE",
      "status": "NEAR_LIMIT", "until": null, "detail": "812 / 1000 requests today",
      "limits": { "rpm": 30, "rpd": 1000 },
      "usageToday": { "calls": 812, "inputTokens": 2400000, "outputTokens": 310000, "costUsd": null },
      "keyConfigured": true, "assignedTo": ["EXTRACT", "SCORE"],
      "lastEval": { "date": "2026-10-25", "falseEligible": 0.024, "highRecall": 0.83 } | null } ],
  "history": [{ "purpose", "fromModelId", "toModelId", "reason", "at" }] }
```
`lastEval` is read from the newest matching entry the eval runner records (T-016).

### `PUT /api/admin/llm/assignments/{purpose}`
Body `{ "modelId": "openrouter-nemotron-super", "fallbacks": [...], "autoFallback": false, "allowPaid": false }`
(all but `modelId` optional) → 200 updated assignment. Errors: 400 `UNKNOWN_MODEL`,
409 `MODEL_UNUSABLE` (status `ERROR`, e.g. missing key). A manual switch to a PAID model is
allowed (`allowPaid` only limits auto-fallback); the response then includes
`"warning": "PAID model: counts against the monthly budget"`.
Switching writes `llm_assignment_event` (reason `USER`) and un-pauses that purpose; pending
jobs are picked up by the next run, or immediately if the body has `"runNow": true`.

### `POST /api/admin/llm/models/{modelId}/test`
Sends one tiny prompt ("Reply with {\"ok\":true}") → 200 `{ ok, latencyMs, status }`; a
success clears `ERROR`/expired statuses. Logged in `llm_call` with purpose `TEST`.

### `POST /api/admin/eval/export`
Writes `data/eval-candidates.jsonl` (T-016) → `{ "path": "...", "count": 80 }`.
