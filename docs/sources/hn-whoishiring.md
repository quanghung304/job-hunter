# Hacker News "Ask HN: Who is hiring?" (`hn-whoishiring`)

> Verified 2026-10-22 (T-017) against real responses of the public Algolia HN Search API
> (https://hn.algolia.com/api). The official Firebase API was only used to compare comment ids.

## Flow

1. Find threads: `GET https://hn.algolia.com/api/v1/search_by_date?tags=story,author_whoishiring&hitsPerPage=10`
   (newest first). Keep hits whose `title` starts with `Ask HN: Who is hiring?` (the same
   account also posts "Who wants to be hired?" on the same minute). Use the newest thread,
   plus the one before it on days 1–7 (UTC) of a month — but only if the newest thread is
   from the current month (on the 1st, before the new thread is posted around 15:00 UTC, the
   newest thread is last month's and it is fetched alone).
2. Fetch each thread with comments: `GET https://hn.algolia.com/api/v1/items/{storyId}`
   → nested `children`. Use **top-level children only** (each is one job post); nested
   replies are ignored.
3. Skip children without an author or text, or whose `type` is not `comment`. **Deleted
   comments are omitted by this endpoint** (2026-10-22: 266 children vs 319 `kids` in
   Firebase; the 53 missing ids are `deleted: true` there and 404 on Algolia).
4. Comments older than `CollectRequest.since` are skipped; the thread is ranked, not ordered by
   time, so the whole thread is read. Ids are deduplicated across the two threads.

- **Auth:** none · **Rate rule:** one search + one thread fetch per run (two in the first 7
  days), `call-delay` (1 s) before each thread fetch, min interval 12h. Algolia's public limit
  is far higher (10,000 requests/hour per IP).
- **Firebase fallback** (`hacker-news.firebaseio.com/v0/item/{id}.json`, one request per kid)
  is **not implemented**: several hundred requests per run; Algolia has been reliable.
- **Errors:** a failing search fails the run; a failing thread fetch is a warning (PARTIAL),
  the run fails if every thread fetch failed. A comment that cannot be read (bad id, unreadable
  date) is an item warning.
- **Terms:** public APIs; keep the comment permalink `https://news.ycombinator.com/item?id={id}`.
- Thread size (October 2026): 266 top-level comments (394 comments in all), about 10 of
  them not job posts (complaints, job seekers, aggregators); they become postings too and are
  left to the prefilter and the owner.

## Response shapes (verified)

Search (`hits[]`, other top-level keys `nbHits`, `page`, `hitsPerPage`, … ignored):

```json
{ "objectID": "49922569", "story_id": 49922569, "author": "whoishiring",
  "title": "Ask HN: Who is hiring? (October 2026)", "created_at": "2026-10-01T15:02:07Z",
  "created_at_i": 1790866927, "num_comments": 394, "points": 277, "updated_at": "…",
  "children": [49926618, 49922584, …], "story_text": "…", "_tags": ["story", "author_whoishiring", "story_49922569", "ask_hn"],
  "_highlightResult": { … } }
```

Item (the thread; each child has the same shape, `children` holds its replies):

```json
{ "id": 49922569, "type": "story", "author": "whoishiring", "title": "Ask HN: Who is hiring? (October 2026)",
  "created_at": "2026-10-01T15:02:07.000Z", "created_at_i": 1790866927, "parent_id": null, "story_id": 49922569,
  "points": 277, "text": "…", "url": null, "options": [],
  "children": [ { "id": 49922609, "type": "comment", "author": "…", "parent_id": 49922569, "story_id": 49922569,
                  "created_at": "2026-10-01T15:03:36.000Z", "created_at_i": 1790866996, "points": null,
                  "title": null, "url": null, "options": [],
                  "text": "Sidekick Labs | Senior SWE, R&amp;D Engineer, … | REMOTE (US Timezones) | Full-time | $170k to $230k …<p>Sidekick builds …",
                  "children": [] } ] }
```

- `text` is HN HTML: paragraphs separated by `<p>` (not closed), sometimes a bare `\n`,
  entities (`&#x2F;`, `&#x27;`, `&amp;`), `<a href>` links (`rel="nofollow"`), `<i>`.
- `created_at` is ISO-8601 UTC — with milliseconds in items, without in search hits;
  `created_at_i` is epoch seconds (used when `created_at` is missing).

## Parsing a comment (deterministic, no LLM)

Convention (not enforced): first line `Company | Role(s) | Location | REMOTE/ONSITE | Salary | URL`.
Real headers vary in order (`Shepherd (Series B) | ONSITE | San Francisco, …`,
`Mainmatter (url) | REMOTE (EU) | Lead Rust Engineering Consultant | …`), so the segments after
the company are classified by keywords (`HnHeaderParser`).

- **Header line** = the HTML up to the first `<p>` or line break, as plain text (links keep
  their text), whitespace collapsed.
- **Segments** = split on `|`, empty segments dropped. Without `|`, split on spaced em/en
  dashes (`PrairieLearn (Remote US) — Full-Stack Software Engineer — TypeScript / …`) when that
  gives at least three parts. Segments after the company lose URLs and outer dashes; segments
  longer than 150 chars are prose (header without a line break before the text) and ignored.
- `companyName` = first segment without URLs and without `(…)` groups that are empty or a
  remote/onsite remark (`Smarkets (https://…)` → `Smarkets`, `PrairieLearn (Remote US)` →
  `PrairieLearn`, `ParadeDB (YC S23, https://…)` → `ParadeDB (YC S23)`).
  Without a separator: the line up to the first punctuation (` - `, `,`, `;`, `:`, `(`, `!`,
  `?`, `. `), at most 60 chars cut at a word break (`DuckDuckGo - we are looking…` →
  `DuckDuckGo`). A blank company (header is only a link) becomes `Unknown (HN {id})`, so it is
  not merged with other comments.
- Each later segment is, in this order: **work mode** (`remote`, `hybrid`, `onsite`/`on-site`/
  `on site`, `in office`, `in person`), **role** (engineer, developer, designer, scientist,
  manager, lead, head, founding, staff, SWE, roles, positions, …), **salary** (`$ € £`,
  `150k`, salary, equity, USD/EUR/GBP, bonus, …), **employment** (full-time, part-time,
  contract, freelance, intern, B2B, …), else **other** (usually a place).
- `title` = the first role segment, else `See description`. Multi-role lists stay as written
  (Phase 1 keeps **one posting per comment**; the LLM extraction notes roles).
- `locationText` = work-mode and other segments joined by ` | ` (at most 200 chars), null if
  none — e.g. `ONSITE | San Francisco, CA & New York City, NY`.
- `employmentHint` = employment segments joined by `, `; null if none.
- `remoteHint` (whole header line) = REMOTE if it matches `/\bremote\b/i` after removing
  `no remote`, `not remote`, `onsite only`, `in-office only`; else HYBRID if `/\bhybrid\b/i`;
  else ONSITE if onsite/on-site/on site/in office/in person, or a removed negation; else UNKNOWN.
  (HYBRID was not in the first draft; `Hybrid - Onsite (London, UK)` is HYBRID.)
- `descriptionText` = full comment HTML → text (the header line included).
- `externalId` = comment id; `url` = permalink; `applyUrl` = first `<a href>` (else first bare
  URL in the text) with an http(s) host other than news.ycombinator.com, else the permalink.
- `postedAt` = `created_at`; `salary*` null (salary stays in the text); `tags` empty.
- `rawPayload` = the comment JSON **without** its `children` (replies are not stored).
- A header without separator is a format warning of the parser only (`HnHeader.warnings`), not
  a run warning: ~9 % of comments have one, and a PARTIAL run would not count for
  `min-interval`.

Prefilter note: titles here are messy, so the prefilter's skill-hit rule (not title regex)
usually decides; this is expected. ONSITE postings are kept only when `locationText` names the
candidate's city.

Measured on the October 2026 thread (266 comments): 187 with a role title, 79
`See description` (no role segment, or no separator); 24 without any separator.

## Config keys (`app.sources.hn-whoishiring.*`)

| Key | Default | Meaning |
|---|---|---|
| `enabled`, `max-items`, `min-interval` | `true`, `500`, `12h` | shared keys (config-spec §2); a thread has ~300 top-level comments |
| `base-url` | `https://hn.algolia.com` | overridden in tests |
| `call-delay` | `1s` | pause before each thread fetch |
| `search-hits` | `10` | stories read by the thread search (1–50) |
| `previous-thread-days` | `7` | also fetch the previous thread on days 1..N of a month (UTC); `0` = never |

Fixture captured: 2026-10-22 — `fixtures/hn-whoishiring/search-by-date.json` (the 10 newest
`whoishiring` stories; each `children` list trimmed to 3 ids), `item-october-2026.json` (thread
49922569 with 11 of 266 top-level comments: well-formatted, multi-role, `REMOTE (US only)`,
ONSITE, hybrid, URL in the company, em-dash header, free-text header without `|`, `<i>` header
ended by `\n`, two with nested replies; plus **one synthetic deleted child** — id 50023607 is
really deleted, but Algolia omits deleted comments, so its `author`/`text` are null by hand to
exercise the guard) and `item-september-2026.json` (thread 49522897, 2 of 249 comments).
Comments are unmodified.
