# T-015: Telegram digest and alerts

Phase: 1 · Depends on: T-013 · Size: S

## Goal
Instant alerts for top jobs and a daily digest via the Telegram Bot API.

## Read first
- PRD F2.5 · data-model `notification` · config-spec (`notify.*`, `app.notify.*`)

## In scope
- `TelegramClient`: `sendMessage` with `parse_mode=HTML`, escaping, 4096-char limit
  (split digests into several messages), retry once on 429 honoring `retry_after`.
- Alert: on `NewScoredJobs`, for scores ≥ `notify.alertMinScore` not alerted before →
  one message per job: score, title, company, summary, region/employment line, links
  ("Open" → `${app.public-base-url}/jobs/{id}`, "Posting" → original URL).
- Digest (`app.notify.digest-cron`): jobs scored since last digest with score ≥
  `digestMinScore`, status NEW, not in a previous digest; top 15 by score, + "and N more";
  send nothing when empty.
- Record every send in `notification`.
- Disabled unless `app.notify.telegram.enabled=true` and env vars present (WARN otherwise).

## Acceptance criteria
- [ ] A job is alerted at most once and appears in at most one digest.
- [ ] Message text is valid Telegram HTML (escape `<`, `>`, `&`).

## Tests
`TelegramClientTest` (WireMock: success, 429 retry, splitting), `DigestBuilderTest`,
`NotificationServiceIT`.
