# ADR-0007: React SPA + REST API

Status: Accepted · Date: 2026-10-16 · Supersedes: ADR-0006 (UI part)

## Context
ADR-0006 chose Thymeleaf + htmx as the cheapest UI. The owner prefers a React frontend:
it keeps the backend a clean REST API (reusable if the tool becomes a SaaS), and it gives
the owner real React experience, which job postings in his target market often ask for.

## Decision
- Monorepo: `backend/` (Spring Boot, REST API under `/api`) and `frontend/` (React SPA).
- Frontend: React + TypeScript (strict) + Vite, React Router, TanStack Query for server
  state, Tailwind CSS + shadcn/ui components, typed API client generated from the
  backend's OpenAPI document (`openapi-typescript` + `openapi-fetch`).
- Backend publishes OpenAPI via springdoc; the generated document is committed at
  `docs/architecture/openapi.json` and is the contract between both sides.
- Local run: the backend serves the built SPA as static files (one process, one port).
  Development: Vite dev server proxies `/api` to the backend (no CORS config needed).
- Telegram notifications unchanged.

## Alternatives considered
- Thymeleaf + htmx (ADR-0006) — least work, but UI would be rewritten for a SaaS and adds
  no frontend experience.
- Angular — the owner already knows it, faster to review; React chosen for market value.
- Next.js — server rendering is unnecessary for a localhost tool and adds a Node server.

## Consequences
- Two toolchains (Maven, npm); a root `Makefile` hides the commands.
- API changes require regenerating `openapi.json` and the TS types in the same commit.
- About 3–5 extra days of work for the UI compared with ADR-0006.
