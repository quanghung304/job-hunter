-- T-018: consecutive 404s of a company board; the ATS collectors disable a board after 3.
alter table company add column not_found_count integer not null default 0 check (not_found_count >= 0);
