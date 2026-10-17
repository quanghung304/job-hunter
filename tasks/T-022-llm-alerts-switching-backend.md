# T-022: LLM quota alerts and model switching — backend

Phase: 1 · Depends on: T-010, T-014, T-015 · Size: M

## Goal
The owner is told (Telegram + API for the web banner) when a model is near its limit,
exhausted or broken, and can switch the model per purpose at runtime through the API.

## Read first
- ADR-0008 · data-model `llm_*`, `notification` · api-spec "LLM models", `GET /api/status`,
  `LlmAlert` · config-spec §5 · PRD F2.8, F2.9

## In scope
- New Flyway migration for `notification` (created by V4): add `ref text`, allow kind
  `LLM_QUOTA`, make `profile_id` nullable (quota alerts are not per profile). Never edit V4.
- `LlmAlertService`: builds `LlmAlert` list (one per purpose whose assigned model is not
  AVAILABLE), `pendingJobs` per purpose (assessments/extractions PENDING), `suggestions`
  (fallbacks first, then other usable models; FREE/LOCAL before PAID; exclude ERROR/EXHAUSTED).
- Telegram (`notify`): listen to `LlmModelStatusChanged`; send for transitions to
  NEAR_LIMIT, EXHAUSTED, ERROR, and "back to AVAILABLE" only if an EXHAUSTED/ERROR alert was
  sent that day. At most once per `<model>:<status>:<UTC date>` (`notification.ref`).
  Message (HTML):
  - EXHAUSTED: "⚠️ <b>Llama 3.3 70B (Groq)</b> hết hạn mức hôm nay (1000/1000 requests).
    Extraction đang tạm dừng · 23 job chờ. Mở lại lúc 02:00. Đổi model: <link to /admin#llm>".
  - NEAR_LIMIT: "… đã dùng 80% hạn mức ngày (812/1000)."
  - ERROR: "… lỗi: <detail> (kiểm tra API key)."
  - Auto-fallback switch: "Đã tự chuyển Extraction sang <label> vì <reason>."
  Language follows `profile.summaryLanguage` (vi/en); keep both templates in one class.
  (Emoji only in Telegram text, never in the web UI.)
- Endpoints per api-spec: fill `llmAlerts` in `GET /api/status` (created in T-014), `GET /api/admin/llm`,
  `PUT /api/admin/llm/assignments/{purpose}` (validation, `llm_assignment_event`,
  `warning` for PAID, optional `runNow` → triggers `PipelineOrchestrator` for LLM stages only),
  `POST /api/admin/llm/models/{modelId}/test`; add `llmAlerts` to the overview.
- Auto-fallback event: when `LlmRouter` switches automatically, write
  `llm_assignment_event` (`AUTO_FALLBACK`) and update the assignment so the UI shows reality.
  When the original model becomes AVAILABLE again it is **not** switched back automatically;
  the alert suggests switching back.
- `usageToday` per model (UTC day) and `lastEval` from `eval_run` (if the table exists yet;
  else null).
- Regenerate `openapi.json`.

## Out of scope
Web UI (T-023).

## Acceptance criteria
- [ ] Simulated 429 daily quota on the EXTRACT model → one Telegram message, `GET /api/status`
      returns an alert with `paused: true`, `pendingJobs`, and suggestions.
- [ ] A second 429 the same day sends no new message.
- [ ] `PUT` to another model clears the alert for that purpose, records history, and the next
      run uses the new model; with `runNow: true` pending jobs are processed immediately.
- [ ] Switching to a model in ERROR returns 409 `MODEL_UNUSABLE`.
- [ ] Test endpoint clears ERROR on success.

## Tests
`LlmAlertServiceIT`, `LlmQuotaTelegramTest` (message text vi/en, dedupe, recovery message),
`LlmAdminControllerTest` (`@WebMvcTest`: get, put ok/400/409/paid warning, test),
`StatusControllerTest`, `AutoFallbackIT` (event + assignment update).

## Docs to update
api-spec if shapes changed; runbook if operations changed; progress.
