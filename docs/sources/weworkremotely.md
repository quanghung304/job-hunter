# We Work Remotely (`weworkremotely`)

> Feed URLs and item elements are expected values. **Verify with captured feeds (T-008 step 1)**;
> category feed slugs must be checked on the site's RSS page.

- **Endpoints (RSS 2.0):**
  - `https://weworkremotely.com/categories/remote-back-end-programming-jobs.rss`
  - `https://weworkremotely.com/categories/remote-full-stack-programming-jobs.rss`
  - `https://weworkremotely.com/categories/remote-devops-sysadmin-jobs.rss`
  - configurable list `app.sources.weworkremotely.feeds`
- **Auth:** none · **Pagination:** none (feed shows recent items)
- **Rate rule:** ≥ 2 s between feeds; min interval between runs 6h.
- **Terms:** attribution and a direct link to the original listing are required.
- **Parser:** Rome (`com.rometools:rome`). Unknown elements (`region`, `type`) are read via
  foreign markup.

## Expected item

```xml
<item>
  <title>Acme Corp: Senior Backend Engineer</title>
  <region>Anywhere in the World</region>
  <category>Back-End Programming</category>
  <type>Full-Time</type>
  <description><![CDATA[ html ]]></description>
  <pubDate>Fri, 10 Oct 2026 12:00:00 +0000</pubDate>
  <guid>https://weworkremotely.com/remote-jobs/acme-corp-senior-backend-engineer</guid>
  <link>https://weworkremotely.com/remote-jobs/acme-corp-senior-backend-engineer</link>
</item>
```

## Mapping

| NormalizedPosting | From |
|---|---|
| externalId | `guid` (or `link`) |
| url, applyUrl | `link` |
| companyName, title | split `title` on the first `": "`; if no separator → companyName "Unknown", warning |
| locationText | `region` |
| remoteHint | REMOTE |
| employmentHint | `type` |
| descriptionText | `description` HTML → text |
| tags | `category`, lower-case |
| postedAt | `pubDate` |
| rawPayload | item converted to JSON object of its elements |

Same item can appear in several category feeds: dedupe by `guid` inside the collector.

Fixture captured: —
