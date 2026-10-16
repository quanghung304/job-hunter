# Configuration spec

Two kinds of configuration:

1. **`config/profile.yaml`** — who I am and what I want. Git-ignored. Template:
   `config/profile.example.yaml`. Loaded by `profile` module; path from `app.profile.path`.
2. **`application.yml` + environment variables** — how the app runs.
3. **`config/companies.yaml`** — company boards for ATS collectors (committed).

Invalid config fails startup with a message naming the field.

For `profile.yaml` (loaded once at startup by `YamlProfileProvider`): every problem is listed
with its path (e.g. `candidate.headline: must not be blank`, `prefilter.titleInclude[1]:
invalid regex`); unknown keys are rejected to catch typos, except inside `tracks` items;
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
| `notify.alertMinScore` | int | no (85) | |
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
| `app.pipeline.cron` | `0 0 7,19 * * *` | |
| `app.pipeline.zone` | `Europe/Rome` | Zone for all crons |
| `app.pipeline.catch-up-after` | `6h` | |
| `app.pipeline.collector-timeout` | `2m` | Per collector |
| `app.sources.<code>.enabled` | per catalog | |
| `app.sources.<code>.max-items` | `200` | Per run |
| `app.sources.<code>.min-interval` | per source spec | Skip if last success is more recent |
| `app.sources.<code>.*` | | Source-specific keys documented in `docs/sources/<code>.md` |
| `app.llm.provider` | `anthropic` | `anthropic` \| `fake` |
| `app.llm.extract-model` | `claude-haiku-5-5` | |
| `app.llm.score-model` | `claude-haiku-5-5` | |
| `app.llm.extract-prompt` | `extract-v1` | File name in `docs/ai/prompts/` |
| `app.llm.score-prompt` | `score-v1` | |
| `app.llm.max-calls-per-run` | `150` | Budget guard |
| `app.llm.monthly-budget-usd` | `5.00` | Hard stop when estimated month cost reaches it |
| `app.llm.pricing.<model>.input-per-mtok` / `output-per-mtok` | — | USD per million tokens; owner fills from the provider's price page |
| `app.llm.timeout` | `60s` | |
| `app.notify.telegram.enabled` | `false` | |
| `app.notify.digest-cron` | `0 30 8 * * *` | |
| `app.jobs.expire-after-days` | `14` | Not seen in any source for N days → EXPIRED |

## 3. Environment variables (secrets)

| Variable | Used for |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `ANTHROPIC_API_KEY` | LLM |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | Notifications |
| `ADZUNA_APP_ID`, `ADZUNA_APP_KEY` | Phase 1.5 |
| `OWNER_CONTACT_EMAIL` | User-Agent contact for polite API use |

Template: `.env.example`. Never log these values.

## 4. `companies.yaml`

```yaml
companies:
  - name: GitLab
    ats: GREENHOUSE      # GREENHOUSE | LEVER | LEVER_EU | ASHBY
    slug: gitlab
    notes: smoke-test entry
```
