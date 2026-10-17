-- Core tables: collector runs, ATS companies, deduplicated jobs and raw postings.
-- See docs/architecture/data-model.md §1 and §5.

create table collector_run (
    id              bigint generated always as identity primary key,
    source_code     text        not null,
    started_at      timestamptz not null,
    finished_at     timestamptz,
    status          text check (status in ('SUCCESS', 'PARTIAL', 'FAILED')),
    fetched_count   int         not null default 0,
    new_raw_count   int         not null default 0,
    new_job_count   int         not null default 0,
    error_message   text
);

create index collector_run_source_started_idx on collector_run (source_code, started_at desc);

create table company (
    id                bigint generated always as identity primary key,
    name              text    not null,
    ats               text    not null check (ats in ('GREENHOUSE', 'LEVER', 'LEVER_EU', 'ASHBY')),
    board_slug        text    not null,
    enabled           boolean not null default true,
    last_check_status text check (last_check_status in ('OK', 'NOT_FOUND', 'ERROR')),
    last_checked_at   timestamptz,
    constraint company_ats_board_slug_key unique (ats, board_slug)
);

create table job (
    id               bigint generated always as identity primary key,
    fingerprint      text        not null,
    title            text        not null,
    company_name     text        not null,
    company_key      text        not null,
    location_text    text,
    remote_hint      text check (remote_hint in ('REMOTE', 'HYBRID', 'ONSITE', 'UNKNOWN')),
    employment_hint  text,
    description_text text        not null,
    description_hash text,
    apply_url        text,
    salary_min       numeric,
    salary_max       numeric,
    salary_currency  text,
    salary_period    text check (salary_period in ('YEAR', 'MONTH', 'HOUR')),
    tags             text[]      not null default '{}',
    posted_at        timestamptz,
    first_seen_at    timestamptz not null,
    last_seen_at     timestamptz not null,
    status           text        not null default 'ACTIVE' check (status in ('ACTIVE', 'EXPIRED')),
    constraint job_fingerprint_key unique (fingerprint)
);

create index job_status_last_seen_idx on job (status, last_seen_at);
create index job_company_key_idx on job (company_key);

create table raw_posting (
    id            bigint generated always as identity primary key,
    source_code   text        not null,
    external_id   text        not null,
    url           text        not null,
    payload       jsonb       not null,
    payload_hash  text        not null,
    first_seen_at timestamptz not null,
    last_seen_at  timestamptz not null,
    job_id        bigint references job (id),
    constraint raw_posting_source_external_id_key unique (source_code, external_id)
);

create index raw_posting_job_id_idx on raw_posting (job_id);
