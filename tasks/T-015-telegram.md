# T-015: Telegram digest and alerts

Phase: 1 · Depends on: T-013 · Size: S

## Goal
A daily digest via the Telegram Bot API. No instant alerts (owner decision, 2026-10-18).

## Read first
- PRD F2.5 · data-model `notification` · config-spec (`notify.*`, `app.notify.*`)

## In scope
- `TelegramClient`: `sendMessage` with `parse_mode=HTML`, escaping, 4096-char limit
  (split digests into several messages), retry once on 429 honoring `retry_after`.
- No instant alerts. Do not implement `notify.alertMinScore`.
- Digest (`app.notify.digest-cron`): (a) count of NEW, ACTIVE, prefilter-PASS jobs not yet
  analyzed; (b) jobs scored since last digest with score ≥ `digestMinScore`, status NEW, not
  marked Not eligible (ADR-0011), not in a previous digest; top 15 by score, + "and N more". Links: "Open" → `${app.public-base-url}/jobs/{id}`,
  "Posting" → original URL. Send nothing when (a) and (b) are both empty.
- Record every send in `notification`.
- Disabled unless `app.notify.telegram.enabled=true` and env vars present (WARN otherwise).

## Acceptance criteria
- [ ] A job appears in at most one digest.
- [ ] Message text is valid Telegram HTML (escape `<`, `>`, `&`).

## Tests
`TelegramClientTest` (WireMock: success, 429 retry, splitting), `DigestBuilderTest`,
`NotificationServiceIT`.
