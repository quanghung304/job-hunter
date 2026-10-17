-- Owner tracking of jobs per profile, its history, and sent notifications.
-- See docs/architecture/data-model.md §1 and §2.

create table job_tracking (
    job_id     bigint      not null references job (id),
    profile_id text        not null,
    status     text        not null check (status in ('NEW', 'SHORTLISTED', 'DISMISSED', 'APPLIED',
                                                      'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN')),
    notes      text,
    updated_at timestamptz not null,
    primary key (job_id, profile_id)
);

create table job_tracking_event (
    id          bigint generated always as identity primary key,
    job_id      bigint      not null references job (id),
    profile_id  text        not null,
    from_status text        not null check (from_status in ('NEW', 'SHORTLISTED', 'DISMISSED', 'APPLIED',
                                                            'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN')),
    to_status   text        not null check (to_status in ('NEW', 'SHORTLISTED', 'DISMISSED', 'APPLIED',
                                                          'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN')),
    note        text,
    created_at  timestamptz not null
);

create index job_tracking_event_job_profile_idx on job_tracking_event (job_id, profile_id, created_at);

-- profile_id is not in the original table sketch; it is needed for
-- "a job is alerted at most once per profile" (data-model.md §1).
create table notification (
    id         bigint generated always as identity primary key,
    profile_id text        not null,
    channel    text        not null check (channel in ('TELEGRAM')),
    kind       text        not null check (kind in ('DIGEST', 'ALERT')),
    job_ids    bigint[]    not null default '{}',
    sent_at    timestamptz not null,
    success    boolean     not null,
    error      text
);

-- "Was this job already alerted for this profile?" filters with job_ids @> array[...].
create index notification_job_ids_idx on notification using gin (job_ids);
create index notification_profile_kind_sent_idx on notification (profile_id, kind, sent_at desc);
