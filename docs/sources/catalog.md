# Source catalog

Status values: `allowed` (implement/keep), `planned` (later phase), `broken` (temporarily
off), `forbidden` (never implement). Terms were researched 2026-10-16 from public
documentation and third-party summaries; **re-read the official docs before implementing**
and update "Verified" with the date you did.

| Code | Source | Kind | Auth | Phase | Status | Key limits / terms | Spec | Verified |
|---|---|---|---|---|---|---|---|---|
| `arbeitnow` | Arbeitnow | JSON API | none | 1 | allowed | Paginated, 300/page, hourly refresh, rate limit header 50; link back requested | [arbeitnow.md](arbeitnow.md) | 2026-10-17 (API `meta.terms`; site ToS not re-read) |
| `himalayas` | Himalayas | JSON API | none (optional free key) | 1 | allowed | Cursor pages of ≤ 20; public shared allowance (key: 60/min, 10k/day); apply via `applicationLink`, show source credit, no redistribution | [himalayas.md](himalayas.md) | 2026-10-17 (API docs + Terms §30A) |
| `jobicy` | Jobicy | JSON API | none | 1 | allowed | 7-day window, 3 h delay, newest first, cursor, count ≤ 200; credit Jobicy and keep the canonical job URL; poll ≤ once per hour | [jobicy.md](jobicy.md) | 2026-10-17 (API docs + fair use) |
| `weworkremotely` | We Work Remotely | RSS | none | 1 | allowed | Category feeds; attribution + direct link required | [weworkremotely.md](weworkremotely.md) | — |
| `hn-whoishiring` | Hacker News "Who is hiring?" | JSON API (Algolia + Firebase) | none | 1 | allowed | Monthly thread, free-text comments; polite delay | [hn-whoishiring.md](hn-whoishiring.md) | — |
| `greenhouse` | Greenhouse job boards | JSON API | none | 1 | allowed | Public GET; per company slug; no salary in board API | [ats.md](ats.md) | — |
| `lever` | Lever postings | JSON API | none | 1 | allowed | Public; 1 request/s; EU host for EU accounts | [ats.md](ats.md) | — |
| `ashby` | Ashby job boards | JSON API | none | 1 | allowed | Public; optional compensation | [ats.md](ats.md) | — |
| `adzuna` | Adzuna | JSON API | free key | 1.5 | planned | Free tier ~25/min, 250/day, 2,500/month; truncated descriptions; links via Adzuna; licence may be needed after trial | [adzuna.md](adzuna.md) | — |
| `remoteok` | Remote OK | JSON API | none | 1.5 | planned | Latest ~100 only; mandatory followed link-back + credit; many "remote" items are on-site | [remoteok.md](remoteok.md) | — |
| `remotive` | Remotive | JSON API | none | — | planned (low value) | Very small feed, 24h delay, ~4 calls/day | — | — |
| `workingnomads` | Working Nomads | JSON API | none | 1.5 | planned | — | — | — |
| `justjoinit` | JustJoin.it | JSON | none | 1.5 | planned | Check terms; EU/Poland tech, salaries | — | — |
| `nofluffjobs` | NoFluffJobs | JSON | none | 1.5 | planned | Check terms | — | — |
| `personio` | Personio career XML | XML feed | none | 1.5 | planned | Per company | — | — |
| `smartrecruiters` | SmartRecruiters | JSON API | none | 1.5 | planned | Per company | — | — |
| `workable` | Workable | JSON | none | 1.5 | planned | Per company | — | — |
| `recruitee` | Recruitee | JSON API | none | 1.5 | planned | Per company | — | — |
| `jooble` | Jooble | JSON API | free key | — | planned (low value) | Snippets only, links via Jooble | — | — |
| `linkedin` | LinkedIn | — | — | — | **forbidden** | ToS prohibits scraping; aggressive anti-bot | — | — |
| `indeed` | Indeed | — | — | — | **forbidden** | Publisher API discontinued (2023); ToS prohibits scraping | — | — |
| `glassdoor` | Glassdoor | — | — | — | **forbidden** | Anti-bot, ToS | — | — |
| `ziprecruiter` | ZipRecruiter | — | — | — | **forbidden** | Anti-bot, ToS | — | — |

## Adding a source

1. Read the source's official API docs and terms. If scraping is prohibited or there is
   no API/feed, mark `forbidden` and stop.
2. Write `docs/sources/<code>.md` from the template below.
3. Add the catalog row with status `allowed` and the verified date.
4. Create a task file using `tasks/TEMPLATE.md`.

## Source spec template

```markdown
# <Source> (`<code>`)
- Endpoint(s), method, query params
- Auth
- Pagination
- Rate / politeness rule
- Terms: attribution, restrictions
- Expected response shape (verify with fixture)
- Field mapping → NormalizedPosting
- remoteHint rule
- Known quirks
- Config keys (app.sources.<code>.*)
- Fixture captured: <date>
```
