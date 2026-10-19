# T-027: Profile editing — backend

Phase: 1 · Depends on: T-002, T-009, T-014, T-025 · Size: M

## Goal
The owner can read and save the current profile through the API; saving rewrites
`config/profile.yaml`, reloads the profile and re-checks jobs without any LLM call (ADR-0012).

## Read first
- ADR-0012 · `config-spec.md` §1 · `api-spec.md` "Profile" · data-model `job_assessment`
- T-002 (`YamlProfileProvider`, validation messages by field path)

## In scope
- `ProfileProvider` becomes reloadable: `current()`, `profileHash()`; reload swaps an immutable
  snapshot.
- `ProfileService.save(Profile, String baseHash)`: same Bean Validation as startup (all problems
  by field path); `id` must equal the current id (`PROFILE_ID_IMMUTABLE`); hash mismatch →
  `PROFILE_CHANGED` (409); write temp file + atomic move; copy the old file to
  `config/profile.yaml.bak`; reload; publish `ProfileChanged(oldHash, newHash)`.
- YAML writer: Jackson YAML, canonical key order = config-spec §1 order, no nulls written for
  absent optional keys.
- `enrich` listens to `ProfileChanged`: `AssessmentService.reassessPrefilter(profile, hash)`,
  which re-evaluates `eligibility_checks` for rows with an OK extraction (T-025); returns counts.
- `GET /api/profile` and `PUT /api/profile` per api-spec (DTO mirrors the YAML structure);
  `VALIDATION_FAILED` problem with `errors: [{path, message}]`.

## Out of scope
More than one profile, auth, profile list/create/delete, DB storage of profiles (Phase 4).
Preserving YAML comments.

## Acceptance criteria
- [ ] Saving a valid profile rewrites the file, keeps a `.bak`, changes the hash and re-runs
      prefilter and checks with 0 LLM calls.
- [ ] An invalid profile is rejected with every problem listed by path; the file is unchanged.
- [ ] A save with a stale `baseHash` returns 409 `PROFILE_CHANGED`; the file is unchanged.
- [ ] Scores and owner eligibility decisions survive a save.

## Tests
`ProfileServiceTest` (valid, invalid, id change, stale hash, atomic write with a temp dir),
`ProfileYamlWriterTest` (round trip: write → load → equal), `ProfileChangedIT` (prefilter and
checks re-evaluated, scores and decisions kept, 0 LLM calls), `ProfileControllerTest`.

## Docs to update
`config-spec.md` if the YAML writer changes any rule; `runbook.md` (editing the profile); progress.
