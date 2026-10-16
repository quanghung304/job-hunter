# Shortcuts for both toolchains (docs/conventions.md, "Makefile targets").

MVNW := cd backend && ./mvnw
FRONTEND_READY := $(wildcard frontend/package.json)
NO_FRONTEND := @echo "frontend not set up yet (T-019)"

.PHONY: verify verify-backend verify-frontend dev dev-backend dev-frontend run api e2e

verify: verify-backend verify-frontend

verify-backend:
	$(MVNW) verify

verify-frontend:
ifeq ($(FRONTEND_READY),)
	$(NO_FRONTEND)
else
	cd frontend && npm ci && npm run lint && npm run typecheck && npm test
endif

# Backend on :8080 and Vite on :5173 (proxies /api) in parallel.
dev:
	$(MAKE) -j2 dev-backend dev-frontend

dev-backend:
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=local

dev-frontend:
ifeq ($(FRONTEND_READY),)
	$(NO_FRONTEND)
else
	cd frontend && npm run dev
endif

# Builds the SPA into the backend and serves everything on http://localhost:8080.
run:
ifeq ($(FRONTEND_READY),)
	$(NO_FRONTEND)
else
	cd frontend && npm run build
endif
	$(MVNW) -Pwith-frontend spring-boot:run -Dspring-boot.run.profiles=local

# Re-exports docs/architecture/openapi.json, then regenerates the TypeScript types.
api:
	$(MVNW) verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiExportIT -Dapi.export=true
ifeq ($(FRONTEND_READY),)
	$(NO_FRONTEND)
else
	cd frontend && npm run gen:api
endif

e2e:
ifeq ($(FRONTEND_READY),)
	$(NO_FRONTEND)
else
	@echo "e2e is set up in T-019/T-020"; exit 1
endif
