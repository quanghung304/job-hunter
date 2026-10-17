-- LLM extraction (profile-independent) and per-profile assessment (prefilter, eligibility,
-- score). See docs/architecture/data-model.md §1 and docs/ai/scoring-spec.md.

create table job_extraction (
    id             bigint generated always as identity primary key,
    job_id         bigint      not null references job (id),
    prompt_version text        not null,
    model          text,
    attributes     jsonb,
    status         text        not null check (status in ('OK', 'FAILED')),
    attempts       int         not null default 0,
    created_at     timestamptz not null,
    constraint job_extraction_job_prompt_key unique (job_id, prompt_version)
);

create table job_assessment (
    id                   bigint generated always as identity primary key,
    job_id               bigint      not null references job (id),
    profile_id           text        not null,
    profile_hash         text        not null,
    prefilter_result     text check (prefilter_result in ('PASS', 'REJECT')),
    prefilter_reasons    text[]      not null default '{}',
    eligibility          text check (eligibility in ('ELIGIBLE', 'UNCERTAIN', 'INELIGIBLE')),
    eligibility_reasons  text[]      not null default '{}',
    extraction_id        bigint references job_extraction (id),
    score                int check (score between 0 and 100),
    score_breakdown      jsonb,
    summary              text,
    score_prompt_version text,
    score_model          text,
    status               text        not null check (status in ('PENDING', 'DONE', 'FAILED')),
    attempts             int         not null default 0,
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    -- NULLS NOT DISTINCT: rows not scored yet (null prompt version) must be unique as well.
    constraint job_assessment_job_profile_prompt_key
        unique nulls not distinct (job_id, profile_id, score_prompt_version)
);

create index job_assessment_profile_score_idx on job_assessment (profile_id, score desc);
create index job_assessment_status_idx on job_assessment (status);
create index job_assessment_extraction_id_idx on job_assessment (extraction_id);
