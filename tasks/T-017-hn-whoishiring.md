# T-017: Hacker News "Who is hiring?" collector

Phase: 1 · Depends on: T-005 · Size: M

## Goal
Each run ingests top-level job comments from the current (and early in the month, the
previous) "Ask HN: Who is hiring?" thread.

## Read first
- `docs/sources/hn-whoishiring.md` · T-004

## Steps
1. Capture fixtures: one Algolia `search_by_date` response, one `items/{id}` response trimmed
   to ~10 top-level children (include: well-formatted header, header without `|`, multi-role,
   "REMOTE (US only)", ONSITE, a deleted/empty child, a child with nested replies).
   Update field names in the spec.
2. Implement thread discovery, item fetch, header parser (pure, heavily tested), mapper.
3. Min interval 12h; one thread fetch per run (two in the first 7 days of the month).
4. Live test.

## Acceptance criteria
- [x] Only top-level children become postings; nested replies ignored.
- [x] Header parser handles the cases in step 1 per the spec.
- [x] Re-running on the same thread creates no duplicates (externalId = comment id).

## Tests
`HnHeaderParserTest` (≥ 15 real-looking headers), `HnMapperTest`, `HnCollectorTest` (WireMock),
live test.
