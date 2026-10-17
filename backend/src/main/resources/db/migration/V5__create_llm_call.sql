-- One row per LLM call, for cost tracking and the budget guard.
-- See docs/architecture/data-model.md §1.

create table llm_call (
    id             bigint generated always as identity primary key,
    purpose        text           not null check (purpose in ('EXTRACT', 'SCORE', 'EVAL')),
    job_id         bigint references job (id) on delete set null,
    model          text           not null,
    prompt_version text           not null,
    input_tokens   int            not null default 0,
    output_tokens  int            not null default 0,
    cost_usd       numeric(10, 6) not null default 0,
    latency_ms     int,
    success        boolean        not null,
    error          text,
    created_at     timestamptz    not null
);

create index llm_call_created_at_idx on llm_call (created_at);
