# Shortcuts for both toolchains (docs/conventions.md, "Makefile targets").

MVNW := cd backend && ./mvnw
NPM := cd frontend && npm

.PHONY: verify verify-backend verify-frontend dev dev-backend dev-frontend run api e2e validate-companies

# Backend (unit + integration, needs Docker) then frontend (lint, typecheck, tests).
verify: verify-backend verify-frontend

verify-backend:
	$(MVNW) verify

verify-frontend:
	$(NPM) ci && npm run lint && npm run typecheck && npm test

# Backend on :8080 and Vite on :5173 (proxies /api) in parallel; open http://localhost:5173.
# No recursive $(MAKE): it breaks when make is installed in a path with spaces (Windows).
dev:
	(cd frontend && npm run dev) & $(MVNW) spring-boot:run -Dspring-boot.run.profiles=local; wait

dev-backend:
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=local

dev-frontend:
	$(NPM) run dev

# Builds the SPA into the backend and serves everything on http://localhost:8080.
run:
	$(NPM) run build
	$(MVNW) -Pwith-frontend spring-boot:run -Dspring-boot.run.profiles=local

# Re-exports docs/architecture/openapi.json, then regenerates frontend/src/api/schema.d.ts.
api:
	$(MVNW) verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiExportIT -Dapi.export=true
	$(NPM) run gen:api

# Fetches every board of config/companies.yaml once, prints OK/NOT_FOUND/ERROR with job counts
# and exits (1 if any board is not OK). Needs the database (docker compose up -d db).
validate-companies:
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--validate-companies

# Playwright smoke tests against a running app (start it first with `make run`).
# First time only: cd frontend && npx playwright install chromium
e2e:
	$(NPM) run e2e
