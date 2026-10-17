# Job Hunter — frontend

React SPA (Vite, TypeScript strict, React Router, TanStack Query, Tailwind CSS v4 + shadcn/ui).
Design rules, tokens and component specs: `docs/architecture/frontend.md`. Decision: ADR-0007.

| Command                              | Does                                                                                         |
| ------------------------------------ | -------------------------------------------------------------------------------------------- |
| `npm run dev`                        | Vite on http://localhost:5173, proxies `/api` to the backend on :8080                        |
| `npm run build`                      | Production build into `dist/` (packaged by `make run`)                                       |
| `npm test`                           | Vitest + React Testing Library + MSW                                                         |
| `npm run lint` / `npm run typecheck` | ESLint + Prettier check / `tsc`                                                              |
| `npm run gen:api`                    | Regenerates `src/api/schema.d.ts` from `docs/architecture/openapi.json` (never edit by hand) |
| `npm run e2e`                        | Playwright against a running app (`make run`; first time `npx playwright install chromium`)  |

Usually run through the root `Makefile` (`make dev`, `make run`, `make verify`, `make api`, `make e2e`).
In dev mode, `/dev/components` shows every shared component for visual review.
