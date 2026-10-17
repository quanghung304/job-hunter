# Himalayas (`himalayas`)

> Verified 2026-10-17 (T-006) against the official pages https://himalayas.app/api,
> https://himalayas.app/docs/remote-jobs-api, https://himalayas.app/terms and real responses.

- **Endpoints:** `GET https://himalayas.app/jobs/api?limit={≤20}[&cursor={nextCursor}]`
  (browse, the full feed) and `GET /jobs/api/search?q=&country=&worldwide=&seniority=&employment_type=&company=&timezone=&sort=&page=`
  (filtered search). OpenAPI: https://himalayas.app/docs/openapi.json. We use **browse**.
- **Auth:** none required. Optional free API key (`Authorization: Bearer …`, env
  `HIMALAYAS_API_KEY`): 60 requests/min and 10 000/UTC day per account. Without a key, all
  callers share one public allowance. Cached responses do not count. Over the limit → `429`
  (with a key: `Retry-After`, `X-RateLimit-*`).
- **Pagination:** cursor. Max 20 jobs per request; pass `nextCursor` back as `cursor`; the
  walk ends when `nextCursor` is absent. `offset` is deprecated (API `comments`, 2026-08-21)
  and slow; cursor never returns the same job twice. The feed is ordered by most recently
  **updated** (a job updated during a walk moves to the front and is picked up next run).
- **Order observed:** the head of the browse feed was strictly newest-first by `pubDate`
  (page 1: 20 jobs within ~7 minutes). `search?sort=recent` is **not** ordered by `pubDate`.
- **Volume:** ~125 000 jobs in the feed; ~160 new jobs per hour (≈ 3 900/day) on 2026-10-17.
- **Stop conditions:** no `nextCursor`, empty page, `max-items` postings, `max-pages`, or a
  **whole page** published before `CollectRequest.since` (older items inside a page are
  skipped). Duplicate `guid`s within one run are skipped.
- **Rate rule (ours):** 2 s between pages; run every 2 h (`min-interval`), up to 400 jobs
  (≈ 20 requests, ~40 s) — owner decision 2026-10-17: walk the newest-first feed and let the
  prefilter select, rather than keyword search.

## Terms (API docs + Terms §30A)

- Terms §30/§93 forbid scraping, but §30A explicitly allows "use of our public API, job
  feeds and MCP server in line with their published documentation and rate limits".
- API docs obligations ("apply to commercial and personal integrations"):
  1. "Every Apply action must use the API's `applicationLink`, not a direct employer/ATS
     link." → a Himalayas-only job gets `applyUrl = applicationLink` (a himalayas.app URL).
     The ATS-over-board merge rule (data-model §4) only replaces it when the same job was
     **also collected from the company's ATS by us** (T-018); that apply link then comes from
     the ATS data, not from Himalayas.
  2. "Display visible Himalayas source credit." → the job detail lists "via Himalayas" with
     the job page link (`postings` in api-spec).
  3. "Do not redistribute jobs to other job boards or aggregators." → personal use only.
- No CORS: server-side calls only (we never call it from the browser).

## Response shape (verified)

```json
{ "comments": "…", "updatedAt": 1792249654, "offset": 0, "limit": 20, "totalCount": 124983,
  "nextCursor": "MjAyNi0xMC0xN1QxNTowMDoxMi42NTc5MDJafDI0MjEwMDI",
  "jobs": [ {
    "guid": "https://himalayas.app/companies/patriot-software/jobs/qa-analyst-ii",
    "title": "QA Analyst II", "companyName": "Patriot Software", "companySlug": "patriot-software",
    "companyLogo": "https://…", "employmentType": "Full Time", "seniority": ["Mid-level"],
    "locationRestrictions": ["United States"], "timezoneRestrictions": [-10, -9, -8, -7, -6, -5, 14],
    "categories": ["QA-Analyst", "Software-QA-Analyst"], "parentCategories": ["Developer"],
    "description": "<p>html</p>…", "excerpt": "…",
    "minSalary": 65000, "maxSalary": 78000, "currency": "USD", "salaryPeriod": "annual",
    "pubDate": 1792249285, "expiryDate": 1797433285,
    "applicationLink": "https://himalayas.app/companies/patriot-software/jobs/qa-analyst-ii" } ] }
```

- `pubDate`/`expiryDate`: epoch **seconds**. `guid` is the job page URL; `applicationLink` was
  the same himalayas.app URL on every captured item.
- `employmentType`: `Full Time`, `Contractor`, `Part Time`, `Intern` seen.
- `salaryPeriod`: `annual` (default), `monthly`, `hourly` seen; docs also list `weekly`,
  `fortnightly`. Amounts may be decimals (1287.13). `currency` can be set with null amounts.
- `timezoneRestrictions`: UTC offsets, may be fractional (5.5). A job without a timezone
  restriction lists **every** offset (37 values on worldwide jobs).
- `locationRestrictions`: country names; empty = worldwide.

## Mapping

| NormalizedPosting | From |
|---|---|
| externalId, url | `guid` (required) |
| title, companyName | `title`, `companyName` |
| locationText | `locationRestrictions` joined by ", ", or "Worldwide" if empty |
| remoteHint | REMOTE (remote-only board) |
| employmentHint | `employmentType` |
| descriptionText | `description` HTML → text, then lines `Location restrictions: … / none listed`, `Timezone restrictions: UTC+1, UTC+5:30 / none` (≥ 24 offsets or empty = none), and `Salary: min - max CUR (weekly)` when the period has no structured equivalent |
| applyUrl | `applicationLink` (API terms); falls back to `guid` |
| salary | `minSalary`, `maxSalary`, `currency`, period annual→YEAR, monthly→MONTH, hourly→HOUR; weekly/fortnightly → text only; no amounts → no salary (currency dropped) |
| tags | `categories` ("Backend-Engineer" → "backend engineer") then `seniority`, lower-case, distinct |
| postedAt | `pubDate` |

## Config keys (`app.sources.himalayas.*`)

| Key | Default | Meaning |
|---|---|---|
| `enabled`, `max-items`, `min-interval` | `true`, `400`, `2h` | shared keys (config-spec §2) |
| `base-url` | `https://himalayas.app` | overridden in tests |
| `api-key` | `${HIMALAYAS_API_KEY:}` | optional; never logged |
| `page-size` | `20` | max 20 |
| `page-delay` | `2s` | pause between page requests |
| `max-pages` | `25` | safety cap per run |

Fixture captured: 2026-10-17 — `fixtures/himalayas/page1.json` (6 of the 20 jobs of the first
browse page: salary annual/monthly/none, US multi-offset timezones), `page2.json` (3 jobs of
the second cursor page: hourly salary, offsets 0/1 and 5.5), `last-page.json` (a real
`search?worldwide=true&q=java` response, which has no `nextCursor`, trimmed to 3 worldwide jobs
with 37 offsets; one has `currency` without amounts). Items, cursors and top-level fields are
unmodified; only the job lists were trimmed.
