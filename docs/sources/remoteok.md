# Remote OK (`remoteok`) — Phase 1.5

- Endpoint: `GET https://remoteok.com/api` (optional `?tags=a,b`). No key.
- The response is an array whose **first element is a legal/metadata object, not a job** —
  skip index 0. The API terms are inside that element; log them on each run.
- Returns only the latest ~100 postings; must run regularly to not miss items.
- Terms: link back to the Remote OK posting URL (followed link, no nofollow) and mention
  Remote OK as the source, or API access may be suspended. Our UI must show
  "via Remote OK" with a normal link on every job sourced from it.
- Quality warning: many items tagged remote are on-site jobs. Do not trust tags; rely on
  extraction + eligibility.
- Expected item fields: `id`, `slug`, `date`, `epoch`, `company`, `position`, `tags[]`,
  `location`, `salary_min`, `salary_max`, `description`, `url`, `apply_url` (verify).
