# ADR-0012: The owner edits the profile in the app; profile.yaml stays the source of truth

Status: Accepted · Date: 2026-10-19

## Context
The profile (`config/profile.yaml`) drives prefilter, eligibility checks and scoring. Editing
YAML by hand and restarting is slow and error-prone. Multiple users and profiles are a Phase 4
topic (ADR-0005, roadmap) and must not be built now.

## Decision
1. A Profile page (`/profile`) shows the **current (owner's) profile** and lets the owner edit and
   save it. Phase 1 has no profile list, no create/delete/switch and no other users.
2. `config/profile.yaml` stays the single source of truth. Saving: validate with the same rules as
   startup → write a temp file and move it over the original → keep the previous file as
   `config/profile.yaml.bak` → reload `ProfileProvider` (new hash) → publish `ProfileChanged`.
3. `ProfileChanged` re-runs the prefilter and re-evaluates eligibility checks for jobs with an OK
   extraction (no LLM). Scores and owner decisions are kept; re-scoring stays manual.
4. Optimistic concurrency: the client sends the hash it loaded (`baseHash`); a mismatch (file
   edited by hand meanwhile) returns 409 `PROFILE_CHANGED`.
5. `id` is not editable from the UI (rows in the DB are keyed by it).

## Alternatives considered
- Store the profile in the database now — needed for Phase 4, but moves the file the owner
  already maintains and changes T-002's loader for no Phase 1 gain.
- Keep hand editing only — the reason for this ADR.

## Consequences
- The app rewrites the YAML in a canonical form: comments and the key order of a hand-edited file
  are lost (the `.bak` keeps the last version).
- Hand edits still work but need a restart; saving from the page after a hand edit returns
  `PROFILE_CHANGED` until the page reloads.
- Phase 4 moves the profile into a table and adds auth; the API shape (`GET/PUT /api/profile` for
  the current user) can stay.
