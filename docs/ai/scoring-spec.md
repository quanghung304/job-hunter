# Prefilter, extraction, eligibility and scoring spec

Implements ADR-0004. Sections are referenced by tasks; keep numbering stable.

## 1. Flow per job and profile

LLM steps (extraction, scoring) run only when the owner asks for that one job, through the
Analyze and Score actions (ADR-0010). The pipeline run only prefilters. Each step is cached,
so a repeat request makes no call.

```
job ──► §2 prefilter ──REJECT──► assessment(prefilter_result=REJECT)    [no LLM]
            │PASS
            ▼
        §3 extraction (cached per job + prompt version)                 [LLM]
            ▼
        §4 eligibility ──INELIGIBLE──► assessment(eligibility=INELIGIBLE) [no LLM]
            │ELIGIBLE / UNCERTAIN
            ▼
        §5 scoring                                                     [LLM]
```

## 2. Prefilter (rules, profile-dependent)

Input: `job` + profile. Output: PASS/REJECT + reasons (string codes). Rules in order;
the first REJECT wins, but collect all reasons for display.

| Code | Rule |
|---|---|
| `TOO_OLD` | `posted_at` older than `prefilter.maxAgeDays` |
| `EXCLUDED_COMPANY` | `company_key` in normalized `preferences.excludeCompanies` |
| `TITLE_EXCLUDED` | title matches any `prefilter.titleExclude` regex |
| `NOT_RELEVANT` | title matches no `titleInclude` regex **and** description contains fewer than `minSkillHits` distinct `skills.core` terms (case-insensitive, word-boundary match; `Spring Boot` also matches `SpringBoot`) |
| `ONSITE_SOURCE_HINT` | `remote_hint == ONSITE` **and** `location_text` does not contain `candidate.city` (also its local name, e.g. Torino) — reject (remote only, unless it is in my city) |

Details (T-009):
- `TOO_OLD`: a job without `posted_at` is never too old.
- `EXCLUDED_COMPANY`: each `excludeCompanies` entry is normalized like `company_key`
  (`TextNormalizer.companyKey`), so "Acme, Inc." excludes `acme`.
- Skill hits (`SkillMatcher`): case-insensitive, whole words (letters/digits around a match
  prevent it: "Java" ≠ "JavaScript"). Words of a skill may be joined by space, `-`, `_` or
  nothing (`Spring Boot` ~ `SpringBoot` ~ `spring-boot`). A slash name also matches each
  part when every part has ≥ 3 characters (`Hibernate/JPA` ~ `JPA`; `CI/CD` stays whole).
  Aliases are a table in `SkillMatcher.ALIASES` (`REST API` ~ `RESTful`, `OAuth 2.0` ~
  `OAuth2`, `OpenID Connect` ~ `OIDC`, …). Hits count distinct profile skills.
- City names: case- and accent-insensitive whole-word match; English/local names are a
  table in `CityNames` (Turin ~ Torino, Munich ~ München/Muenchen, …). No `candidate.city`
  → every ONSITE job is rejected.

## 3. Extraction (LLM, profile-independent)

Prompt: `docs/ai/prompts/extract-v1.md`. Input: title, company, location text, source
remote hint, employment hint, description (truncated to 12 000 chars, keep beginning and
end). Output JSON must validate against this schema (enforced in Java after parsing;
invalid → retry once with the validation error appended, then FAILED):

| Field | Type | Values / notes |
|---|---|---|
| `remoteType` | enum | `FULLY_REMOTE`, `REMOTE_RESTRICTED`, `HYBRID`, `ONSITE`, `UNKNOWN` |
| `allowedRegions` | string[] | Region tokens (config-spec §1.1). Empty = not stated |
| `regionEvidence` | string \| null | ≤ 200 chars quoted/paraphrased from the text |
| `timezoneRequirement` | object \| null | `{ "minUtcOffset": int, "maxUtcOffset": int, "overlapHours": int \| null }` |
| `employmentTypes` | enum[] | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `FREELANCE`, `INTERNSHIP`; empty = not stated |
| `hoursPerWeek` | object \| null | `{ "min": int \| null, "max": int \| null }` |
| `seniority` | enum \| null | `JUNIOR`, `MID`, `SENIOR`, `LEAD`, `PRINCIPAL` |
| `minYearsExperience` | int \| null | |
| `requiredSkills` | string[] | max 15, canonical names (e.g. "Java", "Spring Boot", "Kubernetes") |
| `niceToHaveSkills` | string[] | max 15 |
| `requiredLanguages` | `{code, level}`[] | spoken languages, e.g. `{ "code": "de", "level": "C1" }` |
| `workAuthorization` | string \| null | e.g. "Must be authorized to work in the US", ≤ 200 chars |
| `salary` | object \| null | `{ "min", "max", "currency", "period" }` only if stated in text |
| `roleSummary` | string | ≤ 300 chars, English |
| `isAgencyOrReposting` | boolean | recruiting agency / job aggregator rather than the employer |
| `isScamSignal` | boolean | e.g. pay-to-apply, crypto "investment", personal email only, unrealistic pay |
| `confidence` | enum | `HIGH`, `MEDIUM`, `LOW` — overall confidence of extraction |

Rules the prompt must enforce: never infer facts not in the text; "Remote (US)" →
`REMOTE_RESTRICTED` + `["US"]`; "Remote, Europe" → `["EUROPE"]`; "work from anywhere" →
`["WORLDWIDE"]`; "CET ± 2h" → timezone min 0 max 4 (UTC); unknown → null/empty.

## 4. Eligibility (Java, profile-dependent, deterministic)

Each rule yields OK, UNKNOWN or FAIL with a reason code. Result:
any FAIL → `INELIGIBLE`; else any UNKNOWN on rules marked *critical* → `UNCERTAIN`;
else `ELIGIBLE`. Extraction `confidence == LOW` → at most `UNCERTAIN`.

| Code | Critical | OK | UNKNOWN | FAIL |
|---|---|---|---|---|
| `WORK_MODE` | yes | FULLY_REMOTE or REMOTE_RESTRICTED | UNKNOWN | ONSITE/HYBRID, unless `location_text` contains `candidate.city` (then OK) |
| `REGION` | yes | region rule in config-spec §1.1 passes | `allowedRegions` empty | no accepted region matches |
| `WORK_AUTH` | yes | null | — | text requires authorization for a country not in `acceptRegions` (simple match on country names/codes; else ignore) |
| `TIMEZONE` | no | null, or residence offset within the allowed window (see note) | — | outside the window |
| `EMPLOYMENT_TYPE` | yes | intersection with `preferences.employmentTypes` non-empty | empty | no intersection |
| `HOURS` | no | null or `min` ≤ `maxHoursPerWeek` | — | `min` > `maxHoursPerWeek` |
| `SENIORITY` | no | in `preferences.seniority` or null | — | not in list |
| `LANGUAGE` | yes | all required languages satisfied by `candidate.languages` (level ≥ required) | — | a required language unmet |
| `SCAM` | yes | false | — | `isScamSignal == true` |

Timezone note: compute the residence offset for the current date (DST-aware) and let
d = `preferences.timezone.maxOffsetDiffHours`. OK if `minUtcOffset - d ≤ offset ≤ maxUtcOffset + d`. The eligibility unit test must contain one
table row per cell of this table.

Implementation notes (T-011): `EligibilityEvaluator`; stored reasons are `RULE:UNKNOWN` /
`RULE:FAIL` for non-OK rules plus `FULL_TIME_ONLY_SOFT` and `CONFIDENCE:LOW`. `WORK_AUTH` looks for
country names/adjectives (table in code) and the upper-case codes US, USA, UK, EU, EEA ("work with
us" is not the US); a sentence naming no listed country is OK. Extraction output is parsed
leniently: missing lists become empty and over-long `roleSummary`/`regionEvidence`/
`workAuthorization` are cut to their limit instead of failing.

Important for this owner: many contract/freelance roles say only "full-time" in a template
while hiring contractors. Therefore when `employmentTypes == [FULL_TIME]` and the profile
does not accept FULL_TIME, the result is FAIL **only if** the text also gives
`hoursPerWeek.min ≥ 35` or says "permanent"/"employee"; otherwise UNKNOWN (critical) →
UNCERTAIN, so the owner can decide. Record reason `FULL_TIME_ONLY_SOFT`.

## 5. Scoring (LLM, profile-dependent)

Prompt: `docs/ai/prompts/score-v1.md`. Input: profile excerpt (headline, years, summary,
skills by tier, domains, languages, preferences), job (title, company, extraction JSON,
description truncated to 8 000 chars), eligibility result and reasons.

Rubric (weights sum to 100):

| Key | Weight | Meaning |
|---|---|---|
| `skills` | 40 | Required skills covered by core (full), secondary (half), learning (little) |
| `experience` | 20 | Seniority and years vs requirement |
| `arrangement` | 20 | Employment type, hours, timezone, region fit; UNCERTAIN items cost points |
| `domain` | 10 | Domain/industry overlap, transferable context |
| `other` | 10 | Salary vs `minSalary`, language, company/role quality, growth fit |

Output schema:

```json
{
  "breakdown": { "skills": 0-40, "experience": 0-20, "arrangement": 0-20, "domain": 0-10, "other": 0-10 },
  "score": 0-100,
  "strengths": ["≤ 3 items, ≤ 120 chars each"],
  "gaps": ["≤ 3 items"],
  "redFlags": ["≤ 3 items, empty if none"],
  "summary": "one sentence in the profile summaryLanguage, ≤ 200 chars",
  "applyAdvice": "one sentence: what to emphasize if applying, ≤ 200 chars"
}
```

Java validation: each breakdown value within its range; `score` must equal the sum
(if not, replace with the sum and log a warning — do not retry for this alone).

Score bands for UI: ≥ 85 excellent · 70–84 good · 50–69 maybe · < 50 weak.

## 6. Failure handling

- Network/5xx → retry with backoff (2 s, 8 s) inside the call; then mark attempt failed.
- 429 / quota errors are **not** job failures: `QuotaTracker` updates the model status
  (data-model `llm_model_state`), the attempt counter is not incremented, the job stays
  PENDING, and `LlmRouter` applies the fallback/pause rule (overview "LLM work on owner action").
  A short `retry-after` (≤ 10 s) is waited once inside the call.
- 401/403/404 from the provider → model status `ERROR`; same as above (no attempt used).
- Invalid JSON or schema → one corrective retry; then FAILED for this attempt.
- Max 3 attempts per (job, purpose, prompt version) across runs, then permanently FAILED
  (visible on admin page with "retry" button).
- Budget guard reached → refuse the manual call with `BUDGET_REACHED`; not a failure.

## 7. Eval targets (Phase 1 acceptance)

Measured by `EvalRunner` on `docs/ai/eval/labeled-jobs.jsonl` (≥ 40 jobs):

| Metric | Target |
|---|---|
| Eligibility: labeled INELIGIBLE predicted ELIGIBLE (false-eligible rate) | ≤ 5% |
| Eligibility: labeled ELIGIBLE predicted INELIGIBLE (false-ineligible rate) | ≤ 10% |
| Relevance: labeled HIGH with score ≥ 70 | ≥ 80% |
| Relevance: labeled LOW with score < 50 | ≥ 80% |
| Extraction schema-valid on first try | ≥ 95% |

## 8. Cost control

- Collection costs no LLM calls. Each job costs one extraction call when the owner presses
  Analyze, plus one scoring call when the owner presses Score (ADR-0010). Spend is therefore
  the number of jobs the owner chooses to look at. Keep `max-calls-per-run` and monthly budget as guards.
- Truncate descriptions as above; send the profile excerpt, not the full YAML.
- Use the provider's prompt caching for the fixed system prompt + profile block if the SDK
  supports it (check current Anthropic docs in T-010; optional optimization).
- Default models are free-tier (ADR-0008, `config/llm.yaml`). The monthly USD budget only
  applies to PAID models; free models are limited by provider quotas and our alerts.
- Prompts must work on smaller open models too: keep instructions explicit, ask for one JSON
  object, and rely on the Java schema validation + corrective retry. If a model often fails
  validation, the eval shows it before it becomes the default.
- Scores from different models are not directly comparable. The UI shows the scoring model
  in the breakdown footer; switching the SCORE model does not re-score existing jobs unless
  the owner uses "Re-score active jobs".
