# T-002: Profile loading and validation

Phase: 1 · Depends on: T-001 · Size: S

## Goal
`ProfileProvider.current()` returns a validated, immutable `Profile` loaded from
`config/profile.yaml`, plus a content hash.

## Read first
- `docs/architecture/config-spec.md` §1, §1.1 · `config/profile.example.yaml`

## In scope
- Records for every field in config-spec §1 (enums for employment types, seniority,
  language levels, summary language).
- `YamlProfileProvider` (Jackson YAML): loads at startup from `app.profile.path`; fails
  startup with field-specific messages (Bean Validation) on invalid content; compiles
  prefilter regexes (case-insensitive) and fails on invalid regex.
- `profileHash()` = sha256 of the file bytes.
- `RegionMatcher` utility in `profile`: implements the region rule of config-spec §1.1
  (`isAccepted(List<String> allowedRegions)` → OK / UNKNOWN / FAIL), with built-in
  membership map: EU/EEA members, EUROPE ⊇ EU ⊇ …, EMEA ⊇ EUROPE. Store the map in code as
  constants; include all current EU and EEA member country codes.
- `candidateBlock()` renders the text block sent to the scoring prompt (headline, years,
  summary, skills by tier, domains, languages, preferences in plain lines). No contact data.
- Unknown `tracks` other than `software` → WARN log, ignored.

## Out of scope
Hot reload (restart is fine). Multiple profiles.

## Acceptance criteria
- [ ] App starts with `config/profile.example.yaml` copied to `config/profile.yaml`.
- [ ] Missing required field → startup fails naming the field path.
- [ ] `RegionMatcher` returns OK for `[EU]`, `[EMEA]`, `[WORLDWIDE]`, `[IT]` with residence IT
      and acceptRegions from the example; FAIL for `[US]`, `[US, CA]`; UNKNOWN for `[]`.

## Tests
- `YamlProfileProviderTest` (valid example, each required field missing, bad regex, bad enum).
- `RegionMatcherTest` parameterized (≥ 15 rows incl. `LATAM`, `APAC`, `GB` with IT residence → FAIL
  unless accepted, `CH` → FAIL (not EU) unless EUROPE accepted → OK).
- `CandidateBlockTest` (snapshot-style assertions, no email/phone).

## Docs to update
config-spec if any field changes. `progress.md`.
