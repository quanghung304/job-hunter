# We Work Remotely (`weworkremotely`)

> Verified 2026-10-17 (T-008) against the real feeds; Terms of Service read by the owner in a
> browser the same day. The HTML pages (`/remote-job-rss-feed`, `/terms-of-service`,
> `/sitemap.xml`) answer **403 with a Cloudflare JavaScript challenge** to non-browser clients
> (we do not bypass it). `robots.txt` allows everything except account/admin paths, and the
> category feeds are public RSS (HTTP 200, `application/rss+xml`, `<ttl>60</ttl>`).

- **Endpoints (RSS 2.0), all HTTP 200 on 2026-10-17:**
  - `https://weworkremotely.com/categories/remote-back-end-programming-jobs.rss` (2 items that day)
  - `https://weworkremotely.com/categories/remote-full-stack-programming-jobs.rss` (61 items)
  - `https://weworkremotely.com/categories/remote-devops-sysadmin-jobs.rss` (24 items)
  - `https://weworkremotely.com/remote-jobs.rss` — all categories, latest 87 items (not used
    by default: mostly sales/marketing/product)
  - configurable list `app.sources.weworkremotely.feeds` (paths relative to `base-url`)
- **Auth:** none · **Pagination:** none (each feed shows its recent items).
- **Rate rule:** ≥ 2 s between feeds; min interval between runs 6h.
- **Terms of Service (read 2026-10-17):** no clause on scraping, bots or feeds. WWR grants a
  "non-transferable, non-sublicensable, non-exclusive, revocable, limited-purpose right to
  access and use the Materials that we make available to you"; posted job content is
  "copyright © their respective owners"; WWR may suspend access for operational risk.
  → Personal, local use of the public feeds is fine. **Redistributing WWR listings to other
  users (a multi-user SaaS, roadmap Phase 4) is outside this right: ask WWR first**
  (hello@weworkremotely.com). The Terms do not require attribution, but we still keep the
  direct link to the original listing (`url`/`applyUrl`) and show "via We Work Remotely".
  Keep polling light (3 feeds every 6 h, 2 s apart).
- **Parser:** a hardened JDOM builder (no DOCTYPE, no external entities) reads the XML once;
  Rome maps the standard fields (title, link, guid, description, pubDate, category) from that
  document; WWR's own un-namespaced elements (`region`, `country`, `state`, `skills`, `type`,
  `expires_at`) are read from the JDOM item, because Rome only exposes namespaced extras as
  foreign markup.

## Item (verified)

```xml
<item>
  <title>Medify: Engineering Manager</title>
  <region>Anywhere in the World</region>
  <country>🇬🇧 United Kingdom of Great Britain and Northern Ireland</country>
  <state>London</state>
  <skills>React, Ruby on Rails, and Performance Management</skills>
  <category>Full-Stack Programming</category>
  <type>Full-Time</type>
  <description>&lt;img src="…"&gt;&lt;p&gt;…</description>
  <pubDate>Thu, 15 Oct 2026 13:59:07 +0000</pubDate>
  <expires_at>Sat, 14 Nov 2026 13:59:07 +0000</expires_at>
  <guid>https://weworkremotely.com/remote-jobs/medify-engineering-manager</guid>
  <link>https://weworkremotely.com/remote-jobs/medify-engineering-manager</link>
  <media:content url="https://…" type="image/png"/>   <!-- 14 of 61 items -->
</item>
```

- `description` is **entity-escaped HTML** (not CDATA); after XML parsing it is HTML. It
  usually starts with "Headquarters: …".
- `country`, `state`, `skills` are present on every item but usually empty; `country` starts
  with a flag emoji. Their meaning (company HQ vs. job location) is unclear, so they go into
  the description, not `locationText`.
- `region` seen: "Anywhere in the World", "North America Only", "USA Only".
- `type`: "Full-Time", "Contract".
- Company/title separator is ": ", sometimes with a space before the colon ("Fivetran : …").
  Every captured title had it.
- The same job (same guid) appears in its category feed and in the all-jobs feed; category
  feeds did not overlap each other in the sample.

## Mapping

| NormalizedPosting | From |
|---|---|
| externalId | `guid` (or `link`) |
| url, applyUrl | `link` (or `guid`) |
| companyName, title | split `title` on the first `":"` followed by whitespace (spaces around it trimmed); if none → companyName "Unknown" and a warning (run PARTIAL), still returned |
| locationText | `region` |
| remoteHint | REMOTE |
| employmentHint | `type` |
| descriptionText | `description` HTML → text, then `Country: …` (flag removed), `State: …`, `Skills: …` lines when non-empty |
| tags | `category`, lower-case |
| postedAt | `pubDate` (RFC 822, parsed by Rome) |
| rawPayload | the item as a JSON object of its elements (qualified names; attributes as `@name`; repeated elements as arrays) |

Same item can appear in several feeds: dedupe by `guid` inside the collector. A failing feed
(HTTP error, or an HTML challenge page instead of RSS) is a warning; the run is FAILED only if
every feed failed.

## Config keys (`app.sources.weworkremotely.*`)

| Key | Default | Meaning |
|---|---|---|
| `enabled`, `max-items`, `min-interval` | `true`, `200`, `6h` | shared keys (config-spec §2) |
| `base-url` | `https://weworkremotely.com` | overridden in tests |
| `feeds` | the three category paths above | read in order |
| `feed-delay` | `2s` | pause between feed requests |

Fixture captured: 2026-10-17 — `fixtures/weworkremotely/full-stack.xml` (6 of 61 items of the
full-stack feed: "Fivetran : …", flag country, state/skills, `media:content`, Contract,
"USA Only") and `remote-jobs.xml` (4 of 87 items of the all-jobs feed: other categories, a
title with `&amp;`, and 2 guids shared with `full-stack.xml`). The XML text is unmodified;
only `<item>` blocks were removed.
