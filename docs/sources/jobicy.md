# Jobicy (`jobicy`)

> Verified 2026-10-17 (T-007) against the official API page https://jobicy.com/jobs-rss-feed
> (API docs, fair use), the slug lists and real responses. OpenAPI: https://jobicy.com/api/openapi.json.

- **Endpoint:** `GET https://jobicy.com/api/v2/remote-jobs?count={1–200}&geo={slug}&industry={slug}&tag={keyword}[&cursor={nextCursor}]`
  (all filters optional; send `Accept: application/json`).
- **Auth:** none for the public API. An optional paid "Commercial Jobs API" key adds direct
  application links ($0.01 per job) — not used; public `url` is always the Jobicy listing.
- **Window and order:** only jobs published in the last **7 days**, with a **3-hour delay**;
  ordered by publication time (UTC), newest first, id as tie-breaker.
- **Pagination:** cursor. `count` 1–200 (default 200). If the response has `nextCursor` (and
  `hasMore: true`), pass it unchanged as `cursor` with the same filters; the last page has
  `nextCursor: null`, `hasMore: false`. Cursors live 24 h. Deduplicate by `id`.
- **Volume (2026-10-17):** `geo=europe&industry=engineering` → 59 jobs in the window;
  `tag=java` → 57; 22 ids shared. One page of 100 per query is enough.
- **Plan:** one request per configured query (`app.sources.jobicy.queries`), cursor followed
  up to `max-pages-per-query`. A query stops at its first job older than `CollectRequest.since`
  (the order is guaranteed). Jobs are deduplicated by `id` across queries. One failing query
  is a warning (PARTIAL); the run is FAILED only if every request failed.
- **Valid slugs** (`?get=locations`, `?get=industries`, 2026-10-17):
  - geo (55): `anywhere`, `apac`, `emea`, `latam`, `europe`, `uk`, `usa`, `italy`, `germany`, … 
  - industry (22): `engineering` (Software Engineering), `admin` (DevOps & Infrastructure),
    `data-science`, `qa-testing`, `cybersecurity`, `technical-support`, … — **`dev` (in the
    old spec) does not exist.**
- **Default queries:** `{geo: europe, industry: engineering}`, `{geo: anywhere, industry: engineering}`, `{tag: java}`.
- **Rate rule:** ≥ 2 s between calls; min interval between runs 6h (the docs: polling "must
  not exceed once per hour").
- **Terms (Fair use):** no approval needed for normal integrations; "Keep Jobicy as the original
  source and preserve the canonical Jobicy job URL when displaying listings", do not remove
  source attribution, avoid excessive requests. Every response says "Please credit Jobicy and
  link to the source." → `url` and `applyUrl` are the Jobicy job URL; the UI shows "via Jobicy".

## Response shape (verified)

```json
{ "apiVersion": "2.2.19", "documentationUrl": "https://jobi.cy/apidocs",
  "friendlyNotice": "Thanks for using Jobicy API! Please credit Jobicy and link to the source.",
  "jobCount": 59, "lastUpdate": "2026-10-17T08:33:07+00:00", "nextCursor": null, "hasMore": false,
  "appliedFilters": {"count": 100, "geo": "europe", "industry": "engineering"},
  "jobs": [ {
    "id": 155587, "url": "https://jobicy.com/jobs/155587-engineering-director-eu",
    "jobSlug": "155587-engineering-director-eu", "jobTitle": "Engineering Director, EU",
    "companyName": "Monzo", "companyLogo": "https://…", "jobIndustry": ["Software Engineering"],
    "jobType": ["Full-Time"], "jobGeo": "UK", "jobLevel": "Director", "jobExcerpt": "…",
    "jobDescription": "<p>html</p>…", "pubDate": "2026-10-17T08:33:07+00:00",
    "salaryMin": 170000, "salaryMax": 225000, "salaryCurrency": "GBP", "salaryPeriod": "yearly" } ],
  "statusCode": 200, "success": true }
```

- `pubDate` is ISO-8601 **with offset** (`+00:00`), not `"yyyy-MM-dd HH:mm:ss"` as the old spec
  said. The mapper also accepts the zone-less forms (`2026-10-10 12:00:00`,
  `2026-10-10T12:00:00`) and reads them as **UTC**.
- Salary keys are `salaryMin`/`salaryMax`/`salaryCurrency`/`salaryPeriod` (`yearly`, `monthly`
  seen; docs mention `hourly`) and are **absent** (not null) on jobs without salary (80 of 116).
- `jobGeo` may list several places with double spaces: `"Germany,  Ireland,  Spain"`.
- `jobLevel`: `Director`, `Senior`, `Midweight`, `Any`, or a list (`"Entry-Level, Junior"`).
- `jobType`: `Full-Time`, `Contract`.

## Mapping

| NormalizedPosting | From |
|---|---|
| externalId | `id` (required) |
| url, applyUrl | `url` (required; canonical Jobicy URL per fair use) |
| title, companyName | `jobTitle`, `companyName` |
| locationText | `jobGeo`, list re-joined with ", " |
| remoteHint | REMOTE |
| employmentHint | `jobType` joined by ", " |
| descriptionText | `jobDescription` HTML → text; `Salary: min - max CUR (period)` appended when the period has no structured equivalent |
| salary | `salaryMin`, `salaryMax`, `salaryCurrency`; `yearly`/`annual` → YEAR, `monthly` → MONTH, `hourly` → HOUR (missing period → YEAR) |
| tags | `jobIndustry`, then `jobLevel` split on "," (`Any` dropped), lower-case, distinct |
| postedAt | `pubDate` (ISO with offset; zone-less → UTC); unreadable → item warning |

## Config keys (`app.sources.jobicy.*`)

| Key | Default | Meaning |
|---|---|---|
| `enabled`, `max-items`, `min-interval` | `true`, `200`, `6h` | shared keys (config-spec §2) |
| `base-url` | `https://jobicy.com` | overridden in tests |
| `queries` | the three above | list of `{geo, industry, tag}`; blank values omitted |
| `count` | `100` | 1–200 |
| `call-delay` | `2s` | pause between any two requests |
| `max-pages-per-query` | `3` | cursor pages per query |

Fixture captured: 2026-10-17 — `fixtures/jobicy/europe-engineering.json` (5 of 59 jobs of
`count=100&geo=europe&industry=engineering`: GBP yearly, multi-country geo without salary keys,
USD monthly contract) and `tag-java.json` (5 of 57 jobs of `count=100&tag=java`; ids 155565 and
155439 appear in both files). Both are last pages (`nextCursor: null`). Items and top-level fields
are unmodified; only the job lists were trimmed.
