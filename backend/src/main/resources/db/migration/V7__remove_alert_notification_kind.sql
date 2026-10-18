-- Instant job alerts were removed (ADR-0010): only the daily digest is sent per job.
-- See docs/architecture/data-model.md (notification). V4 is applied and stays unchanged.

alter table notification drop constraint notification_kind_check;
alter table notification add constraint notification_kind_check check (kind in ('DIGEST'));
