# T-018: ATS collectors — Greenhouse, Lever, Ashby

Phase: 1 · Depends on: T-005 · Size: M

## Goal
Three collectors reading public job boards of companies in `config/companies.yaml`, with
company health tracking.

## Read first
- `docs/sources/ats.md` · config-spec §4 · data-model `company` · T-004

## Steps
1. Read each vendor's public job board API docs (Greenhouse Job Board API, Lever Postings API,
   Ashby Public Job Posting API). Note any terms and rate guidance in `ats.md`.
2. Capture one real response per ATS from the smoke-test companies; trim to ~5 jobs incl.
   one remote and one on-site; save fixtures; correct the field lists in `ats.md`.
3. `CompanySeeder`: on startup upsert `config/companies.yaml` into `company` (do not
   overwrite status columns).
4. `GreenhouseCollector`, `LeverCollector` (incl. EU host), `AshbyCollector` with mappers per spec;
   shared `CompanyBoardCollector` base handling iteration, 1 s politeness, 404 counting
   (3 consecutive → `enabled=false`), status updates.
5. Command-line check `--validate-companies` (or admin button) that pings each board once and
   prints OK/NOT_FOUND/ERROR with job counts.
6. Live tests (one board per ATS).

## Acceptance criteria
- [ ] Greenhouse `content` HTML entities are unescaped before HTML → text.
- [ ] Lever lists (requirements etc.) are included in the description text with headings.
- [ ] Ashby `isListed=false` jobs are skipped.
- [ ] A 404 board does not fail the whole collector run (status PARTIAL).

## Tests
`GreenhouseMapperTest`, `LeverMapperTest`, `AshbyMapperTest`, `CompanyBoardCollectorTest`
(WireMock: OK, 404 ×3 disables, 500), `CompanySeederIT`, live tests.
