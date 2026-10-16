# ADR-0005: Jobs are global; assessments and tracking are per profile

Status: Accepted · Date: 2026-10-16

## Context
Phase 1 has one user. A SaaS phase would have many users sharing the same job pool.

## Decision
`raw_posting`, `job`, `job_extraction` have no profile reference. `job_assessment`,
`job_tracking`, `job_tracking_event` carry `profile_id`. The profile is read through
`ProfileProvider` (one YAML file in Phase 1). No auth, no tenant model now.

## Consequences
- Moving to multi-user later adds a `profile`/`user` table and auth, without reshaping
  the job tables.
- Every query in `tracking`, `scoring`, `notify` must filter by `profile_id` from day one.
