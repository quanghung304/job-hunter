# T-019: Frontend bootstrap

Phase: 1 · Depends on: T-001 · Size: M

## Goal
A React + TypeScript + Vite app in `frontend/` with routing, TanStack Query, Tailwind +
shadcn/ui, a typed API client generated from `openapi.json`, test tooling, and Makefile
targets wired — rendering a shell with three empty pages.

## Read first
- `docs/architecture/frontend.md` (all) · ADR-0007 · `docs/conventions.md` (TypeScript / React,
  Makefile) · `docs/testing.md` (Frontend layers)

## In scope
- Vite React-TS template; `strict: true`; path alias `@/` → `src/`.
- Dependencies: react-router, @tanstack/react-query (+ devtools in dev only), openapi-fetch,
  openapi-typescript (dev), tailwindcss, shadcn/ui init (button, badge, input, select,
  checkbox, dialog, textarea, toast/sonner, table, card, skeleton, slider), lucide-react.
- Dev: vitest, @testing-library/react, @testing-library/user-event, @testing-library/jest-dom,
  jsdom, msw, @playwright/test, eslint (typescript-eslint, react-hooks), prettier.
- Scripts: `dev`, `build`, `preview`, `test`, `lint`, `typecheck` (`tsc --noEmit`),
  `gen:api` (`openapi-typescript ../docs/architecture/openapi.json -o src/api/schema.d.ts`),
  `e2e`.
- `vite.config.ts`: proxy `/api` → `http://localhost:8080`.
- `src/api/client.ts` (openapi-fetch, base URL `/`), `src/api/queries.ts` with `useMeta()`
  as the first hook, `src/test/handlers.ts` + MSW server setup for Vitest.
- Layout: top nav (Board, Admin), content area, toast provider; routes `/`, `/jobs/:id`,
  `/admin` with placeholder pages; 404 page.
- Shared components: `ErrorState` (shows Problem Details `detail`), `EmptyState`, `PageSkeleton`.
- Update root `Makefile` targets (`verify`, `dev`, `run`, `api`) to include the frontend.
- No external CDN fonts/scripts.

## Out of scope
Real page content (T-020, T-021).

## Acceptance criteria
- [ ] `make dev` serves the shell on :5173 and the nav shows the profile id from `/api/meta`.
- [ ] `make run` serves the same shell on :8080; reloading `/jobs/1` works (SPA fallback).
- [ ] `make verify` runs backend and frontend checks.
- [ ] `npm run gen:api` produces `schema.d.ts` with no manual edits.

## Tests
`App.test.tsx` (routes render), `ErrorState.test.tsx`, a `useMeta` test with MSW.
One Playwright spec that opens `/` (wired into `make e2e`; may be skipped in CI if no stack).

## Docs to update
Record chosen major versions in `progress.md`; adjust `frontend.md` if a choice changed.
