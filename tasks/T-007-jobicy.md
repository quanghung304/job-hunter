# T-007: Jobicy collector

Phase: 1 · Depends on: T-005 · Size: S

## Goal
Working `JobicyCollector` with multiple configured queries, deduped by id.

## Read first
- `docs/sources/jobicy.md` · T-004

## Steps
1. Read Jobicy's API docs: valid `geo`, `industry`, `tag` values, max `count`, credit terms.
   Update the spec and the default `queries` with valid values.
2. Capture real responses for 2 queries; save fixtures; fix field table and `pubDate` format/zone.
3. Implement DTO, mapper, collector (≥ 2 s between calls, in-collector dedupe by `id`).
4. Live test.

## Acceptance criteria
- [ ] Two overlapping query fixtures produce unique postings.
- [ ] `pubDate` parsing documented and tested (with and without zone).

## Tests
`JobicyMapperTest`, `JobicyCollectorTest`, `JobicyCollectorLiveTest` (`live`).
