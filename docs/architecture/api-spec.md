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
  The board requests `size=12` (one grid page, and each "Load more" step in the job list panel).
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

The eligibility model of ADR-0011 (checks + owner decision) is implemented since T-025; the
earlier verdict field and filter `eligibility` are gone.

### `GET /api/jobs`
Query (all optional): `status` (repeatable), `decision` (repeatable: `ELIGIBLE`, `INELIGIBLE`,
`UNDECIDED`), `checks` (repeatable: `ALL_OK`, `HAS_UNKNOWN`, `HAS_FAIL`, `NOT_CHECKED`), `minScore`,
`maxScore`, `source` (repeatable), `employmentType` (repeatable), `postedWithinDays`, `q`
(title/company text), `includeExpired` (default false), `analysisStage` (repeatable), `sort` (`score` | `posted`, default
`score`), `page`, `size`. With no filters, the default view applies: status ≠ DISMISSED, prefilter PASS,
decision ≠ INELIGIBLE, ACTIVE. Scored jobs come first, by score; jobs not yet analyzed follow, by `postedAt`.
Any filter (everything except `sort`, `page`, `size`) replaces the default view; EXPIRED jobs stay hidden unless
`includeExpired=true`. `q` is a case-insensitive substring of title or company (`%` and `_` are literal);
`postedWithinDays` uses `postedAt`, or `firstSeenAt` when the source gave no date; `employmentType` matches the
extracted `employmentTypes` (unanalyzed jobs never match); `sort=posted` orders by the same date, newest first.
Ranges: `minScore`/`maxScore` 0–100, `postedWithinDays` ≥ 1, `page` ≥ 0, `size` 1–100, `q` ≤ 200 chars; otherwise
400 `VALIDATION_FAILED` (an unknown enum value is 400 `BAD_REQUEST`). Every value is read for the current profile:
assessment, score and tracking status of other profiles are never used.

Item `JobSummary`:
```json
{ "id": 42, "title": "...", "company": "...", "locationText": "...",
  "score": 82, "scoreBand": "GOOD", "summary": "...",
  "eligibilityDecision": "ELIGIBLE" | "INELIGIBLE" | null,
  "checks": { "ok": 6, "unknown": 1, "fail": 0 } | null,
  "remoteType": "REMOTE_RESTRICTED", "allowedRegions": ["EU"],
  "employmentTypes": ["CONTRACT"], "postedAt": "...", "firstSeenAt": "...",
  "sources": [{ "code": "himalayas", "name": "Himalayas" }],
  "status": "NEW", "jobStatus": "ACTIVE", "analysisStage": "NOT_ANALYZED" }
```
`scoreBand`: `EXCELLENT` ≥ 85, `GOOD` 70–84, `MAYBE` 50–69, `WEAK` < 50, `NONE` if unscored.
`checks` counts the eligibility check results (null when not analyzed).
`analysisStage` (derived, ADR-0010): `NOT_ANALYZED` (no LLM call yet) · `ANALYZED`
(extraction and eligibility checks done, not scored) · `SCORED` · `FAILED` (last attempt failed; retry available).
Derivation: score present → `SCORED`; last SCORE attempt failed without a score → `FAILED`; checks present →
`ANALYZED`; extraction for the current prompt version failed → `FAILED`; otherwise `NOT_ANALYZED`.
`remoteType`, `allowedRegions` and `employmentTypes` come from the extraction (null / empty until analyzed);
`sources` lists each source once, by code. `scoreBand` is computed by `tracking.ScoreBand` (also for Telegram).

### `GET /api/jobs/{id}`
`JobDetail` = `JobSummary` fields plus: `description`, `applyUrl`, `salary`, `tags`,
`extraction` (attributes object, scoring-spec §3), `eligibilityChecks`
(`[{ code, critical, result, value, reason }]` in scoring-spec §4 order, empty when not analyzed),
`eligibilityDecidedAt` (null when undecided),
`prefilterReasons`, `scoreBreakdown`, `strengths`, `gaps`, `redFlags`, `applyAdvice`,
`postings` (`[{ sourceCode, sourceName, url, firstSeenAt, lastSeenAt }]` — the UI shows
"via <sourceName>" links), `notes`, `history` (`[{ kind: "STATUS" | "ELIGIBILITY", from, to, note, at }]`),
`allowedTransitions` (statuses the UI may offer).
`salary` is `{ min, max, currency, period }` from the source, or null; `extraction` is null until an OK extraction
exists for the current prompt version (extractions are shared by all profiles); `scoreBreakdown` is
`{ skills, experience, arrangement, domain, other }`; the score fields are null / empty when unscored.
404 `JOB_NOT_FOUND`.

### `POST /api/jobs/{id}/status`
Body `{ "to": "SHORTLISTED", "note": "optional" }` (note ≤ 2 000 chars) → 200 with updated `JobSummary`;
409 `ILLEGAL_STATUS_TRANSITION` (also for the current status); 404 `JOB_NOT_FOUND`. Each change is written to the
history.

### `PUT /api/jobs/{id}/eligibility`
Owner decision (ADR-0011). Body `{ "decision": "ELIGIBLE" | "INELIGIBLE" | null, "note": "optional" }`
→ 200 updated `JobSummary`. Allowed in every analysis stage. Setting the current value is a
no-op (200, no history entry). Errors: 404 `JOB_NOT_FOUND`.

### `POST /api/jobs/{id}/analyze`
Owner action "Analyze" (ADR-0010). Runs extraction if no OK extraction exists for the
current prompt version (one EXTRACT call), then the eligibility checks (no LLM). No body → 200 `JobDetail`.
Repeating it makes no LLM call. A failed attempt (invalid output after the corrective retry,
provider errors) still answers 200 with `analysisStage: FAILED`; after 3 failed attempts for the
prompt version no further call is made. Errors: 404 `JOB_NOT_FOUND`; 409 `JOB_EXPIRED`; 409 `JOB_PREFILTER_REJECTED`
(no call: the prefilter rejects the job for this profile); 409 `LLM_PAUSED` (the Problem Details carry
`purpose` and, since T-022, `alert` = the `LlmAlert` of that purpose); 409 `BUDGET_REACHED`.

### `POST /api/jobs/{id}/score`
Owner action "Score" (ADR-0010). Requires an analyzed job the owner has not marked INELIGIBLE
(ADR-0011); makes one SCORE call → 200 `JobDetail`. Repeating it makes no call.
Errors: 404 `JOB_NOT_FOUND`; 409 `JOB_EXPIRED`; 409 `JOB_NOT_ANALYZED`; 409 `JOB_MARKED_INELIGIBLE`; 409 `LLM_PAUSED`;
409 `BUDGET_REACHED`.

### `PUT /api/jobs/{id}/notes`
Body `{ "notes": "text ≤ 10 000 chars" }` → 204 (an empty string clears the notes; allowed in every status);
404 `JOB_NOT_FOUND`; 400 `VALIDATION_FAILED` when longer.

### `GET /api/meta`
Values for filter controls: `{ "sources": [{code,name}], "statuses": [...],
"employmentTypes": [...], "decisions": [...], "checkResults": [...], "profileId": "hugh",
"summaryLanguage": "vi" }`. `sources` = every registered collector (enabled or not), by code.

## Profile (ADR-0012)

Only the current (owner's) profile. No list, create or delete endpoints in Phase 1. Implemented
in T-027 (`ProfileController`, `profile.ProfileService`).

### `GET /api/profile`
→ `{ "id": "hugh", "hash": "3f9c1a…", "savedAt": "...", "profile": { … } }`; `profile` has the
structure and keys of `profile.yaml` (config-spec §1) with defaults filled in, regexes as text and
absent optional values as `null` (`city`, `maxHoursPerWeek`, `minSalary`). `tracks` is not part of
it (not editable; a save keeps the file's tracks). `hash` = sha256 of the file, `savedAt` = its
last-modified time.

### `PUT /api/profile`
Body `{ "baseHash": "3f9c1a…", "profile": { … } }` → 200
`{ "id", "hash", "savedAt", "reassessed": { "jobs": 30, "prefilterChanged": 2, "checksUpdated": 13 } }`.
A `null` or missing optional value means "use the default", as an empty key in the file. The
response comes after the jobs were re-checked (synchronously, no LLM): `jobs` = assessment rows
re-evaluated, `prefilterChanged` = rows whose PASS/REJECT changed, `checksUpdated` = rows whose
eligibility checks changed. Errors (checked in this order; the file is unchanged on any error):
409 `PROFILE_CHANGED` when `baseHash` is not the current hash, or the file was edited by hand since
the app loaded it (detail says to restart); 400 `PROFILE_ID_IMMUTABLE`; 400 `VALIDATION_FAILED`
with extension `errors: [{ "path": "candidate.yearsExperience", "message": "must not be null" }]`
(same paths and rules as the startup validator; all Bean Validation problems are listed, a value
that cannot be read at all — unknown key, invalid regex or time zone — is reported alone); 400
`BAD_REQUEST` for a body that is not JSON of this shape (e.g. an unknown enum value).

## Admin

### `GET /api/admin/overview`
```json
{ "sources": [{ "code", "name", "enabled", "lastRun": { "status", "startedAt", "fetched",
  "newRaw", "newJobs", "error" } | null, "lastSuccessAt": "..." | null }],
  "pipelineReports": [ PipelineReport, ... up to 20 ],
  "llmUsage": { "monthToDateUsd", "monthlyBudgetUsd", "callsToday",
                "daily": [{ "date", "calls", "inputTokens", "outputTokens", "costUsd" }] },
  "failures": { "extractions": [{ jobId, title, attempts, lastError }], "scorings": [...] },
  "pipelineRunning": false,
  "pipelineProgress": { "stage": "SCORE", "text": "Collect 7/9 sources · scoring 14 / 27", "percent": 62 } | null,
  "schedule": { "nextRunAt": "...", "nextDigestAt": "...", "zone": "Europe/Rome" },
  "kpis": { "activeJobs": 1384, "newToday": 42, "prefilterRejectedToday": 31,
            "scoredToday": 9, "toReview": 37,
            "sourceCount": 9, "companyBoardCount": 6 },
  "llmAlerts": [ LlmAlert, ... ] }
```

`LlmAlert` (also returned by `GET /api/status`, below):
```json
{ "purpose": "EXTRACT", "modelId": "groq-gpt-oss-120b", "modelLabel": "GPT-OSS 120B (Groq)",
  "status": "EXHAUSTED", "detail": "Daily request limit 1000/1000", "until": "2026-10-18T00:00:00Z",
  "paused": true,
  "suggestions": [{ "modelId": "openrouter-nemotron-super", "label": "...", "tier": "FREE", "status": "AVAILABLE" }] }
```
One entry per purpose whose assigned model is not `AVAILABLE`, plus (T-022) one per purpose that
auto-fallback switched away from a model that is usable again: then `status` is the current model's
(`AVAILABLE`), `switchedFromModelId` names the previous model and `suggestions` holds only it (switch
back). `switchedFromModelId` is null otherwise. `paused` = owner actions for that
purpose (Analyze or Score) are refused with `LLM_PAUSED` until the model recovers or is switched. `suggestions` = usable models from the assignment's fallbacks, then
other usable registry models (FREE/LOCAL before PAID); models in `ERROR` (also a missing key) or
`EXHAUSTED` and the current model are left out, and the `fake` model unless the current one is fake.

The app header's status line ("Last run 07:00 · 42 new · 9 scored") uses the newest
`pipelineReports` entry. "Today" = since local midnight in `app.pipeline.zone`.
`toReview` = count of the board's default view.

Details:
- `sources`: every registered collector by code; `lastRun.status` is null while that run is in progress.
- `pipelineReports[]`: `{ runNumber, trigger (SCHEDULED | CATCH_UP | MANUAL), status (SUCCESS | PARTIAL | FAILED),
  startedAt, finishedAt, durationMs, sources: [{ sourceCode, status | null (skipped), fetched, newRaw, newJobs,
  updatedJobs, message }], newJobs, reassessed, prefilterEvaluated, prefilterPassed, prefilterRejected,
  notAnalyzed, expired, llmCalls (always 0), error }`.
- `llmUsage`: `monthToDateUsd` = PAID cost of the current UTC month (what the budget guard checks);
  `daily` = the last 14 local days, oldest first, days without calls included.
- `failures`: extractions FAILED for the current prompt version, scorings FAILED without a score for the profile;
  `lastError` = the newest failed `llm_call` of that purpose for the job (`ERROR_KIND: message`), may be null.
- `pipelineProgress`: the pipeline run (stages `COLLECT`, `PREFILTER`, `EXPIRE`) or a bulk LLM action
  (`ANALYZE`, `SCORE`); the one started last is shown; null when idle.
- `schedule`: next fire times of `app.pipeline.cron` and `app.notify.digest-cron`; null when a cron is `-`.
- `kpis`: `prefilterRejectedToday` = assessment rows created today with REJECT; `scoredToday` = jobs whose
  score was written today; `sourceCount` = enabled collectors; `companyBoardCount` = enabled companies.
  (`alertsToday` was removed: instant alerts no longer exist, ADR-0010.)
- `llmAlerts`: as in `GET /api/status` (T-022).

### `POST /api/admin/pipeline/run`
Starts a run asynchronously → 202 `{ "accepted": true }`; 409 `PIPELINE_ALREADY_RUNNING`.
The run makes no LLM calls (ADR-0010).
The UI polls `GET /api/admin/overview` (`pipelineRunning`, newest report).

### `POST /api/admin/collect/{sourceCode}`
Runs one collector + ingest (debug) → 200 `{ runId, collectStatus, fetched, newRaw, newJobs,
updatedJobs, skippedInvalid, warnings, error }` (`error` null unless FAILED). Ignores
`min-interval` and the `enabled` flag; collects postings newer than `prefilter.maxAgeDays`.
404 `SOURCE_NOT_FOUND` for an unknown source code.

### `POST /api/admin/jobs/analyze`
Bulk analysis (extraction, then eligibility checks) of jobs the admin selected, with the same rules
as `/analyze` (ADR-0010). Body `{ "jobIds": [42, 57], "dryRun": true }`, at most 200 ids.
The admin picks jobs from `GET /api/jobs?analysisStage=NOT_ANALYZED`.

Dry run → `{ "selected": 40, "willAnalyze": 33, "skipped": { "alreadyAnalyzed": 7, "expired": 0, "prefilterRejected": 0, "failedPermanently": 0, "notFound": 0 },
"estimatedCalls": 33, "estimatedCostUsd": 0.0, "model": { "modelId", "label", "tier", "paidCallsAllowed" } }`.
`estimatedCalls` counts the jobs without a current extraction (one EXTRACT call each). Skipped jobs make no
call. `model` is the EXTRACT model; a PAID one must be named in the confirmation (ADR-0009);
`estimatedCostUsd` is null for a PAID model without cost history.

`dryRun: false` → 202 `{ "accepted": true }`. Progress appears in `pipelineProgress` (stage
`ANALYZE`). Errors (checked before the run starts, only when at least one job would be analyzed): 400
`TOO_MANY_JOBS`; 409 `PAID_CALLS_DISABLED`; 409 `LLM_PAUSED`; 409 `BUDGET_REACHED`, with the same mid-run rule
as bulk scoring. Bulk actions run in the background one at a time, in the order they were confirmed.

### `POST /api/admin/jobs/score`
Bulk scoring of jobs the admin selected in the Admin page table (ADR-0010). Body
`{ "jobIds": [42, 57], "dryRun": true }`, at most 200 ids. The admin picks jobs from
`GET /api/jobs?analysisStage=ANALYZED&decision=ELIGIBLE&decision=UNDECIDED`.

Dry run → `{ "selected": 40, "willScore": 27, "skipped": { "notAnalyzed": 5, "markedIneligible": 6,
"alreadyScored": 2, "expired": 0, "failedPermanently": 0, "notFound": 0 }, "estimatedCalls": 27,
"estimatedCostUsd": 0.04, "model": { "modelId": "groq-gpt-oss-120b", "label": "...", "tier": "FREE",
"paidCallsAllowed": false } }`. Skipped jobs make no call. A PAID `model` must be named in the
confirmation (ADR-0009).

`dryRun: false` → 202 `{ "accepted": true }`. Progress appears in `pipelineProgress` (stage
`SCORE`). Errors: 400 `TOO_MANY_JOBS`; 409 `PAID_CALLS_DISABLED` (PAID SCORE model and
`app.llm.allow-paid-calls=false`); 409 `LLM_PAUSED`; 409 `BUDGET_REACHED` (checked before the
run starts). If the budget runs out mid-run, the remaining jobs stay unscored and the report says so.

### `POST /api/admin/rescore`
Bulk LLM action, admin only. Always run the dry run first; execute only after the owner confirms the count (ADR-0010).
Re-scores ACTIVE jobs of the profile that are analyzed, not marked INELIGIBLE and already scored
for the current score prompt version (after a profile or model change).
Body `{ "dryRun": true }` → `{ "jobCount": 57, "estimatedCalls": 57, "estimatedCostUsd": 0.09,
"model": { "modelId": "...", "label": "...", "tier": "PAID", "paidCallsAllowed": true } }`
(cost = 0 for FREE/LOCAL; for PAID the average `cost_usd` of that model's SCORE calls in the last
30 days × calls, null if no history); with `dryRun: false` → 202, or 409 `PAID_CALLS_DISABLED`, 409
`LLM_PAUSED`, 409 `BUDGET_REACHED` (checked before the run starts). Progress appears in `pipelineProgress`
(stage `SCORE`).

### `POST /api/admin/jobs/{id}/retry`
Body `{ "purpose": "EXTRACT" | "SCORE" }` → resets the attempts for the current prompt version (a FAILED
extraction, or a FAILED scoring without a score) and runs Analyze or Score for that job in the background → 202
`{ "accepted": true }`. 404 `JOB_NOT_FOUND`. The run follows the bulk rules (paused model or budget: nothing
happens, the job stays as it was).

### `GET /api/admin/companies`
`[{ id, name, ats, slug, enabled, lastCheckStatus, lastCheckedAt, activeJobs }]`, by name. `activeJobs` = ACTIVE
jobs whose `company_key` equals the normalized company name.

### `GET /api/status`
Lightweight, polled by the app header on every page every 60 s (every 10 s while a banner
is shown): `{ "lastRun": { "startedAt", "newJobs", "scored", "status" } | null,
"pipelineRunning": false, "llmAlerts": [LlmAlert] }`. The run itself scores nothing (ADR-0010):
`scored` = jobs scored by owner actions since that run started. `llmAlerts` = the `LlmAlert` list
above (computed per request, nothing stored).

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
  "history": [{ "purpose", "fromModelId", "toModelId", "reason", "at" }],
  "paidCallsAllowed": false }
```
`lastEval` is read from the newest matching entry the eval runner records (T-016); null until then.
`usageToday` counts `llm_call` rows since 00:00 UTC (`costUsd` null when none had a cost);
`limits` lists `rpm`, `rpd`, `tokensPerMinute`, `tokensPerDay`, `requestsPerMonth` (null = unknown);
`history` = the last 10 switches, newest first; `paidCallsAllowed` = `app.llm.allow-paid-calls`.

### `PUT /api/admin/llm/assignments/{purpose}`
Body `{ "modelId": "openrouter-nemotron-super", "fallbacks": [...], "autoFallback": false, "allowPaid": false,
"confirmPaid": false }` (all but `modelId` optional; a missing value keeps the current one) → 200
`{ "assignment": { purpose, modelId, fallbacks, autoFallback, allowPaid, updatedAt, updatedBy }, "warning": null }`.
Errors: 400 `UNKNOWN_MODEL` (model or a fallback not in the registry), 400 `VALIDATION_FAILED` (blank
`modelId`), 409 `MODEL_UNUSABLE` (status `ERROR`, e.g. missing key). A manual switch to a PAID model is
allowed (`allowPaid` only limits auto-fallback); the response then includes
`"warning": "PAID model: counts against the monthly budget"`. Paid consent (ADR-0009): switching to a
different PAID model, or turning `allowPaid` on, needs `confirmPaid: true`, otherwise 409
`PAID_CONFIRMATION_REQUIRED` with the extensions `modelId`, `modelLabel`, `tier` and
`estimatedCostPerCallUsd` (null without cost history); while `app.llm.allow-paid-calls=false` it is
refused with 409 `PAID_CALLS_DISABLED` even when confirmed.
Switching writes `llm_assignment_event` (reason `USER`) and un-pauses that purpose: its
Analyze/Score buttons work again. Nothing runs by itself (ADR-0010); there is no `runNow`.

### `POST /api/admin/llm/models/{modelId}/test`
Sends one tiny prompt (`docs/ai/prompts/test-v1.md`, "Reply with {\"ok\":true}") → 200
`{ ok, latencyMs, status, detail }` (`status` = the model's status after the call, `detail` = why it
failed). The model is called even in `ERROR`, `EXHAUSTED` or `RATE_LIMITED`, and a success clears that
status; a missing key, the budget guard and paid consent still apply. Logged in `llm_call` with purpose
`TEST`. Optional body `{ "confirmPaid": true }`, required for a PAID model (409
`PAID_CONFIRMATION_REQUIRED` / `PAID_CALLS_DISABLED` as above). 404 `UNKNOWN_MODEL`.

### `POST /api/admin/eval/export`
Writes `data/eval-candidates.jsonl` (T-016) → `{ "path": "...", "count": 80 }`. Until T-016: 501
`NOT_IMPLEMENTED`.
