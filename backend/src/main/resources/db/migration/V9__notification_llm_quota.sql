-- LLM quota alerts (T-022, ADR-0008): kind LLM_QUOTA, a dedupe ref, no profile for quota alerts.
-- See docs/architecture/data-model.md (notification). V4 and V7 stay unchanged.

alter table notification add column ref text;
alter table notification alter column profile_id drop not null;
alter table notification drop constraint notification_kind_check;
alter table notification add constraint notification_kind_check check (kind in ('DIGEST', 'LLM_QUOTA'));

-- "<model_id>:<status>:<yyyy-MM-dd>" is announced at most once; failed sends may be retried.
create unique index notification_ref_sent_uidx on notification (ref) where ref is not null and success;
