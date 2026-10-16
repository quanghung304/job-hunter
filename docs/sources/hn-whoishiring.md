# Hacker News "Ask HN: Who is hiring?" (`hn-whoishiring`)

> Endpoints are the public Algolia HN Search API and the official HN Firebase API.
> **Verify response fields with captured fixtures (T-017 step 1).**

## Flow

1. Find threads: `GET https://hn.algolia.com/api/v1/search_by_date?tags=story,author_whoishiring&hitsPerPage=10`
   Keep hits whose `title` starts with `Ask HN: Who is hiring?`. Use the latest thread,
   plus the previous month's thread during the first 7 days of a month.
2. Fetch the thread with comments: `GET https://hn.algolia.com/api/v1/items/{storyId}`
   → nested `children`. Use **top-level children only** (each is one job post).
   Alternative: Firebase `https://hacker-news.firebaseio.com/v0/item/{id}.json` (`kids`), then
   each kid — many requests; use only if Algolia items endpoint fails.
3. Skip children that are deleted, dead, or have no `text`.

- **Auth:** none · **Rate rule:** one thread fetch per run; min interval 12h.
- **Terms:** public APIs; keep the comment permalink `https://news.ycombinator.com/item?id={id}`.
- Thread size: several hundred top-level comments per month.

## Parsing a comment (deterministic, no LLM)

Convention (not enforced): first line `Company | Role(s) | Location | REMOTE/ONSITE | Salary | URL`.

- `companyName` = first segment of the first line split on `|`, trimmed; if the first line
  has no `|`, use the first 60 chars up to the first punctuation and add a warning.
- `title` = second segment if present, else "See description".
  One comment often lists several roles: Phase 1 keeps **one posting per comment**
  (title = segment 2 trimmed to 200 chars); the LLM extraction notes roles.
- `remoteHint` = REMOTE if any segment matches `/\bremote\b/i` and not only
  `/no remote|onsite only/i`; ONSITE if `/onsite|on-site|in office/i` and no remote; else UNKNOWN.
- `descriptionText` = full comment HTML → text.
- `externalId` = comment id; `url` = permalink; `applyUrl` = first http(s) link in the text
  that is not news.ycombinator.com, else permalink.
- `postedAt` = comment `created_at`.

Prefilter note: titles here are messy, so the prefilter's skill-hit rule (not title regex)
usually decides; this is expected.

Fixture captured: —
