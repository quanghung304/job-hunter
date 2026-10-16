# T-008: We Work Remotely collector (RSS)

Phase: 1 · Depends on: T-005 · Size: S

## Goal
Working `WeWorkRemotelyCollector` reading configured category RSS feeds.

## Read first
- `docs/sources/weworkremotely.md` · T-004

## Steps
1. Check the site's RSS page for current category feed URLs and terms; update the spec and
   `app.sources.weworkremotely.feeds` default.
2. Capture 2 real feeds; trim to ~5 items each (keep XML structure, CDATA); save as `.xml`.
3. Implement with Rome; read `region`/`type` foreign elements; convert item to JSON for
   `rawPayload`; split "Company: Title".
4. Live test.

## Acceptance criteria
- [ ] Title without ": " → company "Unknown" + warning, still ingested.
- [ ] Same guid in two feeds → one posting.

## Tests
`WeWorkRemotelyMapperTest`, `WeWorkRemotelyCollectorTest` (WireMock serving XML), live test.
