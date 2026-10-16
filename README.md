# Job Hunter

Personal tool that finds remote jobs matching my background, scores them with an LLM and
tracks them. Spring Boot backend + React frontend, runs locally. Built spec-first with AI
coding agents.

- Product scope: [docs/product/PRD.md](docs/product/PRD.md)
- Architecture: [docs/architecture/overview.md](docs/architecture/overview.md),
  [api-spec.md](docs/architecture/api-spec.md), [frontend.md](docs/architecture/frontend.md)
- Running it: [docs/runbook.md](docs/runbook.md)
- For coding agents: [AGENTS.md](AGENTS.md)

## Quick start

```bash
cp config/profile.example.yaml config/profile.yaml   # then edit
cp .env.example .env                                  # then fill in keys
docker compose up -d db
make run                                              # http://localhost:8080
```

Requirements: JDK 21, Node.js LTS, Docker, make.
