# PRD — Job Hunter

Status: Approved for Phase 1 · Owner: Hugh · Last updated: 2026-10-16

## 1. Problem

I am a backend developer (Java/Spring Boot, 4+ years) living in Italy and studying part-time.
I need a remote, part-time or contract job. Finding one by hand is slow:

- Relevant postings are spread across many boards and company career pages.
- Most "remote" postings are restricted to the US or another country, which is only
  visible deep in the description.
- Many postings are full-time only, senior-only, or in a stack I do not use.
- I lose track of which jobs I already looked at, saved or applied to.

## 2. Users

- **Phase 1–3:** one user (the owner), running the tool on a laptop.
- **Phase 4 (maybe):** other job seekers in a similar position (micro-SaaS). Not designed
  for now, but Phase 1 must not make it impossible (see roadmap, ADR-0005).

## 3. Goals for Phase 1 (F1 + F2)

| ID | Goal | Measure |
|---|---|---|
| G1 | Collect new postings automatically from ≥ 6 sources | Scheduled runs 2×/day without manual action |
| G2 | Drop jobs I cannot take | ≤ 5% of jobs shown to me are clearly ineligible (region, on-site, full-time-only when I set part-time only) |
| G3 | Rank the rest by fit | In the eval set, ≥ 80% of jobs I labeled HIGH score ≥ 70 |
| G4 | Track my decisions | Every shown job has a status; nothing I dismissed shows again |
| G5 | Notify me | Daily Telegram digest; instant alert for score ≥ 85 |
| G6 | Stay cheap | LLM cost ≤ 5 USD/month at personal volume |

## 4. Functional requirements

### F1 — Collect matching jobs

- F1.1 Each source has a collector that fetches recent postings via its public API or feed
  and maps them to a common `NormalizedPosting`.
- F1.2 Each collector run is recorded (start, end, fetched count, new count, error).
- F1.3 A failing source never stops other sources.
- F1.4 Duplicate postings (same job on several boards) are merged into one `job`,
  keeping links to every source.
- F1.5 Cheap rule-based prefilter (age, keywords, excluded titles) runs before any LLM call.
- F1.6 The LLM extracts structured attributes (remote type, allowed regions, employment
  type, hours, seniority, stack, languages, salary) from the description.
- F1.7 A deterministic eligibility check compares attributes to the profile:
  `ELIGIBLE`, `UNCERTAIN` or `INELIGIBLE`, with reasons.
- F1.8 The LLM scores `ELIGIBLE` and `UNCERTAIN` jobs 0–100 with a breakdown, strengths,
  gaps, red flags and a one-line summary in the profile's summary language.

### F2 — Track jobs

- F2.1 Web app (React SPA on localhost, ADR-0007) listing scored jobs, sorted by score, filterable by status,
  score range, source, eligibility, employment type and date.
- F2.2 Job detail page: normalized fields, extracted attributes, score breakdown,
  links to original postings, my notes.
- F2.3 Status changes with one click; every change is stored in history.
- F2.4 Notes per job.
- F2.5 Telegram: daily digest of new jobs above threshold; instant alert above a higher
  threshold; each message links to the job's detail page and the original posting.
- F2.6 Admin page: source health (last run, counts, errors), LLM usage this month,
  button to run the pipeline now.
- F2.7 Jobs not seen in any source for N days are marked `EXPIRED` (still visible in history).

## 5. Non-goals for Phase 1

Agents must not build these unless a task explicitly says so.

- CV tailoring or generation (F3) — Phase 2.
- Reading email / tracking application responses (F5) — Phase 3.
- Filling or submitting application forms (F4) — Phase 3, assisted only, never fully automatic.
- Scraping LinkedIn, Indeed, Glassdoor or any login-protected site.
- Headless-browser scraping of any kind.
- Multiple users, login, authentication, payments.
- Mobile app, public deployment, cloud hosting.
- Non-dev job tracks (tutoring). The profile format allows tracks, but only the
  `software` track is implemented in Phase 1.

## 6. Constraints

- Runs on the owner's laptop (macOS/Linux/Windows with Docker).
- Must work with intermittent uptime: if the laptop was off, the next run catches up.
- Sources must be used within their published terms (`docs/sources/catalog.md`).
- Personal data (profile, CV) stays local; only job text and the profile summary are
  sent to the LLM provider.

## 7. Acceptance for Phase 1

- All tasks marked Phase 1 in `tasks/backlog.md` are done.
- Eval targets in `docs/ai/scoring-spec.md` §7 are met on the labeled set.
- The app has run unattended on schedule for 7 days with the owner using the UI daily.
