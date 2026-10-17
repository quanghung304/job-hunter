# T-003: Database schema and persistence layer

Phase: 1 · Depends on: T-001 · Size: M

## Goal
All Phase 1 tables exist via Flyway, with JPA entities and repositories inside their
owning modules.

## Read first
- `docs/architecture/data-model.md` (all) · `docs/conventions.md` (Database)

## In scope
- Migrations `V2__create_core_tables.sql` (collector_run, raw_posting, job, company),
  `V3__create_enrichment_tables.sql` (job_extraction, job_assessment),
  `V4__create_tracking_tables.sql` (job_tracking, job_tracking_event, notification),
  `V5__create_llm_call.sql`. Indexes from §5. Check constraints for enum text columns.
- Entities + Spring Data repositories, placed per module ownership:
  `source` (CollectorRun, Company), `ingest` (RawPosting, Job),
  `enrich` (JobExtraction, JobAssessment — `scoring` writes score fields only through
  `enrich`'s public `AssessmentService`), `tracking` (JobTracking, JobTrackingEvent),
  `notify` (Notification), `llm` (LlmCall).
- jsonb mapping with Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)`; text[] with
  `@JdbcTypeCode(SqlTypes.ARRAY)`.
- `job_tracking` status enum and `TrackingStatusMachine` (pure class) implementing §2
  transitions.

## Out of scope
Business services beyond the status machine.

## Acceptance criteria
- [x] `./mvnw verify` runs migrations on Testcontainers Postgres.
- [x] Each repository has a round-trip IT (save → load, jsonb and arrays intact).
- [x] Unique constraints behave as in data-model (duplicate insert fails).

## Tests
- `*RepositoryIT` per aggregate. `TrackingStatusMachineTest`: every allowed and every
  forbidden transition (generate the full matrix).

## Docs to update
Update `data-model.md` if any column/type differs (and say why in progress.md).
