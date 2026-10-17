# Backlog

Work top to bottom unless the owner says otherwise. One task per session/branch.
Tick `[x]` when merged and add a line to `progress.md`.

## Phase 1 — MVP (F1 + F2)

| Done | Id | Task | Depends on | Size |
|---|---|---|---|---|
| [x] | T-001 | [Backend bootstrap](T-001-bootstrap.md) | — | M |
| [x] | T-002 | [Profile loading and validation](T-002-profile.md) | T-001 | S |
| [x] | T-003 | [Database schema and persistence layer](T-003-schema.md) | T-001 | M |
| [x] | T-004 | [Collector framework + Arbeitnow collector](T-004-collector-framework-arbeitnow.md) | T-003 | M |
| [x] | T-005 | [Ingest: normalize, fingerprint, dedupe, upsert](T-005-ingest.md) | T-004 | M |
| [ ] | T-006 | [Himalayas collector](T-006-himalayas.md) | T-005 | S |
| [ ] | T-007 | [Jobicy collector](T-007-jobicy.md) | T-005 | S |
| [ ] | T-008 | [We Work Remotely collector](T-008-weworkremotely.md) | T-005 | S |
| [ ] | T-009 | [Prefilter rules](T-009-prefilter.md) | T-002, T-005 | S |
| [ ] | T-010 | [LLM client, prompt loader, call log, budget guard](T-010-llm-client.md) | T-003 | M |
| [ ] | T-011 | [Extraction + eligibility](T-011-extraction-eligibility.md) | T-009, T-010 | M |
| [ ] | T-012 | [Scoring](T-012-scoring.md) | T-011 | M |
| [ ] | T-013 | [Pipeline orchestration, scheduling, expiry](T-013-pipeline.md) | T-012 | M |
| [ ] | T-014 | [REST API for board, job detail and admin](T-014-rest-api.md) | T-013 | M |
| [ ] | T-019 | [Frontend bootstrap (React)](T-019-frontend-bootstrap.md) | T-001 (can run in parallel any time after T-001) | M |
| [ ] | T-020 | [Frontend: board and job detail](T-020-frontend-board-detail.md) | T-014, T-019 | M |
| [ ] | T-021 | [Frontend: admin page](T-021-frontend-admin.md) | T-014, T-019 | S–M |
| [ ] | T-015 | [Telegram digest and alerts](T-015-telegram.md) | T-013 | S |
| [ ] | T-017 | [HN "Who is hiring?" collector](T-017-hn-whoishiring.md) | T-005 | M |
| [ ] | T-018 | [ATS collectors: Greenhouse, Lever, Ashby](T-018-ats-collectors.md) | T-005 | M |
| [ ] | T-016 | [Eval harness + export for labeling](T-016-eval.md) | T-012 (and a few days of data) | M |

Owner tasks (not for agents):
- [ ] Fill `config/profile.yaml`, `.env`.
- [ ] Re-read terms of each Phase 1 source and set "Verified" dates in the catalog.
- [ ] Add 30–100 target companies to `config/companies.yaml`.
- [ ] Label ≥ 40 jobs for the eval.

## Phase 1.5 (not detailed yet)

- Adzuna collector (IT/EU) · RemoteOK collector with strict remote check · Working Nomads ·
  JustJoin.it / NoFluffJobs · Personio / SmartRecruiters / Workable / Recruitee ·
  company discovery (detect ATS for companies seen in other sources) · Notion export.

## Phase 2+

See `docs/product/roadmap.md`. Do not create tasks for them yet.
