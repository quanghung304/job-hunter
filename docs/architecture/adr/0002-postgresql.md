# ADR-0002: PostgreSQL (Docker) instead of an embedded DB

Status: Accepted · Date: 2026-10-16

## Context
Raw payloads and LLM output are JSON. A possible SaaS phase needs a server database.
The owner already uses Docker.

## Decision
PostgreSQL 16 in Docker Compose, `jsonb` for payloads/attributes, Flyway migrations,
Testcontainers in integration tests.

## Alternatives considered
- H2 file mode (used in the rental bot) — weak JSON support, different SQL dialect from
  production, migration later.
- SQLite — needs a community Hibernate dialect, weak concurrency, same migration problem.

## Consequences
- Requires Docker running for the app and for integration tests.
- Backups via `pg_dump` (runbook).
