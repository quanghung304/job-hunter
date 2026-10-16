# Roadmap

| Phase | Content | Exit criterion |
|---|---|---|
| **1 — MVP (F1 + F2)** | Collectors (remote boards, HN, Greenhouse/Lever/Ashby), dedupe, prefilter, LLM extraction + eligibility + scoring, tracking UI, Telegram | PRD §7 |
| 1.5 — More reach | Adzuna (IT/EU), Personio, JustJoin.it / NoFluffJobs, RemoteOK (with strict remote check), company discovery (find ATS of companies seen elsewhere) | +30% eligible jobs/week vs Phase 1 |
| 2 — CV (F3) | Master CV as structured YAML; per-job tailored CV (selection + rephrasing only, never invented facts); render to PDF via template; owner review before use | Owner uses tailored CV for ≥ 5 real applications |
| 3 — Apply (F4, F5) | Application records; Gmail read-only sync + LLM classification of replies (rejection, interview, etc.); assisted apply for Greenhouse/Lever forms (pre-fill, owner presses submit) | Status of every application updates without manual edits in ≥ 80% of cases |
| 4 — Micro-SaaS (maybe) | Multi-tenant, auth, hosted scheduling, GDPR (privacy notice, data export/delete, DPA with LLM provider), billing | Decision gate: only if Phases 1–3 are used daily and 5+ people ask for it |

## Designing now for later

Phase 1 must keep these doors open (cheap now, expensive later):

- **Jobs are global, judgments are per profile.** `job`, `raw_posting`, `job_extraction`
  have no profile column; `job_score`, `job_tracking` are keyed by `profile_id`.
  This is what lets the same job pool serve many users later (ADR-0005).
- Extraction (profile-independent) is separate from scoring (profile-dependent), so one
  extraction is reused for every profile.
- The profile is loaded through `ProfileProvider`; Phase 1 reads one YAML file.
- Collectors are stateless and know nothing about profiles, except optional search
  keywords passed in a `CollectRequest`.

## Deliberately not designed now

- Tenant isolation, row-level security, auth — Phase 4.
- Horizontal scaling, queues — a single process with a scheduler is enough.
- A public API.
