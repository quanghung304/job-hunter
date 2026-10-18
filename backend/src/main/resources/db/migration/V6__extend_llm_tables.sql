-- LLM model registry, assignments and quota state (ADR-0008, T-010).
-- See docs/architecture/data-model.md §1 (llm_call, llm_assignment, llm_assignment_event, llm_model_state).

-- llm_call: registry model id next to the provider model name, tier, HTTP outcome.
alter table llm_call rename column model to provider_model;
alter table llm_call add column model_id text;
update llm_call set model_id = provider_model where model_id is null;
alter table llm_call alter column model_id set not null;
alter table llm_call add column tier text check (tier in ('FREE', 'PAID', 'LOCAL'));
alter table llm_call add column http_status int;
alter table llm_call add column error_kind text check (error_kind in (
    'RATE_LIMIT', 'QUOTA_EXHAUSTED', 'AUTH', 'MODEL_NOT_FOUND', 'TIMEOUT', 'SERVER',
    'BAD_REQUEST', 'INVALID_OUTPUT'));
-- cost is known for PAID models only; null otherwise
alter table llm_call alter column cost_usd drop not null;
alter table llm_call alter column cost_usd drop default;
alter table llm_call drop constraint llm_call_purpose_check;
alter table llm_call add constraint llm_call_purpose_check check (purpose in ('EXTRACT', 'SCORE', 'EVAL', 'TEST'));

create index llm_call_model_created_at_idx on llm_call (model_id, created_at);

create table llm_assignment (
    purpose       text        primary key check (purpose in ('EXTRACT', 'SCORE')),
    model_id      text        not null,
    fallbacks     text[]      not null default '{}',
    auto_fallback boolean     not null default false,
    allow_paid    boolean     not null default false,
    updated_at    timestamptz not null,
    updated_by    text        not null check (updated_by in ('config', 'user', 'auto-fallback'))
);

create table llm_assignment_event (
    id            bigint generated always as identity primary key,
    purpose       text        not null check (purpose in ('EXTRACT', 'SCORE')),
    from_model_id text,
    to_model_id   text        not null,
    reason        text        not null check (reason in ('USER', 'AUTO_FALLBACK', 'RESTORED')),
    created_at    timestamptz not null
);

create index llm_assignment_event_purpose_created_at_idx on llm_assignment_event (purpose, created_at desc);

create table llm_model_state (
    model_id           text        primary key,
    status             text        not null check (status in ('AVAILABLE', 'NEAR_LIMIT', 'RATE_LIMITED',
                                                              'EXHAUSTED', 'ERROR')),
    until              timestamptz,
    detail             text,
    remaining_requests int,
    remaining_tokens   int,
    updated_at         timestamptz not null
);
