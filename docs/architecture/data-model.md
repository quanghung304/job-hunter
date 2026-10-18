# Data model

PostgreSQL 16. All timestamps `timestamptz` (UTC). Ids `bigint generated always as identity`
unless stated. Migrations in `backend/src/main/resources/db/migration` (V2–V5, T-003).
Enum-like text columns have `check` constraints listing their values; counters default to 0;
`text[]` columns are `not null default '{}'`. Entities and repositories live in
`<module>.persistence` of the owning module (enforced by `ArchitectureTest`).

## 1. Tables

### `collector_run`
| Column | Type | Notes |
|---|---|---|
| id | bigint PK | |
| source_code | text not null | matches `JobCollector.sourceCode()` |
| started_at, finished_at | timestamptz | |
| status | text | `SUCCESS`, `PARTIAL`, `FAILED`; null while the run is in progress |
| fetched_count, new_raw_count, new_job_count | int | |
| error_message | text | truncated to 2000 chars |

### `raw_posting`
One row per (source, external id). Keeps what the source returned.
| Column | Type | Notes |
|---|---|---|
| id | bigint PK | |
| source_code | text not null | |
| external_id | text not null | source's own id; if none, sha256 of canonical URL |
| url | text not null | canonical posting URL (attribution link) |
| payload | jsonb not null | original item (RSS items converted to JSON) |
| payload_hash | text not null | sha256 of payload; detects edits |
| first_seen_at, last_seen_at | timestamptz | |
| job_id | bigint FK → job | set by ingest |
| unique | (source_code, external_id) | |

### `job`
The deduplicated job. Profile-independent.
| Column | Type | Notes |
|---|---|---|
| id | bigint PK | |
| fingerprint | text unique not null | see §4 |
| title | text not null | |
| company_name | text not null | display form |
| company_key | text not null | normalized, see §4 |
| location_text | text | as given by source |
| remote_hint | text | `REMOTE`, `HYBRID`, `ONSITE`, `UNKNOWN` — from source fields, before LLM |
| employment_hint | text | free text from source (e.g. "full_time") |
| description_text | text not null | plain text, max 30 000 chars |
| description_hash | text | |
| apply_url | text | best known apply link |
| salary_min, salary_max | numeric | from source if structured |
| salary_currency, salary_period | text | `YEAR`, `MONTH`, `HOUR` |
| tags | text[] | source tags, lower-case |
| posted_at | timestamptz | earliest known posting date |
| first_seen_at, last_seen_at | timestamptz | |
| status | text | `ACTIVE`, `EXPIRED` |

### `job_extraction`
LLM-extracted attributes, per job and prompt version. Profile-independent.
| Column | Type | Notes |
|---|---|---|
| id | bigint PK | |
| job_id | bigint FK | |
| prompt_version | text | e.g. `extract-v1` |
| model | text | |
| attributes | jsonb | validated against schema in `scoring-spec.md` §3; null when `FAILED` |
| status | text | `OK`, `FAILED` |
| attempts | int | |
| created_at | timestamptz | |
| unique | (job_id, prompt_version) | |

### `job_assessment`
Eligibility + score for one job and one profile. Owned by the `enrich` module;
`scoring` writes score fields only through `enrich`'s public `AssessmentService`.
| Column | Type | Notes |
|---|---|---|
| id | bigint PK | |
| job_id | bigint FK | |
| profile_id | text | from `profile.yaml` `id` |
| profile_hash | text | sha256 of the profile content used |
| prefilter_result | text | `PASS`, `REJECT` |
| prefilter_reasons | text[] | |
| eligibility | text | `ELIGIBLE`, `UNCERTAIN`, `INELIGIBLE`, null if prefilter rejected |
| eligibility_reasons | text[] | |
| extraction_id | bigint FK | |
| score | int | 0–100, null if not scored |
| score_breakdown | jsonb | see `scoring-spec.md` §5 |
| summary | text | one line, profile summary language |
| score_prompt_version, score_model | text | |
| status | text | `PENDING`, `DONE`, `FAILED` |
| attempts | int | |
| created_at, updated_at | timestamptz | |
| unique | (job_id, profile_id, score_prompt_version) | `nulls not distinct`: at most one unscored row (null version) per job and profile |

Rows are written for the configured score prompt version (`app.llm.score-prompt`) by
`AssessmentService.recordPrefilter`: prefilter REJECT → `eligibility` null, `status` DONE
(no LLM work); PASS on a new row or after a REJECT → `status` PENDING.

Analyze (ADR-0010, `AnalysisService`) records the prefilter if needed, then `extraction_id`,
`eligibility` and `eligibility_reasons` (`RULE:UNKNOWN`/`RULE:FAIL` per non-OK rule, plus
`FULL_TIME_ONLY_SOFT` and `CONFIDENCE:LOW`); INELIGIBLE → `status` DONE, otherwise PENDING until
the owner scores it. A changed job description deletes the job's extractions and clears
eligibility and score (`status` PENDING again).

When the profile file changes (`profile_hash` differs), prefilter and eligibility are
re-evaluated (cheap, no LLM): `AssessmentService.reassessPrefilter` re-runs the prefilter
and clears `eligibility` of every row with another hash, so it is evaluated again; scores are kept. Re-scoring after a profile change is manual
(admin button "Re-score active jobs"), to control cost.

### `job_tracking`
| Column | Type | Notes |
|---|---|---|
| job_id, profile_id | PK (composite) | |
| status | text | see §2 |
| notes | text | |
| updated_at | timestamptz | |

Created lazily: a job without a row is `NEW`.

### `job_tracking_event`
| id | job_id | profile_id | from_status | to_status | note | created_at |

### `llm_call`
| Column | Type | Notes |
|---|---|---|
| id | bigint PK | |
| purpose | text | `EXTRACT`, `SCORE`, `EVAL`, `TEST` (admin "Test" call) |
| job_id | bigint | nullable; FK → job, `on delete set null` |
| model_id | text | registry id from `config/llm.yaml` (e.g. `groq-gpt-oss-120b`) |
| provider_model, prompt_version | text | model name sent to the provider |
| tier | text | `FREE`, `PAID`, `LOCAL` at call time |
| input_tokens, output_tokens | int | |
| cost_usd | numeric(10,6) | PAID only, from registry pricing; null otherwise |
| latency_ms | int | |
| success | boolean | |
| http_status | int | nullable |
| error_kind | text | `RATE_LIMIT`, `QUOTA_EXHAUSTED`, `AUTH`, `MODEL_NOT_FOUND`, `TIMEOUT`, `SERVER`, `BAD_REQUEST` (other 4xx, not retried), `INVALID_OUTPUT`, null |
| error | text | truncated 2000 chars |
| created_at | timestamptz | |

Index: `llm_call(model_id, created_at)`. One row per HTTP attempt (retries and the corrective
retry included); a 2xx answer that fails JSON/schema validation is `success=false`,
`error_kind=INVALID_OUTPUT` with its tokens. V5 created the table with a `model` column; V6
renamed it to `provider_model` and added `model_id`, `tier`, `http_status`, `error_kind`.

### `llm_assignment` (ADR-0008)
Which model serves each purpose. One row per purpose; seeded from `config/llm.yaml`
`defaults` when missing. Changed from the Admin page.
| Column | Type | Notes |
|---|---|---|
| purpose | text PK | `EXTRACT`, `SCORE` |
| model_id | text | must exist in the registry |
| fallbacks | text[] | ordered model ids |
| auto_fallback | boolean | |
| allow_paid | boolean | |
| updated_at | timestamptz | |
| updated_by | text | `config` (seeded from `defaults`), `user`, `auto-fallback` (router switched to a fallback; never switched back automatically) |

### `llm_assignment_event`
History of switches: `id`, `purpose`, `from_model_id`, `to_model_id`, `reason`
(`USER`, `AUTO_FALLBACK`, `RESTORED`), `created_at`. Shown on the Admin page.

### `llm_model_state` (ADR-0008)
| Column | Type | Notes |
|---|---|---|
| model_id | text PK | |
| status | text | `AVAILABLE`, `NEAR_LIMIT`, `RATE_LIMITED`, `EXHAUSTED`, `ERROR` |
| until | timestamptz | when the status is expected to clear (from `retry-after`/reset headers, or next UTC midnight for daily limits); null if unknown |
| detail | text | human-readable reason, e.g. "Daily request limit 1000/1000" |
| remaining_requests, remaining_tokens | int | last values reported by provider headers, nullable |
| updated_at | timestamptz | |

Status rules (implemented in `QuotaTracker`, unit-tested with a fixed Clock):
- `RATE_LIMITED`: 429 with reset ≤ 1h → `until` = reset; calls to this model wait/skip until then.
- `EXHAUSTED`: 429 whose body/headers indicate a daily/monthly quota, or reset > 1h, or our
  own count reached a known `rpd`/`tokensPerDay`/`requestsPerMonth` → `until` = reset or next
  UTC midnight (month start for monthly).
- `NEAR_LIMIT`: our count ≥ `nearLimitPercent` of a known daily/monthly limit.
- `ERROR`: 401/403 (bad key), 404 model not found, or missing key env var. Clears only after
  a successful test call (Admin "Test" button) or restart.
- Back to `AVAILABLE` when `until` passes or a call succeeds.

### `eval_run`
Written by `EvalRunner` (T-016): `id`, `extract_model_id`, `score_model_id`,
`extract_prompt`, `score_prompt`, `job_count`, `metrics` jsonb (scoring-spec §7 names),
`cost_usd`, `created_at`. The Admin "LLM models" card shows the newest run per model.

### `notification`
| id | profile_id | channel (`TELEGRAM`) | kind (`DIGEST`, `LLM_QUOTA`) | job_ids bigint[] | ref text | sent_at | success | error |

`profile_id` was added in T-003: without it "once per profile" cannot be checked.
V4 created `notification` without `ref` and with `kind in (DIGEST, ALERT)` and
`profile_id not null`; V7 removed `ALERT` (instant alerts dropped, ADR-0010). T-022 adds a new
migration for `ref`, `LLM_QUOTA` and a nullable `profile_id` (quota alerts are not per profile).

A job appears in at most one digest per profile; the digest includes only jobs not in a previous
digest. `LLM_QUOTA` rows use `ref` = `<model_id>:<status>:<yyyy-MM-dd>` so each model/status
is announced at most once per day.

### `company` (Phase 1, for ATS collectors)
| Column | Type | Notes |
|---|---|---|
| id | bigint PK | |
| name | text | |
| ats | text | `GREENHOUSE`, `LEVER`, `LEVER_EU`, `ASHBY` |
| board_slug | text | |
| enabled | boolean | |
| last_check_status | text | `OK`, `NOT_FOUND`, `ERROR` |
| last_checked_at | timestamptz | |
| unique | (ats, board_slug) | |

Seeded from `config/companies.yaml` on startup (upsert by ats+slug; DB keeps status).

## 2. Tracking status lifecycle

```
            ┌──────────► DISMISSED
            │
 NEW ──► SHORTLISTED ──► APPLIED ──► INTERVIEWING ──► OFFER
  │          │              │              │
  │          └► DISMISSED   ├──────────────┴──► REJECTED
  └──────────► APPLIED      └──► WITHDRAWN
```

Allowed transitions (anything else → `IllegalStatusTransitionException`):

| From | To |
|---|---|
| NEW | SHORTLISTED, DISMISSED, APPLIED |
| SHORTLISTED | DISMISSED, APPLIED, NEW |
| DISMISSED | NEW (undo) |
| APPLIED | INTERVIEWING, REJECTED, WITHDRAWN |
| INTERVIEWING | OFFER, REJECTED, WITHDRAWN |
| OFFER, REJECTED, WITHDRAWN | — (terminal; notes still editable) |

`job.status = EXPIRED` is independent of tracking status. The UI hides EXPIRED + NEW jobs by default.

## 3. `NormalizedPosting` (collector output, not a table)

```java
public record NormalizedPosting(
    String sourceCode, String externalId, String url,
    String title, String companyName, String locationText,
    RemoteHint remoteHint,            // REMOTE | HYBRID | ONSITE | UNKNOWN
    String employmentHint,            // raw, nullable
    String descriptionText,           // plain text (HTML converted by the collector)
    String applyUrl,                  // nullable
    BigDecimal salaryMin, BigDecimal salaryMax, String salaryCurrency, SalaryPeriod salaryPeriod,
    List<String> tags,
    Instant postedAt,                 // nullable; ingest uses now() if null
    JsonNode rawPayload               // the original item
) {}
```

Validation in ingest: title, companyName, url, descriptionText non-blank; otherwise the
posting is skipped and counted as a warning on the run.

## 4. Normalization and dedupe

Implemented in T-005 (`common.text.TextNormalizer`, `ingest.Fingerprint`, `ingest.IngestService`).

- `company_key` = lower-case, accents stripped, punctuation removed, a trailing run of single
  letters joined (`S.r.l.` → `srl`), then legal suffixes removed **from the end, repeatedly**
  (spec list `inc`, `ltd`, `llc`, `gmbh`, `srl`, `spa`, `bv`, `sa`, `co`, `corp`, `limited`
  plus `incorporated`, `corporation`, `plc`, `llp`, `ag`, `kg`, `ug`, `se`, `nv`, `sas`,
  `sarl`, `sl`, `oy`, `ab`, `as`, `aps`, `pty`, `pvt`), whitespace collapsed. A name made only
  of legal words keeps its first word.
- `title_key` = lower-case, accents stripped, content in brackets/parentheses removed
  (nested too), gender tags removed (`m/f/d`, `f/m/d`, `w/m/d`, `m/w/d`, `f/m/x`, any 2–4
  of `m f w d x` joined by `/`), separators `- – — | / ,` replaced by space, the word
  `remote` removed, whitespace collapsed. Other punctuation is kept (`c++`, `c#`).
- `fingerprint` = sha256(`company_key` + "|" + `title_key`).

| Input | Key |
|---|---|
| "Acme, Inc." / "ACME Inc" / "Acme" / "Acme GmbH & Co. KG" | `acme` |
| "Società S.r.l." / "Società Srl" | `societa` |
| "Senior Backend Engineer (Remote) - m/f/d" / "[Remote] Senior Backend Engineer" | `senior backend engineer` |
| "Full-Stack Engineer – AI Authoring Platform" | `full stack engineer ai authoring platform` |

- Ingest (per batch of 50 postings, one transaction): validate (title, company, url,
  description non-blank; else skipped and counted) → find `raw_posting` by (source,
  external_id; blank id → sha256 of the URL). Existing: update `last_seen_at`, and payload,
  `payload_hash` (sha256 of the payload as received) and url if the hash changed; its job is
  reused. New: find the **newest job of the fingerprint family** (`fp` or `fp#…`); if none,
  create; if its `last_seen_at` is older than 60 days, create a new job (re-posting) with
  fingerprint `<fp>#<yyyyMMdd>` of the new first_seen (UTC); else merge into it.
  The family lookup avoids a duplicate `#date` fingerprint on the next re-posting.
- Merge rule when a job gets a posting: keep the longest `description_text` (max 30 000
  chars, truncated); keep earliest `posted_at` (new jobs use now when the source has none);
  take structured salary only if the job has none; prefer ATS `apply_url`
  (greenhouse.io / lever.co / ashbyhq.com hosts) over board URLs (apply_url defaults to the
  posting url); fill `location_text`, `employment_hint`, `remote_hint` (when UNKNOWN) if
  missing; tags are united; title and company keep their first form. A sighting sets
  `last_seen_at` and `status = ACTIVE`.
- If an existing job's description hash changes, `JobDescriptionChanged(jobId)` is published
  and the extraction for it is invalidated (enrich, T-011).
- `IngestResult(newRaw, newJobs, updatedJobs, skippedInvalid)`: `updatedJobs` counts existing
  jobs whose content (not only `last_seen_at`) changed; jobs created in the same call are not
  counted as updated.

## 5. Indexes (minimum)

- `job(status, last_seen_at)`, `job(company_key)`
- `job_assessment(profile_id, score desc)`, `job_assessment(status)`
- `raw_posting(job_id)`, `llm_call(created_at)`
- Also created: `collector_run(source_code, started_at desc)`, `job_assessment(extraction_id)`,
  `job_tracking_event(job_id, profile_id, created_at)`, `notification` GIN on `job_ids` and
  `(profile_id, kind, sent_at desc)`
