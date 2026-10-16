# Jobicy (`jobicy`)

> Expected shape from public examples. **Verify with a captured response (T-007 step 1)**
> and read Jobicy's API page for the current params, max `count`, and credit requirement.

- **Endpoint:** `GET https://jobicy.com/api/v2/remote-jobs?count={n}&geo={geo}&industry={industry}&tag={tag}`
- **Auth:** none
- **Pagination:** none; one call returns up to `count` latest jobs (there is a cap).
- **Plan:** one call per configured query:
  - `app.sources.jobicy.queries` default:
    `[{geo: europe, industry: dev}, {geo: anywhere, industry: dev}, {tag: java}]`
    (verify accepted geo/industry values against the docs; adjust defaults).
- **Rate rule:** ≥ 2 s between calls; min interval between runs 6h.
- **Terms:** credit Jobicy and link to the original posting.

## Expected item shape

```json
{ "id": 123, "url": "https://jobicy.com/jobs/...", "jobSlug": "...", "jobTitle": "...",
  "companyName": "...", "jobIndustry": ["Software Engineering"], "jobType": ["full-time"],
  "jobGeo": "Europe", "jobLevel": "Senior", "jobExcerpt": "...", "jobDescription": "<p>html</p>",
  "pubDate": "2026-10-10 12:00:00", "annualSalaryMin": 50000, "annualSalaryMax": 70000,
  "salaryCurrency": "EUR" }
```

## Mapping

| NormalizedPosting | From |
|---|---|
| externalId | `id` |
| url, applyUrl | `url` |
| title, companyName | `jobTitle`, `companyName` |
| locationText | `jobGeo` |
| remoteHint | REMOTE |
| employmentHint | `jobType` joined |
| descriptionText | `jobDescription` HTML → text |
| salary | annual min/max/currency, period YEAR |
| tags | `jobIndustry` + `jobLevel`, lower-case |
| postedAt | `pubDate` (verify format and zone; assume UTC if none given, document it) |

Results from multiple queries overlap: dedupe by `id` inside the collector.

Fixture captured: —
