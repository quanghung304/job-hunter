-- ADR-0011: the app stores one check per eligibility criterion and no verdict; the owner decides.
-- See docs/architecture/data-model.md (job_assessment, job_tracking, job_tracking_event, §2.1).
-- V3/V4 are applied and stay unchanged. Checks of analyzed jobs are evaluated again from the
-- stored extractions by the application on startup (no LLM); owner decisions start empty.

alter table job_assessment add column eligibility_checks jsonb;
alter table job_assessment drop column eligibility;
alter table job_assessment drop column eligibility_reasons;

alter table job_tracking add column eligibility_decision text
    constraint job_tracking_eligibility_decision_check check (eligibility_decision in ('ELIGIBLE', 'INELIGIBLE'));
alter table job_tracking add column eligibility_decided_at timestamptz;
create index job_tracking_profile_decision_idx on job_tracking (profile_id, eligibility_decision);

alter table job_tracking_event add column kind text not null default 'STATUS'
    constraint job_tracking_event_kind_check check (kind in ('STATUS', 'ELIGIBILITY'));
alter table job_tracking_event drop constraint job_tracking_event_from_status_check;
alter table job_tracking_event drop constraint job_tracking_event_to_status_check;
alter table job_tracking_event add constraint job_tracking_event_values_check check (
    (kind = 'STATUS'
        and from_status in ('NEW', 'SHORTLISTED', 'DISMISSED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN')
        and to_status in ('NEW', 'SHORTLISTED', 'DISMISSED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN'))
    or (kind = 'ELIGIBILITY'
        and from_status in ('ELIGIBLE', 'INELIGIBLE', 'UNDECIDED')
        and to_status in ('ELIGIBLE', 'INELIGIBLE', 'UNDECIDED')));
