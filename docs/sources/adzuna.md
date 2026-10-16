# Adzuna (`adzuna`) — Phase 1.5

- Endpoint: `GET https://api.adzuna.com/v1/api/jobs/{country}/search/{page}?app_id=…&app_key=…&what=…&results_per_page=50&max_days_old=7`
  Countries of interest: `it`, `de`, `nl`, `gb` (verify the supported list in the docs).
- Auth: free developer key (`ADZUNA_APP_ID`, `ADZUNA_APP_KEY`).
- Limits (free tier, as reported Jul 2026 — verify): ~25 calls/min, 250/day, 1,000/week,
  2,500/month; trial period after which a licence agreement may be needed. Budget the
  number of queries × pages × runs to stay well under the daily cap.
- Data quality: `description` is truncated; `redirect_url` goes through Adzuna;
  `salary_is_predicted` marks estimated salaries (store salary only if not predicted).
- Use: discovery. Extraction on truncated text will often give UNCERTAIN; that is fine.
  Phase 1.5 may add "open original" enrichment only where the destination is an allowed source.
- Expected result fields: `id`, `title`, `description`, `redirect_url`, `created`,
  `company.display_name`, `location.display_name`, `location.area[]`, `contract_time`,
  `contract_type`, `salary_min`, `salary_max`, `salary_is_predicted`, `category.label`.
