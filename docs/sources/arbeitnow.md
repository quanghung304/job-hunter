# Arbeitnow (`arbeitnow`)

> Field names below are the expected shape from public documentation and examples.
> **Verify against a captured response (T-004 step 1) and correct this file if different.**

- **Endpoint:** `GET https://www.arbeitnow.com/api/job-board-api?page={n}`
- **Auth:** none
- **Pagination:** `links.next` (null on last page). Stop when `next` is null, when an item
  older than `CollectRequest.since` is reached, or at `max-items`.
- **Rate rule:** ≥ 1 s between pages. Min interval between runs: 1h (feed refreshes hourly).
- **Terms:** free; link back to the posting URL requested → we always keep `url`.
- **Content:** mostly Europe (Germany-heavy), includes on-site jobs; `remote` flag per item.

## Expected item shape

```json
{ "slug": "...", "company_name": "...", "title": "...", "description": "<p>html</p>",
  "remote": true, "url": "https://www.arbeitnow.com/jobs/...", "tags": ["..."],
  "job_types": ["full time"], "location": "Berlin", "created_at": 1760000000 }
```

## Mapping

| NormalizedPosting | From |
|---|---|
| externalId | `slug` |
| url | `url` |
| title, companyName | `title`, `company_name` |
| locationText | `location` |
| remoteHint | `remote == true` → REMOTE, else ONSITE |
| employmentHint | `job_types` joined by ", " |
| descriptionText | `description` HTML → text |
| applyUrl | `url` |
| tags | `tags` lower-case |
| postedAt | `created_at` (epoch seconds) |

## Collector behavior

- Phase 1 option `app.sources.arbeitnow.remote-only: true` (default): skip items with
  `remote == false` before mapping (count them in `fetched`, not in output).

Fixture captured: —
