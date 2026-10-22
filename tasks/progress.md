# Progress log

Newest entry on top. Each entry, max ~10 lines:

```
## YYYY-MM-DD — T-XXX <title> — DONE | PARTIAL | BLOCKED
- What was built (key classes/files)
- Deviations from spec and why (and which doc was updated)
- Open issues / follow-ups (create tasks if needed)
- Next suggested task
```

## 2026-10-22 — T-028 Frontend: Profile page — DONE (visual check by owner pending)
- `/profile`: `ProfilePage` (header card with id, saved time, short hash, dirty tag, Discard, Save changes;
  problems banner; info box; 409 `PROFILE_CHANGED` → ErrorState with Reload; leave guard dialog via
  `useBlocker` + `useBeforeUnload`), `ProfileForm` (Candidate, Skills and domains, Preferences, Prefilter,
  Notifications; YAML path under each label), `ChipsInput`, `ToggleChips`, `Field`, `useProfileForm` (draft,
  dirty, client checks, server `errors[]` mapped to fields). Header: "Profile" nav link; profile pill links to it.
- `api`: `useProfile`, `useSaveProfile` (invalidates profile, jobs, job, meta); `ApiError.errors`.
- App switched to a data router (`createBrowserRouter`, one catch-all route around `AppShell`) because
  `useBlocker` needs one; frontend.md said "data router not required" and was updated.
- Backend fix: `PUT /api/profile` 400/409 are declared as problem+json (T-027 had declared them with the
  success body); openapi.json + schema.d.ts regenerated.
- Tests: `ChipsInput` (4), `ProfileForm` (5 + 3 `clientProblems`), `ProfilePage` (6: load, save with baseHash +
  toast + new hash, 400 errors at fields and banner, 409 + Reload, leave guard Stay/Leave, no guard when clean).
  Frontend 226 tests green, lint/typecheck/build clean. Playwright `e2e/profile.spec.ts` (flow 5) written, not
  run; it rewrites config/profile.yaml of the stack it runs against and restores the value.
- Next: T-015 (Telegram), then T-022/T-023.

## 2026-10-22 — T-027 Profile editing — backend — DONE
- profile: `ProfileProvider.snapshot()` (`ProfileSnapshot`: profile, hash, savedAt; `current()`/`profileHash()` are
  defaults); `YamlProfileProvider.reload()` swaps a volatile snapshot; `ProfileParser` (YAML or JSON tree → Profile,
  problems by path; shared by startup and the API); `ProfileYamlWriter` (§1 key order, no nulls, empty optional
  lists left out, defaults written, `|` blocks, header comment); `ProfileService.parse/save` (stale hash or hand
  edit → `PROFILE_CHANGED` 409, `PROFILE_ID_IMMUTABLE`, `VALIDATION_FAILED` with every problem, write check, temp
  file + atomic move, `.bak`, reload, `ProfileChanged` event with the listener's counts).
- enrich: `reassessPrefilter` returns `Reassessment(jobs, prefilterChanged, checksUpdated)`;
  `ProfileChangeListener` (synchronous, no LLM). pipeline uses `.jobs()`.
- api: `GET/PUT /api/profile` (`ProfileController`, `ProfileDto` mirrors the YAML keys; problem extension
  `errors: [{path, message}]`); openapi.json + schema.d.ts regenerated.
- Deviations (docs updated: api-spec, config-spec, runbook): `tracks` is not in the API and is written back
  verbatim (track-specific keys like `subjects` would otherwise be lost); a hand edit after startup makes saves
  return `PROFILE_CHANGED` until a restart (ADR-0012 said "until the page reloads"; GET does not reload the file,
  so a page reload alone does not help — reloading on GET would apply hand edits as a side effect of a read).
- Tests: `ProfileServiceTest` (9), `ProfileYamlWriterTest` (6, incl. YAML look-alikes `NO`/`no`/`on`, regexes,
  multi-line), `ProfileControllerTest` (7, standalone MockMvc over a temp file), `ProfileChangedIT` (prefilter and
  checks re-evaluated, score and decision kept, 0 LLM calls), `AssessmentServiceIT` counts.
- Next: T-028 (frontend Profile page).

## 2026-10-19 — T-021 Frontend: admin page — DONE (visual check by owner pending)
- `api/queries.ts`: `useAdminOverview` (3 s while a run or bulk action is in progress, else 60 s),
  `useRunPipeline`, `useRescore` and `useBulkAction(mode)` (each `{ dryRun, execute }`), `useBulkCandidates`
  (GET /api/jobs, 50 per page), `useRetry`, `useCompanies`, `useEvalExport`.
- `features/admin`: `AdminPage` (title row with schedule line, Export / Re-score / Run pipeline),
  `PipelineBanner`, `KpiRow`, `SourcesTable`, `LlmUsageCard` (CSS meter + 14 bars), `RunsList`,
  `FailuresList` (Retry), `CompaniesTable`, `RescoreDialog`, `BulkActionTable` + `BulkActionDialog`
  (Analyze | Score, max 200, dry run → confirm), `CostSummary` (model, tier, calls, cost, PAID note; confirm
  disabled when paid calls are off), `AdminTag` (icon + word per status). New token `--accent-bar`.
- The header status line already read `GET /api/status` (T-020); runs now refresh it. LLM models card and
  banner stay for T-023.
- Deviations (frontend.md updated): usage bars show calls while no day cost money; bulk table pages of 50
  with the selection kept across pages; export answers 501 until T-016, shown as a toast.
- Tests: `AdminPage.test.tsx` (9: overview, run → polling → done, 409, rescore confirm, PAID callout and
  paid-off block, retry, bulk 200 cap + confirm, Score mode, export 501), `LlmUsageCard.test.tsx` (3);
  `App.test` /admin route. Frontend 208 tests green, lint/typecheck/build clean. Playwright
  `e2e/admin.spec.ts` (flow 3) written, not run (needs the running stack).
- Next: T-022 (LLM quota alerts and model switching — backend).

## 2026-10-19 — T-026 Frontend: board grid, split view and eligibility decision — DONE (visual check by owner pending)
- `api/queries.ts`: `PAGE_SIZE` 12; `useJobs(query, page)` (one grid page, keeps the previous page while
  loading), `useInfiniteJobs(query, seedPages)` (list panel, tops up a shorter cached list to pages 1…page),
  `useSetDecision()` (optimistic, rollback; no removal, lists only marked stale), `cachedPosition`;
  `patchLists` handles grid pages and the infinite list; grid pages refetch after a status change (refill),
  the list panel never refetches on its own. Default view check is now status ≠ DISMISSED.
- Board: `BoardGrid` (`/`, desktop grid/list + mobile list, `Pagination`, `StatusLegend`, `LayoutToggle` in
  `localStorage` `board.layout`, keys act on the focused card), `JobCard`, `JobListCard`, `useCardActions`
  (fade-out then leave), `useBoardFilters` gains 1-based `page`. Density toggle, `JobRow`, `JobList`,
  `JobDetailPane` removed. Split view: `JobSplitView`, `JobListPanel` (own scroll, Load more appends, selected
  card scrolled into view once), `JobDetailPanel` (own scroll, sticky header, reset on job change).
- Job: `JobDetail` in the new order with "Not analyzed yet" card, `EligibilityPanel` (rows not OK on
  `--attention-row`, decision row + hint), `EligibilityDecision`, `useDecisionChanger` (toast + Undo);
  `AnalysisActions` only in the detail action row / mobile bar (shared busy state via mutation key).
- Tags: `StatusTag`, `AnalysisTag` (new), `DecisionTag`, `ChecksTag` with token colors + icons; new tokens in
  `tokens.css` and the Tailwind theme (+ radii job/panel, card shadows). Keys `e`, `n`, `Esc` added.
- Deviations (frontend.md updated): card title is a stretched link, not a `<button>`; a dismissed grid card
  fades (no height collapse); mobile `/` uses the same numbered pages; `o` on the grid needs the job's
  detail cached; the URL `page` follows the selected job in the split view.
- Tests: `JobCard`, `Pagination` (+ `pageItems`), `JobListPanel` (append keeps `scrollTop`, no remount),
  `EligibilityDecision`, tag colors/icons, `BoardPage` (grid 10 + split view 9), `JobDetailPage` (9),
  `useBoardFilters` incl. page; Vitest `testTimeout` 15 s (page tests run 5–7 s in parallel here).
  Frontend 196 tests green, lint/typecheck/build clean. Playwright `e2e/board.spec.ts` flows 1, 2, 4
  written, not run (need a running stack with jobs).
- Open: owner to compare with the mockups at 1440 and 390 px; bundle 522 kB (lazy-load admin later).
- Next: T-021 (admin page).

## 2026-10-19 — T-025 Eligibility checks and owner decision — backend — DONE
- V8: `job_assessment.eligibility_checks jsonb` replaces `eligibility`/`eligibility_reasons`;
  `job_tracking.eligibility_decision` + `eligibility_decided_at`; `job_tracking_event.kind` with a
  kind-dependent check; index `(profile_id, eligibility_decision)`.
- enrich: `EligibilityEvaluator` returns `List<EligibilityCheck>` (code, critical, result, reason,
  value) incl. `EXTRACTION_CONFIDENCE`; no verdict (`Eligibility`, `EligibilityResult` removed);
  `CheckResult`, `CheckCounts`; `AssessmentService.recordChecks`, checks re-evaluated on profile
  change and attached from a stored OK extraction; `EligibilityChecksStartup` prefilters ACTIVE jobs
  and fills missing checks on startup (no LLM).
- tracking: `EligibilityDecision`, `TrackingService.setEligibilityDecision/decision/decisions`,
  history with `kind`; `JobBoardQuery` filters `decision` and `checks`, new default view (status ≠
  DISMISSED, decision ≠ INELIGIBLE), `checks` counts. scoring: refused only when marked INELIGIBLE
  (`JOB_MARKED_INELIGIBLE`), bulk skip `markedIneligible`, prompt `score-v2` (now the default).
- api: `PUT /api/jobs/{id}/eligibility`, `JobSummary.eligibilityDecision/checks`,
  `JobDetail.eligibilityChecks/eligibilityDecidedAt`, history `kind`, meta `decisions`/`checkResults`;
  openapi.json + schema.d.ts regenerated. Frontend kept working with minimal changes: `DecisionTag` +
  `ChecksTag` replace `EligibilityTag`, Eligibility panel lists the checks, filters Decision/Checks;
  the decision control and board grid are T-026.
- Note: switching to score-v2 hides score-v1 assessments (rows are per score prompt version); scores
  made with score-v1 must be redone with "Re-score" or Score. Eval for score-v2 pending (T-016),
  recorded in `docs/ai/eval/results.md`.
- Tests: `EligibilityEvaluatorTest` (54), `AssessmentServiceIT` (8), `TrackingServiceIT` decision
  cases, `JobBoardQueryIT` decision/checks/default view, `JobDetailQueryIT`, `ScoringServiceIT`
  (marked ineligible, failing check still scorable, prompt lists checks), controller tests.
  Backend 767 unit + 182 IT green; frontend 153 tests green.
- Next: T-026 (board grid, split view, decision UI).

## 2026-10-19 — Board grid, owner eligibility decision, Profile page (docs) — DONE
- Mockups updated in the canvas: board as a 12-card grid with pagination and a Grid/List toggle;
  split view with independent list and detail scroll (Load more keeps the position); colored
  status/analysis/decision tags; bordered cards with hover and selected states; Profile page.
- ADR-0011: the app checks eligibility per criterion, the owner decides (Eligible / Not eligible /
  Undecided); Score allowed unless marked Not eligible; default view hides only those.
- ADR-0012: the owner edits their single profile in the app; profile.yaml stays the source of truth.
- Updated: PRD, overview, data-model, api-spec, frontend.md, config-spec, runbook, scoring-spec
  §1/§4/§5/§7, eval README, testing, ADR-0004/0010 notes; tasks T-015, T-016, T-021.
- T-011–T-014 and T-020 were already done, so their task files stay as shipped (owner decision):
  the rework is in new tasks T-025 (eligibility checks + decision, backend), T-026 (board grid +
  split view + decision UI); the profile work is T-027 (backend) and T-028 (frontend). T-024 was
  skipped because a docs commit already used that tag.
- score-v1 is released, so the new prompt input is score-v2 (draft until T-025 switches to it).
- openapi.json not changed (regenerated in T-025/T-027).

## 2026-10-19 — T-020 Frontend: board and job detail — DONE (visual check by owner pending)
- `api/queries.ts`: `useJobs` (infinite, 25/page), `useJob`, `useStatus` (60 s / 10 s with alerts),
  `useChangeStatus` (optimistic on list + detail caches, rollback; lists patched, not refetched),
  `useUpdateNotes`, `useAnalysisAction` (Analyze / Score, one request, updates both caches).
- `lib`: `statusMachine.ts` (+ next-step primary), `labels.ts` (enums, rule results from reason codes,
  prefilter reasons), `format.ts`. Components: `StatusActions`, `SourceLinks`.
- Board: `BoardPage` (desktop split view; mobile list / full-page detail), `FilterBar` (Radix popovers),
  `MobileFilters` (pills + bottom sheet), `SearchField` (300 ms), `JobList`/`JobRow` (collapse animation,
  density in localStorage), `JobDetailPane`, `useBoardFilters` (URL), `useBoardKeyboard` (j k Enter s d a o /).
- Job: `JobDetail` in the spec order (`WhyItFits`, `ScoreBreakdown`, `EligibilityPanel`, `FactsPanel`,
  `DescriptionPanel`, `NotesEditor` 1 s autosave, `HistoryList`), `AnalysisActions`, `MobileActionBar`,
  `JobDetailPage` ("Job N of M" from the cached list). Header status line now reads `GET /api/status`.
- Deviations (frontend.md updated): Undo only when the way back is a legal transition; score breakdown
  footer shows the total only (prompt version/model not in the API); "Switch model" links to `/admin#llm`
  until T-023; mobile count + search toggle live in the filter row.
- Tests: `statusMachine` (64 pairs), `useBoardFilters`, `StatusActions`, `BoardPage` (10: load, URL filters,
  menu filter, optimistic dismiss + Undo, rollback, star, keyboard, typing ignored, one Analyze request,
  deep link), `JobDetailPage` (8: panels, links, status, rollback, notes autosave with fake timers, Score);
  Playwright `e2e/board.spec.ts` flows 1–2 (not run: need a running stack with jobs).
- Open: owner to compare with the mockups at 1440 and 390 px; bundle is 505 kB (lazy-load admin later).
- Next: T-021 (admin page).

## 2026-10-18 — T-014 REST API for board, job detail and admin — DONE
- `tracking`: `JobBoardQuery` (one SQL over job + profile's assessment + extraction + tracking; every filter,
  default view, `score`/`posted` sorts, pages, `countDefaultView`), `JobDetailQuery`, `TrackingService`
  (`changeStatus` via `TrackingStatusMachine` + history event, `updateNotes`), `AnalysisStage`, `ScoreBand`.
- `api`: `JobController` (list, detail, status, analyze, score, notes), `MetaController` (+ `GET /api/status`),
  `AdminController` (overview, bulk analyze/score, rescore, retry, companies, eval export 501), `AdminViews`
  (read-only assembly), DTOs; `OpenApiConfig` rewrites nullable `$ref`s to `anyOf [$ref, null]` so the TS
  types keep `| null`. openapi.json and schema.d.ts regenerated.
- Support: `common.ProgressBoard` (pipeline publishes COLLECT/PREFILTER/EXPIRE, bulk actions ANALYZE/SCORE),
  `scoring.BulkActionRunner` (pre-checks then one background thread), `LlmRouter.availability` (pause/budget
  check without a call), `LlmUsageService`, `PipelineSchedule`, `SourceCatalog`, `CompanyService`, failure lists
  and retry resets in enrich, `LlmPausedException`/`BudgetReachedException`.
- Choices (api-spec, overview, config-spec updated): any board filter replaces the default view; `kpis.alertsToday`
  removed (no instant alerts, ADR-0010); bulk analyze dry run also names the EXTRACT model + cost and a real run is
  refused for a PAID model without consent (`PAID_CALLS_DISABLED`), like bulk score; `LLM_PAUSED` carries
  `purpose` (full `LlmAlert` in T-022); `status.lastRun.scored` = jobs scored since that run started; overview
  sources gained `lastSuccessAt`; status note ≤ 2 000 chars.
- Open: `JobAttributes` (reused as `extraction`) has optional, not nullable, fields in the TS types; T-020 should
  treat them as possibly null. `LlmAlert.purpose` enum lists all purposes until T-022.
- Tests: `JobBoardQueryIT` (15), `JobDetailQueryIT` (4), `TrackingServiceIT` (5), `LlmUsageServiceIT` (2),
  `JobControllerTest` (15), `AdminControllerTest` (14), `MetaControllerTest` (3), `BulkActionRunnerTest` (7),
  `ScoreBandTest`, `ProgressBoardTest`, `PipelineScheduleTest`; `LlmRouterIT` covers `availability`.
- Next: T-020 / T-021 (frontend) or T-015 (Telegram).

## 2026-10-18 — T-013 Pipeline orchestration, scheduling, expiry — DONE
- `pipeline`: `PipelineOrchestrator` (`runOnce`, `startAsync` on one thread, last 20 reports,
  `isRunning`), `PipelineLock` (`pg_try_advisory_lock` on a held connection), `PipelineScheduler`
  (cron + startup catch-up), `PipelineProperties`, `PipelineReport`/`SourceReport`, `Trigger`,
  `PipelineAlreadyRunningException`; `POST /api/admin/pipeline/run` (202 / 409), openapi.json
  and schema.d.ts regenerated.
- Steps: collect + ingest per source (isolated failures, timeouts), `reassessPrefilter`,
  `AssessmentService.prefilterJobs` (writes only new/changed rows), `JobExpiryService`,
  NOT_ANALYZED count, `RunCompleted` (in `common`, notify may not depend on pipeline). 0 LLM calls:
  ArchUnit rule `pipelineNeverCallsTheLlm` (+ fixture) and the report's `llmCalls`.
- Expired jobs: single Analyze/Score now refuse them (`JOB_EXPIRED`; bulk already skipped them).
- Tests no longer run the schedule or catch-up (`src/test/resources/config/application.yml`):
  otherwise every full-context test would call the real job sources.
- Tests: `PipelineOrchestratorIT` (6: first run 0 LLM calls, idempotent second run, failing and
  hanging source, profile change, expiry with fixed clock), `PipelineLockIT` (2), controller
  and ArchUnit tests.
- Next: T-014 (REST API).

## 2026-10-18 — T-012 Scoring — DONE
- `scoring`: `ScoreResult` (§5 validation, text cut, `withSumAsScore`), `ScoringService`
  (`score(jobId, profile)` owner action, cached per job/profile/prompt version, max 3 attempts;
  `computeScore(...)` pure for the eval; `scoreSelected` bulk max 200 with dry run naming the
  model and cost; `rescoreActive` dry run + confirmed run), result records.
- `enrich`: `AssessmentView`, `AssessmentService.find/findAll/scoredJobIds/recordScore/
  recordScoreFailure`. `llm`: `ModelChoice` + `LlmRouter.resolve(purpose)`,
  `BudgetGuard.estimateCostUsd`, `PaidCallsDisabledException` (409 `PAID_CALLS_DISABLED`).
- Choices (docs updated: overview, api-spec, data-model, scoring-spec, prompts README):
  `scoring` may read `ingest` (`JobView`, as the task's `computeScore` signature needs; ArchUnit
  updated); `score_breakdown` stores the whole §5 object; eligibility is re-checked before each
  SCORE call; bulk score and re-score are refused up front for a PAID model without consent.
  score-v1 is now released.
- Tests: `ScoreResultValidationTest` (12), `ScoringPromptRenderingTest` (4), `ScoringServiceIT`
  (13), `ScoringPaidConsentIT` (3). No real LLM call.
- Next: T-013 (pipeline) or T-014 (REST API).

## 2026-10-18 — T-011 Extraction + eligibility — DONE
- `enrich`: `JobAttributes` (§3, Bean Validation; lenient: missing lists → empty, long text cut),
  `ExtractionService` (`extractAttributes(JobView)` pure for the eval; `extract(jobId)` cached per
  prompt version, max 3 failed attempts, UNAVAILABLE uses no attempt; description truncation
  9 000 + "[…]" + 3 000; `JobDescriptionChanged` listener clears extractions, eligibility, score),
  `EligibilityEvaluator` (all §4 rules, `FULL_TIME_ONLY_SOFT`, DST-aware timezone, `CONFIDENCE:LOW`
  cap), `AnalysisService` (`analyze`, `analyzeMany` max 200 with dry run, stops at pause/budget),
  `AssessmentService.ensurePrefilter/recordEligibility`; `ingest.JobNotFoundException`.
- Choices (docs updated: api-spec, config-spec, data-model, scoring-spec, prompts README):
  analyze refuses prefilter-rejected jobs (`JOB_PREFILTER_REJECTED`, no call); each owner action
  is one budget "run" (`max-calls-per-run` now caps one action, the pipeline makes no calls);
  WORK_AUTH matches US/UK/EU only in upper case. extract-v1 is now released.
- Tests: `EligibilityEvaluatorTest` (47 rows + 2), `JobAttributesValidationTest` (12),
  `ExtractionServiceIT` (9), `AnalysisServiceIT` (10). No real LLM call.
- Next: T-012 (scoring).

## 2026-10-18 — Resolve contradictions with ADR-0010 — DONE
- `runNow` / "Process pending jobs now" and `pendingJobs` removed (api-spec, frontend.md,
  runbook, T-022, T-023): switching a model only un-pauses Analyze/Score; nothing runs by itself.
- `Profile.Notify.alertMinScore` removed; the old key is ignored (not rejected) so existing
  profile files still load (`@JsonIgnoreProperties`, test added, config-spec noted).
- `NotificationKind.ALERT` removed; V7 narrows `notification.kind` to `DIGEST` (V4 untouched);
  data-model updated; repository IT proves `ALERT` is rejected.
- `make verify` green: 634 unit + 105 IT, frontend 6 files.

## 2026-10-18 — T-010 follow-up: real provider check, Cerebras removed, LF line endings — DONE
- `.gitattributes`: `* text=auto eol=lf` (owner approved the T-009 proposal); frontend files
  re-checked out with LF → Prettier passes. Other folders turn LF on their next checkout.
- Cerebras removed (no free tier without a credit card): registry, `.env.example`, docs.
  Replacement cross-provider fallback for EXTRACT only: `openrouter-nemotron-super`
  (`nvidia/nemotron-3-super-120b-a12b:free`); not for SCORE, whose prompt holds CV data.
- Real calls with the owner's Groq/OpenRouter keys (`LlmProvidersLiveTest`, tag `live`, FREE
  models only, paid calls forced off): Groq `llama-3.3-70b-versatile` → 404 (no longer offered;
  removed, default is now `groq-gpt-oss-120b`; added `groq-gpt-oss-20b`, `groq-qwen3-27b`);
  OpenRouter nemotron returned no content because reasoning used up `max_tokens` → new
  registry field `minOutputTokens` (4096 for reasoning models) and a clearer error.
  Final run: 4/4 OK (gpt-oss-120b 1027/840 tokens 2.6 s, gpt-oss-20b 1027/1491 1.8 s,
  qwen3 996/373 0.9 s, nemotron 1028/1751 37 s). OpenRouter spend $0 (3/50 free requests).
- Open (for T-013): Groq free TPM is 8K and one extraction uses ~2-2.5K tokens, so a run will
  hit per-minute 429s; the router waits once (≤ 10 s) then pauses the purpose. T-013 should
  resume a purpose whose model is RATE_LIMITED for a short time instead of ending the run.
- Next: T-011.

## 2026-10-18 — T-010 LLM clients, model registry, router, call log, budget guard — DONE
- `llm`: `ModelRegistry` (+ failure analyzer), `PromptRepository`/`PromptTemplate`, `JsonExtractor`,
  clients (`AnthropicLlmClient` on SDK `com.anthropic:anthropic-java` 2.73.0,
  `OpenAiCompatibleLlmClient`, `FakeLlmClient`), `LlmRouter` → `LlmResult | LlmUnavailable |
  LlmFailed`, `QuotaTracker`, `CallPacer` (rpm), `BudgetGuard`, `LlmCallLogger`,
  `AssignmentService`; V6 (llm_call columns, llm_assignment(_event), llm_model_state).
- SDK findings: `withRawResponse()` gives headers; `maxRetries(0)` (router retries);
  `baseUrl` override → WireMock tests; Haiku 5.5 (`claude-haiku-5-5`, $0.10/$0.50 per MTok)
  rejects non-default temperature and thinks by default → client sends no temperature,
  `effort: low`, `max_tokens = max(4096, 4 × prompt budget)`. Prompt caching not used yet.
- Provider docs (comments in `config/llm.yaml`): GitHub Models retired 2026-07-30 → removed;
  Cerebras no longer has llama-3.3-70b → `gpt-oss-120b` (5 rpm, 1M tokens/day); added
  `groq-gpt-oss-120b`; Groq 429 body names RPD/TPD; OpenRouter free = `:free`, 20 rpm, 50/day.
- **Owner request: paid calls need consent → ADR-0009**: `app.llm.allow-paid-calls` (default
  false) blocks every PAID request (`PAID_NOT_ALLOWED`); only `LlmRouter` may call `LlmClient`
  (ArchUnit); anthropic models must be PAID; AGENTS.md hard rule for agents; T-012/T-016/
  T-021–T-023 now require cost confirmation.
- Deviations: `LlmClient.send` is one raw HTTP request, JSON parsing/retries/logging live in
  the router (overview.md updated); `RemoteHint`-style move of `Sleeper` to `common`;
  fake answers keyed by prompt (`llm-fakes/score/…`), not purpose; auto-fallback switches the
  assignment as T-022 planned (done here); `AssessmentService` now reads `LlmProperties`.
- Tests: 12 new test classes (registry, prompts, JSON, clients with WireMock, quota table,
  pacer, router/budget/consent/force-model ITs); backend 633 unit + 104 IT green.
- No live LLM call was made in this step (see the follow-up entry above). Next: T-011.

## 2026-10-17 — T-009 Prefilter rules — DONE
- `enrich`: `Prefilter` (all §2 rules, all reasons collected in rule order, injected `Clock`),
  `PrefilterDecision`/`PrefilterReason`, `SkillMatcher` (word-boundary, flexible word
  separators, slash-name parts, `ALIASES` table), `CityNames` (English/local names, accent-
  insensitive), `AssessmentService.recordPrefilter(...)` and `reassessPrefilter(profile, hash)`.
- `ingest`: `JobView` + `JobQueryService` (read access for other modules).
- `RemoteHint` moved `source` → `common.model` (enrich may not depend on source; same reason
  as the T-002 enums). `app.llm.score-prompt: score-v1` added to application.yml (rows are
  keyed by it, as the task says; T-010 may move it into `LlmProperties`).
- Choices (scoring-spec §2 "Details", data-model `job_assessment` updated): no `posted_at` →
  never TOO_OLD; REJECT → eligibility null, status DONE; PASS after REJECT → PENDING;
  hash change clears eligibility (re-evaluated, no LLM) and keeps scores.
- Tests: `PrefilterTest` (28 rows + 3), `SkillMatcherTest` (35 rows), `CityNamesTest`,
  `AssessmentServiceIT` (5 cases incl. reassess).
- Next: T-010 (LLM client), then T-011.

## 2026-10-17 — T-019 Frontend bootstrap (React) — DONE
- Versions: Vite 8.3, React 19.3, React Router 8.4 (declarative routes), TanStack Query 5.104,
  openapi-fetch 0.17 / openapi-typescript 7.13, Tailwind CSS 4.3 (`@tailwindcss/vite`,
  theme in CSS), shadcn CLI 4.22 (style `radix-nova`, Radix), lucide-react 1.59, sonner 2.0,
  @fontsource IBM Plex Sans/Mono 5.3, Vitest 5.0 + jsdom 30, RTL 16, MSW 3.0, Playwright 1.64,
  ESLint 10 + typescript-eslint 8.71, Prettier 3.9. **TypeScript 5.9** (not 7.0/6.0):
  typescript-eslint supports < 6.1 and openapi-typescript requires 5.x.
- Built: tokens.css (+ `--attention-icon`, `--icon-faint`, `--on-accent`), Tailwind theme
  mapped to tokens only, shadcn components restyled (shadcn's `--accent`/`--muted` names clash
  with ours → our tokens win; `bg-muted` uses replaced), button variants with shortcut hint,
  Sonner per Toast spec, shared components, AppHeader (desktop + mobile sheet; status line "—"
  until T-014/T-022), routes + 404, dev-only `/dev/components` (excluded from builds).
- API client: absolute same-origin base URL and per-call `fetch` (Node rejects relative URLs;
  MSW must intercept), `ApiError` from Problem Details / network failures.
- Makefile: frontend wired into verify/dev/run/api/e2e. Checked: `make verify`, `make run`
  (SPA fallback on reload), Vite proxy on :5173, Playwright 2 specs, no hex in .tsx.
- Owner: check `/dev/components` against the mockup (`make dev`, open
  http://localhost:5173/dev/components). npm upgraded to 12.2 (was 8.14; packages want ≥ 9.5).
- Next: T-009 (prefilter) on the backend; T-020 needs T-014.

## 2026-10-17 — T-008 We Work Remotely collector (RSS) — DONE
- `source.weworkremotely`: `WeWorkRemotelyCollector` (configured feeds, dedupe by guid,
  per-feed failure = warning), `WeWorkRemotelyFeedParser` (hardened JDOM + Rome; WWR's own
  elements read from JDOM; item → JSON rawPayload), `WeWorkRemotelyMapper` ("Company: Title"
  split, "Unknown" + warning), `WeWorkRemotelyItemDto`, `WeWorkRemotelyProperties`.
- Findings (weworkremotely.md rewritten): description is entity-escaped HTML, not CDATA;
  extra elements `country` (flag emoji), `state`, `skills`, `expires_at`, `media:content`
  → country/state/skills appended to the description; spec feed URLs still valid.
- Terms: RSS page, terms and sitemap are behind a Cloudflare JS challenge (not bypassed);
  the owner read the ToS in a browser: no scraping/feed clause; limited, revocable,
  non-transferable personal-use right → fine for Phase 1, no redistribution (a Phase 4 SaaS
  needs WWR's permission). Catalog and weworkremotely.md updated.
- Tests: `WeWorkRemotelyMapperTest` (7 fixture items, extras, rawPayload, title split, XXE
  rejected), `WeWorkRemotelyCollectorTest` (2 feeds with shared guids → 8 unique of 10,
  "Unknown" company, since, max-items, failing / challenge / all-failing feeds); live passed.
- Next: T-009 (prefilter).

## 2026-10-17 — T-007 Jobicy collector — DONE
- `source.jobicy`: `JobicyCollector` (one request per configured query, cursor while
  `hasMore`, dedupe by id across queries, per-query failure = warning), `JobicyMapper`
  (`parsePubDate`: ISO with offset, zone-less → UTC), DTOs, `JobicyProperties`; live test passed.
- Spec corrections (jobicy.md rewritten): industry slug `dev` does not exist → `engineering`;
  `pubDate` is ISO-8601 with offset; salary keys `salaryMin/Max/Currency/Period` and absent
  when unknown; cursor pagination, count ≤ 200; 7-day window with 3 h delay, newest first
  (so a query stops at its first job older than `since`).
- Terms (fair use): allowed without approval; keep the canonical Jobicy URL and credit.
- Tests: `JobicyMapperTest` (7 fixture items, 6 date formats, errors), `JobicyCollectorTest`
  (overlapping queries → 8 unique of 10, since, max-items, cursor, max-pages, partial and
  total failure, malformed items).
- Next: T-008 (We Work Remotely) or T-009 (prefilter).

## 2026-10-17 — T-006 Himalayas collector — DONE
- `source.himalayas`: `HimalayasCollector` (cursor pagination, optional `HIMALAYAS_API_KEY`
  bearer header), `HimalayasMapper`, `HimalayasJobDto`, `HimalayasPageDto`,
  `HimalayasProperties`; real fixtures captured 2026-10-17; live test passed once.
- Spec corrections (himalayas.md rewritten): browse is **cursor**-paginated (offset
  deprecated), ≤ 20 per request, feed ordered by last update; `guid` is the job URL;
  `salaryPeriod` field; worldwide jobs list all 37 offsets; ~160 new jobs/hour.
- Terms: §30A allows API use; obligations recorded (apply via `applicationLink`, source
  credit, no redistribution). The ATS-over-board merge rule stays (an ATS link only wins when
  we collected the job from the ATS ourselves).
- Owner decisions: walk the newest-first feed (every 2h, ≤ 400 jobs, 2 s/page, ≤ 25 pages)
  instead of keyword search; optional API key via env (`.env.example`, config-spec, owner
  task added).
- Tests: `HimalayasMapperTest` (11 fixture items + edge cases), `HimalayasCollectorTest`
  (WireMock: cursors, since, max-items/pages, empty page, API key header, 500, 429, malformed,
  duplicate guids).
- Next: T-007 (Jobicy), T-008 (WWR) or T-009 (prefilter).

## 2026-10-17 — Free-tier LLMs, quota alerts, model switching (docs) — DONE
- ADR-0008: model registry `config/llm.yaml` (Groq free by default; Cerebras, GitHub
  Models, OpenRouter, Ollama, Claude Haiku as options), OpenAI-compatible client,
  per-purpose assignments switchable at runtime, quota tracking, Telegram + banner alerts.
- Updated: PRD (F2.8, F2.9, G6, G7), config-spec §2/§3/§5, data-model (`llm_call` columns,
  `llm_assignment`, `llm_assignment_event`, `llm_model_state`, `eval_run`, `notification`),
  overview, api-spec, frontend.md, scoring-spec §6/§8, testing, runbook, eval README,
  AGENTS.md, ADR-0004, `.env.example`. Tasks: T-010 rewritten, T-013/T-014/T-016/T-021
  adjusted, new T-022 (backend) and T-023 (frontend).
- Banner, switch dialog and LLM models card are specified in frontend.md but not drawn in
  the mockups yet.

## 2026-10-17 — T-005 Ingest: normalize, fingerprint, dedupe, upsert — DONE
- `common.text.TextNormalizer`, `common.Hashing` (profile reuses it), `ingest.Fingerprint`,
  `JobMerger`, `IngestService` (batches of 50, `JobDescriptionChanged` event), `IngestResult`.
- `POST /api/admin/collect/{sourceCode}` in `AdminController` (runs now, ignores
  min-interval; TODO T-013 move to pipeline); `UnknownSourceException` (404
  `SOURCE_NOT_FOUND`); `CollectorRunner.runNow/defaultRequest`; `openapi.json` regenerated.
- Refinements (data-model §4 rewritten with examples): extended legal suffixes removed from
  the end repeatedly; generic gender tags; re-posting looks up the newest job of the
  fingerprint family; merge fills missing fields and unites tags; EXPIRED → ACTIVE on sighting.
- Real run against Arbeitnow: 2643 fetched, 200 postings → 194 raw, 184 jobs. An immediate
  second run hit Cloudflare 429 on page 4 → PARTIAL as designed; warnings now carry only
  "HTTP 429 …" (`HttpErrors`), Arbeitnow `page-delay` raised to 2s.
- Tests: TextNormalizer (45 rows), Fingerprint (20 same/different pairs), JobMerger,
  IngestServiceIT (13 cases), AdminControllerTest.
- Next: T-006 / T-007 / T-008 (collectors) or T-009 (prefilter, needs T-002 + T-005).

## 2026-10-17 — T-004 Collector framework + Arbeitnow collector — DONE
- `source` API: `JobCollector`, `CollectRequest`, `CollectResult` (+ `fetched`; overview.md
  updated), `NormalizedPosting`; `CollectorRegistry`, `CollectorRunner` (virtual-thread
  timeout, never throws, writes `collector_run`, min-interval skip, `recordIngestCounts`),
  `HttpClients` (+ `app.http.*`, config-spec updated), `Politeness`/`Sleeper`.
- `ArbeitnowCollector`/`Mapper`/`JobDto`/`PageDto`; real fixtures captured 2026-10-17.
- Spec corrections (arbeitnow.md): 300 items/page; items not strictly ordered by `created_at`
  → old items are skipped and paging stops only when a whole page is older than `since`;
  end of data = empty page; page number incremented locally; added `max-pages` (20) cap.
- `HtmlText` now decodes double-escaped HTML (seen in Arbeitnow descriptions).
- Owner decision: `remote: false` + location containing "remote" counts as remote
  (`ArbeitnowMapper.isRemote`, used by mapper and `remote-only`); arbeitnow.md updated.
- Tests: mapper (fixture table), collector (WireMock: paging, since, max-items/pages, 500,
  timeout, non-JSON, later-page error, malformed → PARTIAL), runner, registry, runner IT;
  live test passed once.
- Next: T-005 (ingest).

## 2026-10-17 — UI design approved — DONE
- Mockups approved in the Claude Design canvas "Job Hunter UI mockups" (5 artboards).
- `frontend.md` now holds design tokens, typography, component specs, layouts and
  behavior ("inbox triage", split view ≥ 1024px). Added `o` and `Esc` shortcuts.
- `api-spec.md`: overview gained `kpis`, `schedule`, `pipelineProgress`; re-score dry run
  gained `estimatedCostUsd`. T-014, T-019, T-020, T-021 updated accordingly.

## 2026-10-17 — T-003 Database schema and persistence layer — DONE
- Flyway V2–V5 (all Phase 1 tables, §5 indexes + a few FK/lookup indexes, check constraints
  on every enum column). Entities/repositories in `<module>.persistence`: source (CollectorRun,
  Company), ingest (Job, RawPosting), enrich (JobExtraction, JobAssessment), tracking
  (JobTracking with `JobTrackingId`, JobTrackingEvent), notify (Notification), llm (LlmCall).
  Cross-module references are plain `Long` ids. Module enums in each module's root package.
- `TrackingStatusMachine` + `IllegalStatusTransitionException` (409); test covers all 64 pairs.
- New ArchUnit rule: `<module>.persistence` is private to its module (with a violating fixture).
- Schema deviations (data-model.md updated): `notification.profile_id` added (needed for
  "alerted once per profile"); `job_assessment` unique is `nulls not distinct`;
  `collector_run.status` null while running; `llm_call.job_id` FK on delete set null.
- Tests: 9 `*RepositoryIT` (round trip incl. jsonb/arrays, unique, check, FK) on Testcontainers.
- Next: T-004 (or T-010 / T-019 in parallel).

## 2026-10-16 — T-002 Profile loading and validation — DONE
- `profile`: `Profile` (nested records, defaults in compact constructors), `ProfileProvider`,
  `YamlProfileProvider` (Jackson YAML + Bean Validation, all problems listed by field path,
  unknown keys rejected), `RegionMatcher`/`RegionVerdict`, `CandidateBlock`,
  `InvalidProfileFailureAnalyzer` (short "APPLICATION FAILED TO START" report).
- Shared enums in `common.model` (`EmploymentType`, `Seniority`, `SalaryPeriod`, `LanguageLevel`)
  because `source` may not depend on `profile`; overview.md updated.
- Deviations: record component is `notifySettings` (`notify` is illegal), YAML key unchanged;
  `app.profile.path` bound as String (Spring's String→Path rejects `../`); `CandidateBlock`
  also redacts e-mails/phone numbers in free text; `/api/meta` now returns `profileId` and
  `summaryLanguage` (openapi.json regenerated); config-spec/runbook notes added.
- Region membership known for EU/EEA/EUROPE/EMEA only; other tokens match via acceptRegions.
- Owner: copy `config/profile.example.yaml` to `config/profile.yaml`, or the app will not start.
- Next: T-003.

## 2026-10-16 — T-001 Backend bootstrap — DONE
- `backend/`: Spring Boot 3.5.16 (latest 3.x), Java 21, Maven wrapper 3.9.16; springdoc 2.9.1,
  wiremock-jetty12 3.13.2, archunit 1.5.1, jsoup 1.23.2, rome 2.1.0; profiles `live`, `with-frontend`.
- `common`: `ClockConfig`, `JobHunterException` (+ overridable `httpStatus()`, default 400),
  `common.text.HtmlText`. `api`: `GlobalExceptionHandler`, `MetaController` (stub),
  `SpaFallbackController` (regex segments, depth ≤ 4), `OpenApiConfig` (fixed server `/`).
- Root `docker-compose.yml`, `Makefile`, `.gitattributes`; `V1__baseline.sql`; `openapi.json`.
- Tests: 49 unit + 7 IT green; `ArchitectureTest` encodes the overview.md module table and
  proves itself against a violating fixture (`io.github.quanghung304.archfixture`).
- Deviation: api-spec.md now documents error codes for framework/unexpected errors.
- Note: `app.*` has no keys yet (none are read); each task adds its own.
- Next: T-002 / T-003 (T-019 can start in parallel).

## 2026-10-16 — Switch UI to React SPA — DONE
- ADR-0007 supersedes ADR-0006 (UI part): monorepo `backend/` + `frontend/`, REST API under
  `/api`, OpenAPI contract at `docs/architecture/openapi.json`, React + Vite + TanStack Query.
- Added `api-spec.md`, `frontend.md`; updated AGENTS, overview, conventions, testing, runbook.
- Old T-014 (Thymeleaf UI) replaced by T-014 (REST API), T-019 (FE bootstrap),
  T-020 (board + detail), T-021 (admin).
- Next: T-001.

## 2026-10-16 — Documentation set — DONE
- PRD, roadmap, architecture, data model, config spec, ADR-0001..0006, source specs,
  scoring spec, prompts v1 (draft), eval plan, task specs T-001..T-018.
- Source field shapes are **unverified**; each collector task starts by capturing a real response.
- Next: T-001.
