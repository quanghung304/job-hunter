# T-028: Frontend — Profile page

Phase: 1 · Depends on: T-027, T-019 · Size: M

## Goal
The owner edits their own profile on `/profile` and saves it, with the same validation as the
backend (ADR-0012).

## Read first
- `frontend.md` "/profile" and "Behavior › Profile" · `api-spec.md` "Profile" · `config-spec.md` §1
- Mockup artboard "Profile · desktop"

## In scope
- Route `/profile`, header nav link "Profile", profile pill links to it.
- Hooks: `useProfile()`, `useSaveProfile()`; `useProfileForm` (local draft, dirty flag, client
  checks for required fields, maps server `errors[].path` to fields).
- Components: `ProfilePage`, `ProfileForm` (sections Candidate, Skills and domains, Preferences,
  Prefilter, Notifications), `ChipsInput` (Enter adds, Backspace on empty input removes the last
  chip, each chip has a remove button with `aria-label`), `ToggleChips` (`aria-pressed`).
- Header card: "Your profile · <id>", mono "Saved <time> · hash <short> · config/profile.yaml",
  dirty tag, Discard (quiet), Save changes (primary, disabled while clean or invalid).
- Error banner listing problems; info box about re-checks; toast after save with the counts.
- Leave guard: unsaved changes → confirm dialog (React Router `useBlocker`).
- 409 `PROFILE_CHANGED` → ErrorState with a "Reload" button.

## Out of scope
Profile list, other users, creating profiles, editing `id`, YAML import/export.

## Acceptance criteria
- [ ] Matches the "Profile · desktop" mockup at 1440 px; one column at 390 px.
- [ ] Save sends one PUT with `baseHash`; success updates the header hash and shows the toast.
- [ ] Server validation errors appear next to the matching fields and in the banner.
- [ ] Navigating away with unsaved changes asks for confirmation.

## Tests
`ChipsInput.test.tsx`, `ProfileForm.test.tsx` (dirty, discard, client validation),
`ProfilePage.test.tsx` (MSW: load, save, 400 with errors, 409). Playwright flow 5 (testing.md).
