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
- Digest at 08:30: new jobs waiting for analysis, and scored jobs above the digest threshold.
- Run now: Admin → "Run pipeline", or `curl -X POST localhost:8080/api/admin/pipeline/run`.
- The pipeline never calls the LLM. New jobs appear as "Not analyzed". Open a job you want to
  judge and press "Analyze" (one extraction call), check the eligibility criteria, mark it
  Eligible or Not eligible if you like (ADR-0011), then "Score" (one scoring call) unless you
  marked it Not eligible. On the admin page you can select jobs and analyze or score them in bulk, or
  re-score active jobs. Each of these shows the count first.

## Telegram digest

Off by default. To turn it on: put `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` in `.env`, set
`app.notify.telegram.enabled: true` (application.yml or `APP_NOTIFY_TELEGRAM_ENABLED=true`), restart.
Every day at `app.notify.digest-cron` (default 08:30 Europe/Rome) one digest lists the number of new
jobs waiting for Analyze and the jobs scored since the last digest with a score of at least
`notify.digestMinScore` (top 15, then "and N more"). A job is in at most one digest; nothing is
sent when there is nothing to report. Every send (also a failed one) is a row in `notification`.

## Changing the profile

Edit `config/profile.yaml` and restart (the app does not start without a valid profile; the
startup error lists each invalid field), or use the Profile page (ADR-0012, from T-028; the API
`PUT /api/profile` exists since T-027); a save needs no restart. Prefilter and eligibility checks
are re-evaluated automatically (no LLM call); your eligibility decisions and scores are kept. To
re-score active jobs with the new profile: Admin → "Re-score active jobs" (shows the number of LLM
calls first).

What a save does to the file:
- It is rewritten in canonical form: comments and your key order are gone, defaults are written
  out, a one-line header comment says it was saved from the app. `tracks` is kept as it was.
- The previous version is `config/profile.yaml.bak` (only the last one). To undo a save, copy it
  back and restart.
- **Hand edits after the app started** are not loaded until a restart; until then a save from the
  page is refused with `PROFILE_CHANGED` ("edited outside the app; restart"), so the hand edit is
  never overwritten. Restart, reload the page, then edit.

## LLM models and quotas

- Models and providers are listed in `config/llm.yaml`; keys live in `.env`. Default: Groq
  free tier for both steps. Get a key from each provider you want to use and add it to
  `.env`, then restart (keys are read at startup).
- **When a model runs out** you get a Telegram message and a banner on every page. Click
  "Switch model" and pick another usable model; the Analyze/Score buttons work again.
  If you do nothing, the Analyze/Score buttons for that step stay disabled until the limit resets.
  Telegram (when enabled) says it once per model, status and day: near the limit (80%), exhausted
  (with the reset time), error (check the key), back to normal after an exhausted/error alert, and
  "switched Extraction to X" after an auto-switch. Models that serve no step stay silent. The texts
  follow the profile's summary language. Without the web UI (until T-023) switch with
  `curl -X PUT localhost:8080/api/admin/llm/assignments/EXTRACT -H 'Content-Type: application/json'
  -d '{"modelId":"groq-qwen3-27b"}'`; `GET /api/admin/llm` shows models, status and usage today.
- **After an auto-switch** the purpose stays on the fallback model; once the old model works again
  the banner suggests switching back (nothing switches back by itself).
- **Paid models (Claude) are off by default** (ADR-0009). To allow them set
  `LLM_ALLOW_PAID_CALLS=true` in `.env` and restart; the monthly budget
  (`app.llm.monthly-budget-usd`) still applies. While off, a PAID assignment or fallback
  pauses that step (`PAID_NOT_ALLOWED`), nothing is charged.
- **Auto-switch**: Admin → LLM models → "Auto-switch to fallback". Leave "Allow paid
  fallback" off unless you accept Claude costs (they count against the monthly budget).
- **Adding a model**: add an entry under `models:` in `config/llm.yaml` (provider, modelId,
  tier, known limits, `minOutputTokens` for reasoning models), restart, press "Test" on the
  Admin page (or `POST /api/admin/llm/models/<id>/test`: one tiny call, also clears an ERROR status
  after you fixed the key; a PAID model needs `{"confirmPaid":true}` and paid calls enabled), run the
  eval with it, then switch to it. Before the Admin page exists:
  `cd backend && ./mvnw test -Plive -Dtest=LlmProvidersLiveTest` (FREE models only; add the
  id to the test's list).
- **Check which models a key can use**: Groq lists them at `GET https://api.groq.com/openai/v1/models`
  (a model missing there answers 404 → status ERROR). OpenRouter free models end in `:free`.
- **Local model**: install Ollama, `ollama pull qwen2.5:7b-instruct`, keep Ollama running,
  choose "Local model (Ollama)". Slower on CPU; set `app.llm.timeout-local` higher if needed.
- After switching the scoring model, existing scores keep their old model label. Use
  "Re-score active jobs" if you want all jobs judged by the same model.

## Company boards (Greenhouse, Lever, Ashby)

- The list is `config/companies.yaml` (`name`, `ats`: `GREENHOUSE` | `LEVER` | `LEVER_EU` | `ASHBY`,
  `slug`, optional `notes`). It is read on every start: new boards are added, names updated,
  boards removed from the file are disabled. An invalid entry stops the start with the list
  of problems.
- The slug is the last part of the company's career page URL:
  `job-boards.greenhouse.io/<slug>`, `jobs.lever.co/<slug>` (EU accounts:
  `jobs.eu.lever.co/<slug>` → `LEVER_EU`), `jobs.ashbyhq.com/<slug>`.
- **Validate the list** after editing it: `docker compose up -d db`, then
  `make validate-companies`. It fetches every board once (enabled or not), prints
  `OK`/`NOT_FOUND`/`ERROR` with the number of jobs, records the status and exits (code 1 if any
  board is not OK). No pipeline run starts.
- A board that answers 404 three runs in a row is disabled (Admin → Companies). Fix the slug or
  remove the entry; a board that answers OK in `make validate-companies` is enabled again.

## Changing a prompt

1. Copy the current version (e.g. `docs/ai/prompts/score-v2.md`) to the next number
   (`score-v3.md`), edit.
2. Run the eval with both versions; record results in `docs/ai/eval/results.md`.
3. If better, set `app.llm.score-prompt` to the new id and restart. Old assessments stay;
   new ones are created on demand (Admin → "Re-score active jobs").

## Troubleshooting

| Symptom | Check | Fix |
|---|---|---|
| A source shows 0 fetched for 2+ runs | Admin sources table: error; run `cd backend && ./mvnw verify -Plive -Dit.test=<Source>CollectorLiveTest` | API changed → capture new fixture, update mapper and `docs/sources/<code>.md`; mark `broken` in catalog until fixed |
| A company shows NOT_FOUND / disabled | Career page URL of the company | Wrong slug or the company changed ATS → fix `config/companies.yaml`, `make validate-companies` |
| HTTP 429 from a source | Run frequency | Raise `app.sources.<code>.min-interval` |
| HTTP 403 / blocked | Terms changed? | Disable source, update catalog status |
| Many `FAILED` extractions | `llm_call.error` | Invalid JSON → the model may be too weak for the prompt: run the eval, switch model or write a new prompt version |
| Banner "Extraction paused" / "Scoring paused" | Admin → LLM models (status, detail, resets at) | Switch model, wait for reset, or enable auto-switch |
| Model shows "Error" | Detail text | Missing/invalid key in `.env`, or model id removed by the provider → fix `config/llm.yaml`, restart, press "Test" |
| Hitting limits every day | Usage today vs limit | Tighten prefilter, lower run frequency, or split steps across two providers (e.g. extraction on OpenRouter free, scoring on Groq) |
| "Budget reached" in logs | Admin LLM usage | Raise `monthly-budget-usd` or tighten prefilter |
| Telegram not sending | Startup log (WARN when enabled but a secret is missing), `notification.error` | `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID` in `.env`, `app.notify.telegram.enabled=true`; the bot must have received a message from you first |
| Page reload on `/jobs/42` gives 404 | SPA fallback | Check `SpaFallbackController` and that the app was started with `make run` |
| Frontend type errors after pulling | API contract changed | `make api` |
| Board shows only "Not analyzed" jobs | Expected: collection makes no LLM calls | Press "Analyze" on the jobs you want to judge |
| Jobs I dismissed reappear | Fingerprint changed (title edited by employer) | Expected occasionally; dismiss again |

## Backup and restore

```bash
docker compose exec db pg_dump -U jobhunter jobhunter > data/backup-$(date +%F).sql
docker compose exec -T db psql -U jobhunter jobhunter < data/backup-YYYY-MM-DD.sql
```

## Resetting

`docker compose down -v` deletes all data. Collected jobs can be re-fetched only as far
back as each source still lists them; tracking history is lost.
