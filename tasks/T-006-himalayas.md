# T-006: Himalayas collector

Phase: 1 · Depends on: T-005 · Size: S

## Goal
Working `HimalayasCollector` feeding ingest.

## Read first
- `docs/sources/himalayas.md` · T-004 (follow the same structure)

## Steps
1. Read the official Himalayas API page; record params, page-size cap, attribution terms in
   the source spec. If terms forbid this use, stop and report.
2. Capture a real response (2 pages), trim, save fixtures; correct the spec's field table.
3. Implement DTO, mapper (incl. appending location/timezone restrictions to description
   text as the spec says), collector with offset pagination and stop conditions.
4. Live test tagged `live`.

## Acceptance criteria
- [ ] Mapper covers: empty restrictions → locationText "Worldwide"; salary present/absent.
- [ ] Pagination stops on empty page, `since`, `max-items`.
- [ ] Catalog row updated with verified date.

## Tests
`HimalayasMapperTest`, `HimalayasCollectorTest` (WireMock), `HimalayasCollectorLiveTest` (`live`).
