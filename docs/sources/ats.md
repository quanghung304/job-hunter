# Company ATS boards: Greenhouse (`greenhouse`), Lever (`lever`), Ashby (`ashby`)

These are the public APIs that power companies' own career pages. They return complete,
first-hand postings. Coverage depends on `config/companies.yaml` (and the `company` table).

> Endpoints below are documented publicly by each vendor (Greenhouse Job Board API,
> Lever Postings API on GitHub, Ashby Public Job Posting API). Field lists are the expected
> shape — **verify with captured fixtures (T-018 step 1).**

## Common behavior

- One `JobCollector` per ATS. Each iterates enabled companies of its ATS.
- Per company: GET board → map all jobs. A 404 marks the company `NOT_FOUND`
  (`company.last_check_status`) and disables it after 3 consecutive 404s; other errors → `ERROR`.
- Politeness: ≥ 1 s between requests for all three (Lever states 1 request/s).
  Min interval between runs: 12h.
- `since` is ignored (boards return all open jobs); ingest updates `last_seen_at`, which
  drives expiry.
- Board APIs return all jobs including on-site; mapping sets `remoteHint`, prefilter/LLM do the rest.

## Greenhouse

- `GET https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true`
- Expected: `{ "jobs": [ { "id", "title", "updated_at", "absolute_url",
  "location": { "name" }, "content" (HTML, entity-escaped), "departments": [{ "name" }],
  "offices": [{ "name" }] } ], "meta": { "total" } }`
- Mapping: externalId=`slug:id`; url/applyUrl=`absolute_url`; locationText=`location.name`;
  remoteHint REMOTE if location matches `/remote|anywhere|distributed/i` else UNKNOWN;
  description = unescape `content` then HTML → text; postedAt=`updated_at` (no created date
  exposed in this endpoint — verify); tags=department names. No salary in this API.

## Lever

- `GET https://api.lever.co/v0/postings/{slug}?mode=json`; for `ats: LEVER_EU` use
  `https://api.eu.lever.co/v0/postings/{slug}?mode=json`.
- Expected item: `id`, `text` (title), `hostedUrl`, `applyUrl`, `createdAt` (epoch ms),
  `categories { commitment, department, location, team, allLocations[] }`,
  `workplaceType` (`remote` | `hybrid` | `onsite` | `unspecified`), `descriptionPlain`,
  `lists [{ text, content(html) }]`, `additionalPlain`, `salaryRange { min, max, currency, interval }`.
- Mapping: externalId=`slug:id`; title=`text`; url=`hostedUrl`; applyUrl=`applyUrl`;
  locationText=`categories.location` (+ allLocations); remoteHint from `workplaceType`;
  employmentHint=`categories.commitment`; description = descriptionPlain + each list
  (`text` heading + content → text) + additionalPlain; salary from `salaryRange`
  (interval `per-year-salary`→YEAR, `per-month-salary`→MONTH, `per-hour-wage`→HOUR; verify values);
  postedAt=`createdAt`; tags=department, team.

## Ashby

- `GET https://api.ashbyhq.com/posting-api/job-board/{slug}?includeCompensation=true`
- Expected: `{ "jobs": [ { "id", "title", "location", "secondaryLocations": [...],
  "department", "team", "isListed", "isRemote", "workplaceType", "employmentType",
  "descriptionHtml", "descriptionPlain", "publishedAt", "jobUrl", "applyUrl",
  "compensation": { ... } } ] }`
- Mapping: skip `isListed == false`; externalId=`slug:id`; url=`jobUrl`; applyUrl=`applyUrl`;
  remoteHint from `isRemote`/`workplaceType`; employmentHint=`employmentType`;
  description=`descriptionPlain` (fallback HTML → text); postedAt=`publishedAt`;
  salary: parse `compensation` summary only if a single clear range exists (verify structure),
  else leave null and append the compensation summary text to the description.

## Company list

- Seeded from `config/companies.yaml`; the DB `company` table keeps check status.
- Admin page lists companies with status, last check, job count.
- Finding target companies is ongoing manual work in Phase 1; automated discovery
  (detect ATS for companies seen in other sources) is Phase 1.5.

Fixtures captured: —
