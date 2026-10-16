# ADR-0006: Server-rendered UI (Thymeleaf + htmx) and Telegram

Status: Superseded by ADR-0007 (UI part only; Telegram decision still stands) · Date: 2026-10-16

## Context
Tracking needs a list with filters, a detail view and one-click status changes.
Options: SPA (React), Notion database sync, or server-rendered pages.

## Decision
Thymeleaf templates with htmx for partial updates, served by the same app on localhost.
Telegram for push notifications. No JavaScript build step.

## Alternatives considered
- React SPA — a second toolchain for a single-user tool.
- Notion as the tracker — no build effort, but two-way sync, API limits and data outside
  the DB; can be added later as an export.

## Consequences
- Simple UI; if the SaaS phase happens, the UI may be rewritten, while services stay.
