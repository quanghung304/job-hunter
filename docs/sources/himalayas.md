# Himalayas (`himalayas`)

> Expected shape from public examples. **Verify with a captured response (T-006 step 1).**
> Check the official API page on himalayas.app for current params, page-size cap and
> attribution terms, and record them here.

- **Endpoint:** `GET https://himalayas.app/jobs/api?limit={limit}&offset={offset}`
  (a search endpoint with keyword filters may exist; prefer it if documented).
- **Auth:** none
- **Pagination:** `offset` + `limit`; response has a total count. Items are newest first;
  stop at `since`, at `max-items`, or when a page is empty.
- **Rate rule:** ≥ 1 s between pages; min interval between runs 6h.
- **Terms:** record from official page (attribution likely required).
- **Content:** remote-only board; restrictions are given explicitly per job.

## Expected item shape

```json
{ "guid": "...", "title": "...", "companyName": "...", "companySlug": "...",
  "employmentType": "Full Time", "seniority": ["Mid-level"],
  "locationRestrictions": ["Germany", "Italy"], "timezoneRestrictions": [1, 2],
  "categories": ["Backend Development"], "description": "<p>html</p>", "excerpt": "...",
  "minSalary": 50000, "maxSalary": 70000, "currency": "EUR",
  "pubDate": 1760000000, "expiryDate": 1765000000, "applicationLink": "https://..." }
```

## Mapping

| NormalizedPosting | From |
|---|---|
| externalId | `guid` (or the job URL if absent) |
| url | job page URL (from `guid` if it is a URL; verify) |
| title, companyName | `title`, `companyName` |
| locationText | `locationRestrictions` joined, or "Worldwide" if empty |
| remoteHint | REMOTE |
| employmentHint | `employmentType` |
| descriptionText | `description` HTML → text |
| applyUrl | `applicationLink` |
| salary | `minSalary`, `maxSalary`, `currency`, period YEAR (verify) |
| tags | `categories` + `seniority`, lower-case |
| postedAt | `pubDate` (epoch seconds; verify unit) |

## Quirks

- `locationRestrictions` are country names, not codes: keep as text; the LLM extraction
  maps them to region tokens. Also add a deterministic hint: if empty → description note
  "Location restrictions: none listed".
- `timezoneRestrictions` (UTC offsets) — append to description text as
  "Timezone restrictions: UTC+1, UTC+2" so extraction sees them.

Fixture captured: —
