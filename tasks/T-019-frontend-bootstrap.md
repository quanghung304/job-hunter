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
  checkbox, dialog, textarea, toast/sonner, table, card, skeleton, slider, sheet),
  lucide-react, @fontsource/ibm-plex-sans, @fontsource/ibm-plex-mono.
- Dev: vitest, @testing-library/react, @testing-library/user-event, @testing-library/jest-dom,
  jsdom, msw, @playwright/test, eslint (typescript-eslint, react-hooks), prettier.
- Scripts: `dev`, `build`, `preview`, `test`, `lint`, `typecheck` (`tsc --noEmit`),
  `gen:api` (`openapi-typescript ../docs/architecture/openapi.json -o src/api/schema.d.ts`),
  `e2e`.
- `vite.config.ts`: proxy `/api` → `http://localhost:8080`.
- `src/api/client.ts` (openapi-fetch, base URL `/`), `src/api/queries.ts` with `useMeta()`
  as the first hook, `src/test/handlers.ts` + MSW server setup for Vitest.
- **Design tokens** exactly as frontend.md "Visual design": `src/styles/tokens.css` with all
  color variables, Tailwind theme mapping (colors, font families, radii 3/4/5/6/8, spacing
  as listed), shadcn variables set from the tokens, fonts imported in `main.tsx`,
  base `body` typography, global focus ring, `prefers-reduced-motion` rule.
- `lib/scoreBand.ts` (band + style class names), `lib/useMediaQuery.ts` (1024px breakpoint).
- `AppHeader` per frontend.md "App header" (status line may show "—" until T-014's
  overview endpoint exists); mobile header variant with menu; routes `/`, `/jobs/:id`,
  `/admin` with placeholder pages; 404 page; Sonner toaster restyled per the Toast spec.
- Shared components from frontend.md "Shared components": `ScoreBadge`, `StatusTag`,
  `EligibilityTag`, `RuleResultTag`, `FactChip`, `SectionCard`, `SectionLabel`, `Kbd`,
  `EmptyState`, `ErrorState`, `ListSkeleton`, and button variants (primary, secondary,
  quiet, leave-app) on the shadcn `Button`.
- A dev-only route `/dev/components` rendering every shared component in every variant
  (mirrors the "Components & states" mockup artboard) for visual review.
- Update root `Makefile` targets (`verify`, `dev`, `run`, `api`) to include the frontend.
- No external CDN fonts/scripts.

## Out of scope
Real page content (T-020, T-021).

## Acceptance criteria
- [ ] `make dev` serves the shell on :5173 and the nav shows the profile id from `/api/meta`.
- [ ] `make run` serves the same shell on :8080; reloading `/jobs/1` works (SPA fallback).
- [ ] `make verify` runs backend and frontend checks.
- [ ] `npm run gen:api` produces `schema.d.ts` with no manual edits.
- [ ] No raw hex colors outside `tokens.css` (`grep -rE '#[0-9a-fA-F]{6}' src --include=*.tsx` is empty).
- [ ] `/dev/components` matches the "Components & states" mockup (owner checks visually).

## Tests
`App.test.tsx` (routes render), `ErrorState.test.tsx`, a `useMeta` test with MSW,
`scoreBand.test.ts` (boundaries 49/50/69/70/84/85, null), `ScoreBadge.test.tsx`
(number always rendered, band word on `lg`), `StatusTag.test.tsx`.
One Playwright spec that opens `/` (wired into `make e2e`; may be skipped in CI if no stack).

## Docs to update
Record chosen major versions in `progress.md`; adjust `frontend.md` if a choice changed.
