# ADR-0003: Public APIs and feeds only; no HTML or browser scraping in Phase 1

Status: Accepted · Date: 2026-10-16

## Context
LinkedIn, Indeed and Glassdoor forbid scraping in their terms and block bots aggressively.
Many boards and all major ATSs (Greenhouse, Lever, Ashby) publish JSON APIs or RSS feeds
meant for reuse. HTML scrapers break when pages change and create legal risk for a SaaS.

## Decision
Collectors use only documented/public JSON APIs and RSS feeds listed in
`docs/sources/catalog.md`. Each source's terms (attribution, rate limits) are recorded in
its spec and implemented. No Playwright/Selenium, no proxy rotation, no login.

## Alternatives considered
- JobSpy-style scraping of LinkedIn/Indeed — highest volume, but ToS violation, frequent
  blocks, and not a foundation for a SaaS.

## Consequences
- Lower volume; compensated by ATS boards of chosen companies and HN threads.
- A source can be added only after its catalog row is filled and its status is `allowed`.
