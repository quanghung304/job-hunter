# Configuration spec

Two kinds of configuration:

1. **`config/profile.yaml`** — who I am and what I want. Git-ignored. Template:
   `config/profile.example.yaml`. Loaded by `profile` module; path from `app.profile.path`.
2. **`application.yml` + environment variables** — how the app runs.
3. **`config/companies.yaml`** — company boards for ATS collectors (committed).

Invalid config fails startup with a message naming the field.

For `profile.yaml` (loaded once at startup by `YamlProfileProvider`): every problem is listed
with its path (e.g. `candidate.headline: must not be blank`, `prefilter.titleInclude[1]:
invalid regex`); unknown keys are rejected to catch typos, except inside `tracks` items and
the removed `notify.alertMinScore` (instant alerts dropped, ADR-0010), which is ignored so older
profile files still load;
an empty value (`maxHoursPerWeek:`) is the same as omitting the key, so the default applies.

## 1. `profile.yaml`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | string `[a-z0-9-]+` | yes | Stable profile id stored in DB |
| `summaryLanguage` | `vi` \| `en` | no (en) | Language of the LLM one-line summary |
| `candidate.headline` | string | yes | One line, sent to the LLM |
| `candidate.yearsExperience` | number | yes | Total professional years |
| `candidate.residenceCountry` | ISO-3166 alpha-2 | yes | Where I live and would work from |
| `candidate.city` | string | no | Used to keep on-site/hybrid jobs in my city (prefilter `ONSITE_SOURCE_HINT`, eligibility `WORK_MODE`) |
| `candidate.timezone` | IANA tz | yes | e.g. `Europe/Rome` |
| `candidate.languages` | list of `{code, level}` | yes | level: `NATIVE`, `C2`, `C1`, `B2`, `B1`, `A2`, `A1` |
| `candidate.summary` | multiline string | yes | 5–15 lines of facts (no contact data); sent to the LLM |
| `candidate.skills.core` | list | yes | Strong skills; drive the score most |
| `candidate.skills.secondary` | list | no | Used before, weaker |
| `candidate.skills.learning` | list | no | Interested, little experience |
| `candidate.domains` | list | no | e.g. banking, healthcare |
| `preferences.employmentTypes` | list of `FULL_TIME`, `PART_TIME`, `CONTRACT`, `FREELANCE`, `INTERNSHIP` | yes | Acceptable types |
| `preferences.maxHoursPerWeek` | int | no | Jobs explicitly requiring more are ineligible |
| `preferences.acceptRegions` | list | yes | Region tokens a job may be restricted to and still be OK, see §1.1 |
| `preferences.timezone.maxOffsetDiffHours` | int | no (6) | Max difference between my tz and a required tz window |
| `preferences.minSalary` | `{amount, currency, period}` | no | Soft: lowers score, never ineligible |
| `preferences.seniority` | list of `JUNIOR`, `MID`, `SENIOR`, `LEAD`, `PRINCIPAL` | yes | Acceptable levels |
| `preferences.excludeCompanies` | list | no | Matched on `company_key` |
| `prefilter.maxAgeDays` | int | no (21) | Drop postings older than this |
| `prefilter.titleInclude` | list of regex (case-insensitive) | yes | Title must match at least one, OR description must contain ≥ `prefilter.minSkillHits` core skills |
| `prefilter.titleExclude` | list of regex | no | Title matching any is rejected |
| `prefilter.minSkillHits` | int | no (2) | See above |
| `notify.digestMinScore` | int | no (65) | |
| `tracks` | list | no | Phase 1 implements only `software`; other tracks are parsed and ignored with a WARN |

### 1.1 Region tokens

Used by extraction (`allowedRegions`) and by `acceptRegions`:
`WORLDWIDE`, `EU`, `EEA`, `EUROPE`, `EMEA`, `AMERICAS`, `NORTH_AMERICA`, `LATAM`, `APAC`,
`ASIA`, or an ISO-3166 alpha-2 country code (`IT`, `DE`, `US`, `VN`, …).

Eligibility region rule (implemented in `enrich`, tested with a table):
a job is region-OK if its `allowedRegions` is empty/unknown (→ UNCERTAIN), or contains
`WORLDWIDE`, or any token in `acceptRegions`, or a region that contains
`candidate.residenceCountry` (built-in map: `IT` ∈ `EU`, `EEA`, `EUROPE`, `EMEA`).

## 2. `application.yml` (prefix `app`)

| Key | Default | Meaning |
|---|---|---|
| `app.profile.path` | `../config/profile.yaml` | |
| `app.companies.path` | `../config/companies.yaml` | |
| `app.public-base-url` | `http://localhost:8080` | Used in Telegram links |
| `app.pipeline.cron` | `0 0 7,19 * * *` | `-` disables the schedule (tests do this in `src/test/resources/config/application.yml`) |
| `app.pipeline.zone` | `Europe/Rome` | Zone for all crons |
| `app.pipeline.catch-up-after` | `6h` | |
| `app.pipeline.catch-up-enabled` | `true` | Run once on startup when the last successful collector run is older than `catch-up-after`; false in tests |
| `app.pipeline.collector-timeout` | `2m` | Per collector |
| `app.http.connect-timeout` / `read-timeout` | `5s` / `20s` | Source HTTP clients (overview.md, Cross-cutting) |
| `app.sources.<code>.enabled` | per catalog | |
| `app.sources.<code>.max-items` | `200` | Per run |
| `app.sources.<code>.min-interval` | per source spec | Skip if last success is more recent |
| `app.sources.<code>.*` | | Source-specific keys documented in `docs/sources/<code>.md` |
| `app.llm.registry-path` | `../config/llm.yaml` | Model registry, see §5 |
| `app.llm.force-model` | — | If set (e.g. `fake`), overrides every assignment; used by tests and `make e2e` |
| `app.llm.extract-prompt` | `extract-v1` | File name in `docs/ai/prompts/` |
| `app.llm.score-prompt` | `score-v1` | |
| `app.llm.max-calls-per-run` | `150` | Budget guard (all models): HTTP attempts per owner action; a bulk action is one action (ADR-0010) |
| `app.llm.monthly-budget-usd` | `5.00` | Hard stop for **PAID** models when estimated month cost reaches it |
| `app.llm.allow-paid-calls` | `false` (env `LLM_ALLOW_PAID_CALLS`) | Owner's consent to PAID models (ADR-0009). While false, no request is ever sent to a PAID model (assignment, fallback, eval, "Test"); the router answers `PAID_NOT_ALLOWED` |
| `app.llm.timeout` | `60s` | Per call; local models may need more (`app.llm.timeout-local`, default `180s`) |
| `app.llm.fake.simulate` | — | Test-only behaviour of the fake model: `quota-after:<n>` answers a daily-quota 429 after n calls |
| `app.notify.telegram.enabled` | `false` | |
| `app.notify.digest-cron` | `0 30 8 * * *` | In `app.pipeline.zone`. Also read by `PipelineSchedule` for the admin page's "next digest" (T-014); `-` = none |
| `app.jobs.expire-after-days` | `14` | Not seen in any source for N days → EXPIRED |

## 3. Environment variables (secrets)

| Variable | Used for |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `ANTHROPIC_API_KEY` | Claude models (paid) |
| `GROQ_API_KEY`, `OPENROUTER_API_KEY` | Free-tier providers; only the ones referenced by models you use are needed (GitHub Models retired 2026-07-30; Cerebras removed 2026-10-18: no free tier without a credit card) |
| `LLM_ALLOW_PAID_CALLS` | `true` = consent to PAID models (`app.llm.allow-paid-calls`); default false |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | Notifications |
| `ADZUNA_APP_ID`, `ADZUNA_APP_KEY` | Phase 1.5 |
| `OWNER_CONTACT_EMAIL` | User-Agent contact for polite API use |
| `HIMALAYAS_API_KEY` | Optional free Himalayas key (higher rate limits); empty = public allowance |

Template: `.env.example`. Never log these values.

## 4. `companies.yaml`

```yaml
companies:
  - name: GitLab
    ats: GREENHOUSE      # GREENHOUSE | LEVER | LEVER_EU | ASHBY
    slug: gitlab
    notes: smoke-test entry
```

## 5. `llm.yaml` (model registry, ADR-0008)

Committed file, no secrets. Loaded at startup; invalid entries fail startup with the path
of the bad field. A model whose provider key env var is empty is listed but marked
`ERROR: missing key` and cannot be assigned.

| Field | Type | Meaning |
|---|---|---|
| `providers.<id>.type` | `anthropic` \| `openai-compatible` \| `fake` | Which `LlmClient` implementation |
| `providers.<id>.baseUrl` | URL | Required for `openai-compatible` (the client calls `{baseUrl}/chat/completions`); optional for `anthropic` (overrides the API URL, used by tests) |
| `providers.<id>.apiKeyEnv` | string | Name of the env var with the key; `""` for keyless (Ollama) |
| `models.<id>.label` | string | Shown in the UI |
| `models.<id>.provider` | provider id | |
| `models.<id>.modelId` | string | Model name sent to the provider |
| `models.<id>.tier` | `FREE` \| `PAID` \| `LOCAL` | PAID calls need `app.llm.allow-paid-calls=true` and count against `monthly-budget-usd`; fallback to PAID also needs `allowPaid`. Models of an `anthropic` provider must be `PAID` |
| `models.<id>.resetZone` | IANA zone, optional | Zone in which the provider resets daily limits; default UTC |
| `models.<id>.limits` | `{rpm, rpd, tokensPerMinute, tokensPerDay, requestsPerMonth}` all optional | Known limits used for NEAR_LIMIT warnings and to avoid hitting 429; omit what you don't know |
| `models.<id>.pricing` | `{inputPerMTok, outputPerMTok}` USD | PAID only; null → cost unknown (WARN once) |
| `models.<id>.jsonMode` | boolean, default `true` for openai-compatible | Send `response_format: {type: json_object}` when the provider supports it |
| `models.<id>.minOutputTokens` | int, optional | Lower bound for `max_tokens`. Reasoning models (gpt-oss, qwen3, nemotron) spend output tokens thinking before the answer; without room they return no content |
| `defaults.<PURPOSE>.model` | model id | Initial assignment for `EXTRACT` / `SCORE` (used when the DB has none) |
| `defaults.<PURPOSE>.fallbacks` | model ids | Ordered alternatives offered in the UI and used by auto-fallback |
| `defaults.<PURPOSE>.autoFallback` | boolean (false) | Switch automatically when the assigned model is EXHAUSTED/ERROR |
| `defaults.<PURPOSE>.allowPaid` | boolean (false) | Auto-fallback may pick a PAID model |
| `alerts.nearLimitPercent` | int (80) | NEAR_LIMIT threshold for known daily/monthly limits |

Days for limits are counted in UTC (most providers reset daily limits at UTC midnight;
verify per provider and note exceptions in the model entry as `resetZone`).
