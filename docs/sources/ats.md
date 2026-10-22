# Company ATS boards: Greenhouse (`greenhouse`), Lever (`lever`), Ashby (`ashby`)

These are the public APIs that power companies' own career pages. They return complete,
first-hand postings. Coverage depends on `config/companies.yaml` (and the `company` table).

> Verified 2026-10-22 (T-018) against real responses: Greenhouse `gitlab`, `stripe`; Lever
> `spotify`, `palantir`; Ashby `linear`, `notion`, `ramp`, `openai` (the last two only to see
> compensation). The vendors' terms were not re-read. All six smoke-test boards answer OK.

## Common behavior (`CompanyBoardCollector`)

- One `JobCollector` per ATS. Each reads the **enabled** companies of its ATS types, by name,
  one request per board, with `request-delay` (1 s) between boards (Lever asks for ≤ 1
  request/s; the same pause is used for all three). Min interval between runs: 12h.
- A board answering **404** marks the company `NOT_FOUND`, increments `not_found_count` and
  is a run warning; at 3 in a row the company is disabled. Other HTTP errors mark it `ERROR`
  (warning). A board read successfully marks it `OK`, resets the count and enables it.
- Run status: warnings → PARTIAL; FAILED only if no board was read and at least one failed
  with an error other than 404 (only missing boards → PARTIAL). No enabled board → SUCCESS
  with nothing.
- `since` is ignored (boards return all open jobs); ingest updates `last_seen_at`, which
  drives expiry. `max-items` (3000) caps the postings of one run.
- `externalId` = `{slug}:{id}`; `companyName` = the name in `companies.yaml` (not the
  vendor's field), so the Admin "active jobs" count matches.
- Board APIs return all jobs including on-site; mapping sets `remoteHint`, prefilter/LLM do the rest.
- 404 bodies (2026-10-22): Greenhouse `{"status":404,"error":"Job not found"}`, Lever
  `{"ok":false,"error":"Document not found"}`, Ashby plain text `Not Found`.
- `make validate-companies` (`--validate-companies`) fetches every board once, also disabled
  ones, records the status and prints a report (docs/runbook.md, "Company boards").

## Greenhouse

- `GET https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true` → `{ "jobs": [...], "meta": { "total": 232 } }`
  (all jobs in one response; GitLab 3.6 MB, Stripe 5.7 MB).
- Job (verified): `id` (number), `internal_job_id`, `title` (sometimes with trailing spaces),
  `absolute_url` (`https://job-boards.greenhouse.io/{slug}/jobs/{id}`), `location { name }`
  (`Remote`, `Remote, Canada`, `Bangalore, India`, or a `;`-separated list),
  `content` (HTML **entity-escaped once more**: `&lt;div class=&quot;content-intro&quot;&gt;…`),
  `departments [{ id, name, child_ids, parent_id }]`, `offices [{ id, name, location, … }]`,
  `first_published` and `updated_at` (ISO-8601 with offset, `2026-09-10T13:32:44-04:00`),
  `company_name`, `requisition_id`, `language`, `metadata [...]`, `data_compliance [...]`,
  `application_deadline`, `ai_disclaimer`, … No salary.
- Mapping: url = applyUrl = `absolute_url`; locationText = `location.name`; remoteHint REMOTE
  if it matches `/\b(remote|anywhere|distributed)\b/i`, else UNKNOWN; description = `content`
  entity-unescaped once, then HTML → text; postedAt = `first_published` (else `updated_at`;
  the old spec only knew `updated_at`); tags = department names, lower-case.

## Lever

- `GET https://api.lever.co/v0/postings/{slug}?mode=json` (a JSON array); for `ats: LEVER_EU`
  `https://api.eu.lever.co/v0/postings/{slug}?mode=json` (a US board answers 404 there).
- Posting (verified): `id` (UUID), `text` (title), `hostedUrl`, `applyUrl`
  (`{hostedUrl}/apply`), `createdAt` (epoch ms), `country`,
  `categories { commitment?, department, location, team, allLocations[] }` (`commitment` may be
  absent), `workplaceType` (`remote` | `hybrid` | `onsite`; docs also name `unspecified`),
  `description`/`descriptionPlain`, `descriptionBody`/`descriptionBodyPlain`,
  `opening`/`openingPlain`, `lists [{ text, content (HTML <li>s) }]`, `additional`/`additionalPlain`.
- `salaryRange { min, max, currency, interval }` was **not present on any board checked**
  (Spotify 81, Palantir 304, Mistral 0 postings); mapping follows the Lever docs and is
  unverified: `per-year-salary` → YEAR, `per-month-salary` → MONTH, `per-hour-wage` → HOUR;
  another interval → no structured salary.
- Mapping: title = `text`; url = `hostedUrl`; applyUrl = `applyUrl`; locationText =
  `categories.location` then the other `allLocations`, joined by ", "; remoteHint from
  `workplaceType` (else UNKNOWN); employmentHint = `categories.commitment`; description =
  `descriptionPlain` + each list as its heading line and its items (HTML → text) +
  `additionalPlain`, separated by blank lines; postedAt = `createdAt`; tags = department, team.

## Ashby

- `GET https://api.ashbyhq.com/posting-api/job-board/{slug}?includeCompensation=true` →
  `{ "apiVersion": …, "jobs": [...] }`.
- Job (verified): `id` (UUID), `title` (may start with a space), `department`, `team`,
  `employmentType` (`FullTime`, `Contract`, `Intern`), `location`,
  `secondaryLocations [{ location, address }]`, `address { postalAddress }`, `isListed`,
  `isRemote` (true for Hybrid jobs too), `workplaceType` (`Remote` | `Hybrid` | `OnSite` | null),
  `publishedAt` (ISO with offset and millis), `jobUrl`, `applyUrl` (`{jobUrl}/application`),
  `descriptionHtml`, `descriptionPlain`, `shouldDisplayCompensationOnJobPostings`,
  `compensation { compensationTierSummary, scrapeableCompensationSalarySummary,
  compensationTiers [...], summaryComponents [{ compensationType, interval, currencyCode,
  minValue, maxValue }] }`. Without published pay the summaries are null and the lists empty
  (Linear, Notion). Seen types: `Salary` (`interval: "1 YEAR"`), `EquityCashValue`,
  `EquityPercentage` (`interval: "NONE"`). No `isListed: false` job was seen.
- Mapping: skip `isListed == false`; url = `jobUrl`; applyUrl = `applyUrl`; locationText =
  `location` then the secondary locations, joined by ", "; remoteHint from `workplaceType`
  (null → REMOTE if `isRemote`, else UNKNOWN); employmentHint = `employmentType`;
  description = `descriptionPlain` (fallback HTML → text); postedAt = `publishedAt`;
  tags = department, team. Salary: exactly one `Salary` component with an amount and interval
  `1 YEAR`/`1 MONTH`/`1 HOUR` → structured min/max/currency/period; otherwise none, and
  `Compensation: {compensationTierSummary}` is appended to the description when present.

## Company list

- Seeded from `config/companies.yaml` on startup (`CompanySeeder`, data-model.md `company`);
  the DB keeps the check status. Removing a board from the file disables it.
- Admin page lists companies with status, last check, active job count.
- Finding target companies is ongoing manual work in Phase 1; automated discovery
  (detect ATS for companies seen in other sources) is Phase 1.5.

## Config keys (`app.sources.greenhouse|lever|ashby.*`, `app.companies.path`)

| Key | Default | Meaning |
|---|---|---|
| `enabled`, `max-items`, `min-interval` | `true`, `3000`, `12h` | shared keys (config-spec §2) |
| `base-url` | `https://boards-api.greenhouse.io` / `https://api.lever.co` / `https://api.ashbyhq.com` | overridden in tests |
| `lever.eu-base-url` | `https://api.eu.lever.co` | host for `LEVER_EU` boards |
| `request-delay` | `1s` | pause between two boards |
| `app.companies.path` | `../config/companies.yaml` | the company list |

Fixtures captured: 2026-10-22 — `fixtures/greenhouse/gitlab.json` (5 of 232 jobs: `Remote`,
`Remote, Canada`, `Bangalore, India`, two multi-location; `meta.total` kept as received),
`fixtures/lever/spotify.json` (5 of 81: remote, hybrid, onsite, no `commitment`, two
`allLocations`), `fixtures/ashby/linear.json` (3 of 34: Remote, Hybrid, with a secondary
location; no compensation) and `fixtures/ashby/ramp.json` (4 of 170: three with one Salary plus
equity, one without compensation; two Hybrid, two OnSite). Jobs are unmodified; only the lists were trimmed.
