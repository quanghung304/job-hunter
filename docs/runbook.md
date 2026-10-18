# Runbook

## Prerequisites

- JDK 21, Node.js LTS with npm ≥ 9.5 (`npm i -g npm@latest`), Docker (Compose v2), make, Git.
- First `make e2e` only: `cd frontend && npx playwright install chromium`.
- Anthropic API key; Telegram bot token + chat id (create via @BotFather, then send the bot
  a message and read the chat id from `getUpdates`).

## First setup

```bash
cp .env.example .env                       # fill values
cp config/profile.example.yaml config/profile.yaml
docker compose up -d db
make run                                   # builds the SPA and starts the backend
```

Open `http://localhost:8080` (board) and `http://localhost:8080/admin`.
API docs while running: `http://localhost:8080/swagger-ui.html`.

Environment variables are loaded from `.env` by Docker Compose for the DB and by the
backend's `local` Spring profile (`spring.config.import=optional:file:../.env[.properties]`).

## Development

```bash
docker compose up -d db
make dev        # backend on :8080 + Vite on :5173 with hot reload; open http://localhost:5173
```

Use `app.llm.force-model=fake` in `application-local.yml` while working on the UI to avoid
LLM cost.

## Daily operation

- Pipeline runs at 07:00 and 19:00 Europe/Rome while the app is running; on start it
  catches up if the last run is older than 6h.
- Digest at 08:30. Alerts immediately for score ≥ alert threshold.
- Run now: Admin → "Run pipeline", or `curl -X POST localhost:8080/api/admin/pipeline/run`.

## Changing the profile

Edit `config/profile.yaml`, restart (the app does not start without a valid profile; the
startup error lists each invalid field). Prefilter and eligibility are re-evaluated
automatically. To re-score active jobs with the new profile: Admin → "Re-score active jobs"
(shows the number of LLM calls first).

## LLM models and quotas

- Models and providers are listed in `config/llm.yaml`; keys live in `.env`. Default: Groq
  free tier for both steps. Get a key from each provider you want to use and add it to
  `.env`, then restart (keys are read at startup).
- **When a model runs out** you get a Telegram message and a banner on every page. Click
  "Switch model", pick another usable model, optionally tick "Process waiting jobs now".
  If you do nothing, waiting jobs are processed automatically after the limit resets.
- **Paid models (Claude) are off by default** (ADR-0009). To allow them set
  `LLM_ALLOW_PAID_CALLS=true` in `.env` and restart; the monthly budget
  (`app.llm.monthly-budget-usd`) still applies. While off, a PAID assignment or fallback
  pauses that step (`PAID_NOT_ALLOWED`), nothing is charged.
- **Auto-switch**: Admin → LLM models → "Auto-switch to fallback". Leave "Allow paid
  fallback" off unless you accept Claude costs (they count against the monthly budget).
- **Adding a model**: add an entry under `models:` in `config/llm.yaml` (provider, modelId,
  tier, known limits, `minOutputTokens` for reasoning models), restart, press "Test" on the
  Admin page, run the eval with it, then switch to it. Before the Admin page exists:
  `cd backend && ./mvnw test -Plive -Dtest=LlmProvidersLiveTest` (FREE models only; add the
  id to the test's list).
- **Check which models a key can use**: Groq lists them at `GET https://api.groq.com/openai/v1/models`
  (a model missing there answers 404 → status ERROR). OpenRouter free models end in `:free`.
- **Local model**: install Ollama, `ollama pull qwen2.5:7b-instruct`, keep Ollama running,
  choose "Local model (Ollama)". Slower on CPU; set `app.llm.timeout-local` higher if needed.
- After switching the scoring model, existing scores keep their old model label. Use
  "Re-score active jobs" if you want all jobs judged by the same model.

## Changing a prompt

1. Copy `docs/ai/prompts/score-v1.md` to `score-v2.md`, edit.
2. Run the eval with both versions; record results in `docs/ai/eval/results.md`.
3. If better, set `app.llm.score-prompt=score-v2` and restart. Old assessments stay;
   new ones are created on demand (Admin → "Re-score active jobs").

## Troubleshooting

| Symptom | Check | Fix |
|---|---|---|
| A source shows 0 fetched for 2+ runs | Admin sources table: error; run `cd backend && ./mvnw verify -Plive -Dit.test=<Source>CollectorLiveTest` | API changed → capture new fixture, update mapper and `docs/sources/<code>.md`; mark `broken` in catalog until fixed |
| HTTP 429 from a source | Run frequency | Raise `app.sources.<code>.min-interval` |
| HTTP 403 / blocked | Terms changed? | Disable source, update catalog status |
| Many `FAILED` extractions | `llm_call.error` | Invalid JSON → the model may be too weak for the prompt: run the eval, switch model or write a new prompt version |
| Banner "Extraction paused" / "Scoring paused" | Admin → LLM models (status, detail, resets at) | Switch model, wait for reset, or enable auto-switch |
| Model shows "Error" | Detail text | Missing/invalid key in `.env`, or model id removed by the provider → fix `config/llm.yaml`, restart, press "Test" |
| Hitting limits every day | Usage today vs limit | Tighten prefilter, lower run frequency, or split steps across two providers (e.g. extraction on OpenRouter free, scoring on Groq) |
| "Budget reached" in logs | Admin LLM usage | Raise `monthly-budget-usd` or tighten prefilter |
| Telegram not sending | `notification.error` | Token/chat id; bot must have received a message from you first |
| Page reload on `/jobs/42` gives 404 | SPA fallback | Check `SpaFallbackController` and that the app was started with `make run` |
| Frontend type errors after pulling | API contract changed | `make api` |
| Jobs I dismissed reappear | Fingerprint changed (title edited by employer) | Expected occasionally; dismiss again |

## Backup and restore

```bash
docker compose exec db pg_dump -U jobhunter jobhunter > data/backup-$(date +%F).sql
docker compose exec -T db psql -U jobhunter jobhunter < data/backup-YYYY-MM-DD.sql
```

## Resetting

`docker compose down -v` deletes all data. Collected jobs can be re-fetched only as far
back as each source still lists them; tracking history is lost.
