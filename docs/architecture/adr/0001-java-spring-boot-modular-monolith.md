# ADR-0001: Java 21 + Spring Boot 3, modular monolith

Status: Accepted · Date: 2026-10-16

## Context
The owner is strongest in Java/Spring Boot and already built a similar local scraper
(rental listings) with Spring Boot, an LLM for extraction and Telegram notifications.
The tool runs on one laptop; later it may become a small SaaS.

## Decision
One Spring Boot application, Maven, Java 21, organized by module packages with explicit
boundaries (see `overview.md`). No microservices, no message broker.

## Alternatives considered
- Python (scraping ecosystem, JobSpy) — faster prototyping but the owner reviews Java
  better, which matters when agents write most of the code.
- Separate services per stage — no benefit at this scale.

## Consequences
- Agents get one build, one test command.
- Module boundaries are enforced by convention and a simple ArchUnit test (T-001).
