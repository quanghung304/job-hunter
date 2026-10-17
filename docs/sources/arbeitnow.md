# Arbeitnow (`arbeitnow`)

> Shape verified against real responses captured 2026-10-17 (T-004).

- **Endpoint:** `GET https://www.arbeitnow.com/api/job-board-api?page={n}`
- **Auth:** none
- **Page size:** 300 items on full pages (not ~100); later pages may be smaller (250 seen).
- **Pagination:** `links.next` is an absolute URL to the next page, `links.last` is always
  `null`. Requesting past the end returns `200` with `data: []` and `links.next: null`
  (seen at `page=1000`). The collector increments the page number itself and uses
  `links.next` only as the "more pages" flag (so WireMock can stand in for the host).
- **Order:** `meta.info` says items are ordered by `created_at`, but they are **not strictly
  ordered**, within a page or across pages (page 6 held items newer than some on page 1).
- **Stop conditions:** empty page, `links.next == null`, `max-items` postings reached,
  `max-pages` reached, or a **whole page** older than `CollectRequest.since` (an item older
  than `since` is skipped, not a stop signal, because of the order quirk above).
- **Rate rule:** ≥ 1 s between pages; we use 2 s. Response header `x-ratelimit-limit: 50`, but
  Cloudflare answered `429` with a JS challenge page after ~13 page requests within a minute
  (2026-10-17). Min interval between runs: 1h (feed refreshes hourly).
- **Terms** (`meta.terms` in every response): "This is a free public API for jobs, please do
  not abuse. I would appreciate linking back to the site. By using the API, you agree to the
  terms of service present on Arbeitnow.com." → we keep `url` as the attribution link.
- **Content:** mostly Europe (Germany-heavy, also UK, FR, NL, CH); few remote items
  (13 of 300 on page 1).

## Response shape (verified)

```json
{ "data": [ {
    "slug": "remote-head-of-urology-232681", "company_name": "Engagedmd",
    "title": "Head of Urology", "description": "<p>html</p>…", "remote": true,
    "url": "https://www.arbeitnow.com/jobs/companies/engagedmd/remote-head-of-urology-232681",
    "tags": ["Market Expansion"], "job_types": ["Full Time"], "location": "Remote",
    "created_at": 1792183223 } ],
  "links": { "first": "…?page=1", "last": null, "prev": null, "next": "…?page=2" },
  "meta": { "current_page": 1, "current_page_url": "…", "from": 1, "path": "…",
            "per_page": 300, "to": 300, "terms": "…", "info": "…" } }
```

All ten item fields were present on every item of page 1 (types: strings, `remote` boolean,
`tags`/`job_types` arrays, `created_at` epoch seconds).

## Mapping

| NormalizedPosting | From |
|---|---|
| externalId | `slug` (required, else the item is a warning) |
| url, applyUrl | `url` (required); hosts vary: `arbeitnow.com`, `.co.uk`, `.nl` |
| title, companyName | `title`, `company_name` |
| locationText | `location`, empty string → null (22 of 300 empty) |
| remoteHint | `remote == true` **or** `location` contains the word "remote" (case-insensitive) → REMOTE, else ONSITE |
| employmentHint | `job_types` joined by ", ", empty → null (most items have none) |
| descriptionText | `description` HTML → text (`HtmlText.toPlain`) |
| tags | `tags` lower-case, distinct |
| postedAt | `created_at` (epoch seconds) |
| salary* | not provided |

## Known quirks

- **Double-escaped HTML:** some descriptions arrive entity-escaped
  (`&lt;div class=&quot;content-intro&quot;&gt;…`) and then end with a real HTML footer
  `<p>Find more English Speaking Jobs in Germany on Arbeitnow</p>`. `HtmlText.toPlain`
  unescapes once when it finds ≥ 3 escaped tags. Every description ends with that footer.
- **`remote: false` with `location: "Remote"`** exists (e.g. "Regional Sales Director - France").
  Owner decision (2026-10-17): treat a location containing the word "remote" as remote too,
  for both `remoteHint` and `remote-only` — a false positive is better than a missed job.
- `job_types` mixes languages and casing ("Full Time", "Full time",
  "Unbefristeter arbeitsvertrag", "freelance") — kept raw as `employmentHint` for the LLM.
- Titles and descriptions contain typographic Unicode (–, ’); JSON is UTF-8.
- The same slug can appear on two pages of one run (unstable order): 200 postings gave 194
  raw postings in the first real run. Ingest dedupes by (source, external_id).
- On `429`, the collector keeps earlier pages (PARTIAL); warnings carry only "HTTP 429 …",
  never the HTML body.

## Config keys (`app.sources.arbeitnow.*`)

| Key | Default | Meaning |
|---|---|---|
| `enabled`, `max-items`, `min-interval` | `true`, `200`, `1h` | shared keys (config-spec §2) |
| `base-url` | `https://www.arbeitnow.com` | overridden in tests |
| `remote-only` | `true` | skip items that are not remote by the rule above (counted in `fetched`, not output) |
| `page-delay` | `2s` | pause between page requests |
| `max-pages` | `20` | safety cap per run |

Fixture captured: 2026-10-17 — `fixtures/arbeitnow/page1.json` (6 items of page 1: 3 remote,
2 empty locations, 2 double-escaped descriptions, items without tags/job types),
`page2.json` (3 items of page 6, all older than page 1), `last-page.json` (`page=1000`,
empty, `next: null`). Items and `links`/`meta` are unmodified; only the item list was trimmed.
