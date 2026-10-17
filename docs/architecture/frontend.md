# Frontend design

React SPA in `frontend/` (ADR-0007). Single user, desktop first, usable at 360 px.
Visual source of truth: the approved mockups in the Claude Design canvas
**"Job Hunter UI mockups"** (artboards: Board + job detail · desktop, Admin · desktop,
Board · mobile, Job detail · mobile, Components & states). This file turns them into rules;
if this file and the mockups disagree, this file wins and the owner updates the mockup.

Style name: **inbox triage** — a dense, calm, keyboard-first review tool (think an email
client), not a dashboard or a card gallery.

## Stack

| Concern | Choice |
|---|---|
| Build | Vite, TypeScript `strict: true` |
| Routing | React Router (data router not required) |
| Server state | TanStack Query (no Redux/Zustand; local UI state with `useState`) |
| API client | `openapi-fetch` with types from `openapi-typescript` (`src/api/schema.d.ts`, generated) |
| UI | Tailwind CSS + shadcn/ui components (copied into `src/components/ui`, restyled with the tokens below), lucide icons |
| Fonts | `@fontsource/ibm-plex-sans` (400/500/600/700) and `@fontsource/ibm-plex-mono` (400/500/600), self-hosted |
| Forms | plain controlled inputs (only notes and filters; no form library) |
| Lint/format | ESLint (typescript-eslint, react-hooks), Prettier |
| Tests | Vitest + React Testing Library + MSW; Playwright for 3 smoke flows |

Use current stable major versions at bootstrap time and record them in `progress.md`.

## Structure

```
frontend/
  src/
    api/            client.ts (openapi-fetch instance), schema.d.ts (generated), queries.ts (hooks)
    components/ui/  shadcn components (generated, restyled via tokens)
    components/     ScoreBadge, StatusTag, EligibilityTag, RuleResultTag, FactChip, StatusActions,
                    SourceLinks, SectionCard, SectionLabel, EmptyState, ErrorState, ListSkeleton,
                    AppHeader, Kbd
    features/board/ BoardPage, FilterBar, JobList, JobRow, JobDetailPane, useBoardFilters,
                    useBoardKeyboard
    features/job/   JobDetail (shared by pane and mobile page), WhyItFits, ScoreBreakdown,
                    EligibilityPanel, FactsPanel, DescriptionPanel, NotesEditor, HistoryList,
                    MobileActionBar
    features/admin/ AdminPage, KpiRow, SourcesTable, LlmUsageCard, RunsList, FailuresList,
                    CompaniesTable, PipelineBanner, RescoreDialog
    lib/            format.ts, labels.ts, statusMachine.ts, scoreBand.ts, useMediaQuery.ts
    styles/         tokens.css (CSS variables), index.css
    App.tsx, main.tsx, routes.tsx
  e2e/              Playwright specs
```

## Visual design

### Color tokens

Defined once as CSS variables in `src/styles/tokens.css` and mapped in
`tailwind.config` (`colors: { ground: 'var(--ground)', … }`). Components use only these
names — **no raw hex values in components**. shadcn's own variables (`--background`,
`--primary`, `--border`, `--ring`, …) are set from these tokens.

| Token | Value | Use |
|---|---|---|
| `--ground` | `#F6F6F3` | page background, detail pane background |
| `--surface` | `#FFFFFF` | header, list, cards, inputs |
| `--surface-muted` | `#FBFBF9` | filter bar, list footer, kbd background |
| `--ink` | `#1B1D20` | primary text, "Apply" button, toasts |
| `--ink-2` | `#33373C` | body text inside cards, quiet buttons |
| `--muted` | `#5A5F66` | secondary text, section labels, icons (≥ 4.5:1 on white) |
| `--line` | `#E3E3DE` | card and layout borders |
| `--line-soft` | `#EDEDE8` | row dividers inside a list/card |
| `--line-faint` | `#F1F1EC` | table row dividers, fact chip background |
| `--control-border` | `#D9D9D3` | inputs, secondary buttons |
| `--neutral-fill` | `#ECECE6` | bar tracks, skeletons, neutral tags |
| `--accent` | `#2F5BD3` | primary buttons, score ≥ 85, active nav, bars, meter |
| `--accent-tint` | `#E0E6F8` | score 70–84, OK tags, Shortlisted/Applied tags |
| `--accent-tint-2` | `#F0F4FC` | selected list row, running banner background |
| `--accent-deep` | `#1F3B89` | text on `--accent-tint`, text links inside cards |
| `--attention-bg` | `#FCEFD9` | "Needs check", "Unclear", Partial, warnings |
| `--attention-fg` | `#7A4A00` | text on `--attention-bg`; warning icon stroke `#B56A00` |
| `--eligible-bg` | `#E7F0EC` | "Eligible" tag |
| `--eligible-fg` | `#1F5240` | text on `--eligible-bg` |
| `--strong` | `#3D4147` | "Fail", "Ineligible", "Failed" tags (white text) |
| `--maybe-bg` | `#EFEFEA` | score 50–69 badge (text `--ink-2`) |

Color rules:
- One accent hue only. Tints are derived from it (white mix 85% / 93%, black mix 35%);
  if the accent changes, recompute all three accent variables.
- Never red/green as a pair. Attention = orange family; failure = strong neutral.
- Color is never the only signal: badges show the number, tags show words.
- Light theme only in Phase 1. Keep all colors in variables so a dark theme is a later
  token swap (add `prefers-color-scheme: dark` overrides then, not now).

### Typography

| Role | Font | Size / weight | Notes |
|---|---|---|---|
| Base / body | IBM Plex Sans | 14px / 400, line-height 1.45 | `html { font-size: 14px }` is NOT used; set on `body` |
| Page / job title | Plex Sans | 22px / 600, letter-spacing −0.01em (mobile 19px) | |
| LLM summary | Plex Sans | 16px / 400, line-height 1.5 (mobile 15.5px) | `lang` attribute = profile summary language |
| Job description | Plex Sans | 15px, line-height 1.6, max 72ch, `white-space: pre-line` | |
| Row title | Plex Sans | 14.5px / 600, single line ellipsis (mobile: 2 lines) | |
| Secondary / meta | Plex Sans | 13–13.5px, `--muted` | |
| Section label | Plex Sans | 12px / 600, uppercase, letter-spacing 0.07em, `--muted` | `SectionLabel` component |
| Tags / chips | Plex Sans | 12px / 500 (rule results 600) | |
| **All numbers** | **IBM Plex Mono** | inherit size; badges 15px / 600 | scores, times, counts, money, costs, keyboard hints |

Use `font-variant-numeric: tabular-nums` on mono numbers in tables.

### Spacing, radius, elevation

- Spacing scale (px): 4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32. Page gutters 24px desktop,
  16px mobile. Card padding 20px 22px desktop, 16px mobile. Gaps between cards 20px.
- Radius: tags 3px, badges/score 4px, inputs and buttons 5px, cards 6px, dialog 8px,
  profile pill and mobile filter chips 999px.
- Borders over shadows. Shadows only on: toast `0 8px 24px rgb(0 0 0 / .18)`, dialog
  `0 20px 48px rgb(0 0 0 / .25)`, mobile action bar `0 -6px 16px rgb(0 0 0 / .05)`.
- Heights: desktop buttons/inputs 36–38px; icon buttons 32px in list rows (desktop),
  44px minimum touch targets on mobile; mobile action bar buttons 48px.

### Iconography

lucide-react, stroke 1.8–2, size 14–20px, color `--muted` unless the icon carries state
(filled star in `--accent` = shortlisted). No emoji. Icon-only buttons need `aria-label`
and a `title` that includes the shortcut, e.g. `title="Shortlist (s)"`.

### Motion

Minimal: a dismissed row collapses (height + opacity, 150ms ease-out); toast fades in;
no page transitions, no decorative animation. Respect `prefers-reduced-motion` (no
transitions at all).

## Shared components (spec)

| Component | Spec |
|---|---|
| `ScoreBadge` | Mono number in a box. Sizes: `sm` 40×32 (rows), `lg` 72×72 with band word below in 10.5px letter-spaced caps (detail; mobile 60×60). Band styles from `lib/scoreBand.ts`: EXCELLENT ≥ 85 = `--accent` bg + white; GOOD 70–84 = `--accent-tint` + `--accent-deep`; MAYBE 50–69 = `--maybe-bg` + `--ink-2`; WEAK < 50 = transparent + 1px inset `--control-border` + `--muted`; NONE (unscored) = "—" with 1px inset `--line`. |
| `StatusTag` | New = `--line-faint` / `#3D4147`; Shortlisted, Applied = `--accent-tint` / `--accent-deep`; Interviewing, Offer = `--accent` / white; Rejected, Withdrawn = `--neutral-fill` / `--muted`. Rows show it only when status ≠ NEW. |
| `EligibilityTag` | Eligible = `--eligible-*`; Needs check (UNCERTAIN) = `--attention-*`; Ineligible = `--strong` / white. Rows show only "Needs check". |
| `RuleResultTag` | OK = `--accent-tint` / `--accent-deep`; Unclear = `--attention-*`; Fail = `--strong` / white; 12px / 600. |
| `FactChip` | `--line-faint` bg (`--neutral-fill` in the detail header), `#3D4147` text, 12px, radius 3px. |
| Buttons | Primary = `--accent` bg, white; Secondary = white, 1px `--control-border`; Quiet = transparent, `--ink-2`; Leave-app ("Apply") = `--ink` bg, white; Disabled = `--neutral-fill`, `--muted`. **Exactly one primary per view**: the next step of the status flow. Desktop action buttons show their shortcut after the label in mono at 70% opacity. |
| `SectionCard` | `--surface`, 1px `--line`, radius 6px, padding 20/22, starts with a `SectionLabel`. |
| `Toast` | Bottom-left 24px, `--ink` bg, white text, max-width 520px, one at a time, 5 s; every status change toast has "Undo" (outlined `--muted` button). Uses shadcn Sonner restyled. |
| `EmptyState` | Centered inbox icon (`#9A9EA4`, 32px), bold title, one muted sentence, optional secondary button. Board empty text: "Inbox zero" / "No jobs match these filters." |
| `ErrorState` | `--ground` box, 1px `--line`, warning icon `#B56A00`, bold title, `detail` from Problem Details, and `status · code` in mono 12px muted. Network failure variant: "Backend not reachable — Is the app running?". |
| `ListSkeleton` | Rows with a 40×32 `--neutral-fill` block and three bars (12px, 10px, 10px) in `--neutral-fill`/`--line-faint`; no shimmer if reduced motion. |
| `Kbd` | Mono 12px, 1px `--control-border` (2px bottom), radius 4px, `--surface-muted` bg. |

## Layout

### App header (all desktop pages)
Height 56px, `--surface`, bottom border `--line`, padding 0 24px. Left: logo icon (briefcase,
`--accent`) + "Job Hunter" 15px/700. Nav links Board / Admin: active link 600 weight with a
2px `--accent` bottom indicator (`box-shadow: inset 0 -2px 0`), inactive `--muted`. Right:
mono status text "Last run 07:00 · 42 new · 9 scored" (from `/api/admin/overview`) and the
profile pill (dot in `--accent` + profile id). Wraps on narrow screens.

### `/` and `/jobs/:id` — Board (desktop ≥ 1024px: split view)
- Under the header, the **filter bar** (`--surface-muted`, bottom border, padding 12px 24px,
  wraps): search field (min 240px, with `/` kbd hint), one dropdown button per filter
  showing "Label **value**" (Status, Eligibility, Score, Source, Type, Posted), "Reset" as an
  underlined text button, then right-aligned: mono job count and a "Sort: Score" button.
- Below: two columns in a wrapping flex row.
  - **List** (`flex: 1 1 440px; max-width: 600px`), `--surface`, right border. Rows separated
    by `--line-soft`. Row = [select button: ScoreBadge sm · title · "company · sources" ·
    summary (2-line clamp, hidden in compact density) · tags (Needs check, StatusTag,
    FactChips)] + [posted relative time, mono 12px, top-right] + [icon buttons column:
    star (shortlist toggle) and ✕ (dismiss)]. Selected row background `--accent-tint-2`
    and `aria-current="true"`. Row padding 14px (compact: 10px). "Load more" button at the
    end. Sticky list footer with the keyboard hint line (mono 12px):
    `j/k move · Enter open · s shortlist · d dismiss · a applied · o posting · / search`.
  - **Detail pane** (`flex: 999 1 560px`), `--ground`, padding 28px 32px 56px, vertical
    gap 20px, scrolls with the page. Content = `JobDetail` (below).
- Routing: selecting a row navigates to `/jobs/:id` **keeping the current search params**;
  on desktop that route renders the same split view with that job selected. Opening `/`
  selects the first job of the list (URL stays `/` until the user moves).
- Density: user toggle "Comfortable / Compact" in the filter bar's sort area, stored in
  `localStorage` (try/catch), default comfortable.

### `JobDetail` content order (pane and mobile page)
1. Header: ScoreBadge lg · title (h1) · "company · location · posted X ago" · tags
   (StatusTag, EligibilityTag, FactChips).
2. Action row: StatusActions from `allowedTransitions` (primary first, then secondary, then
   quiet Dismiss), spacer, "Open posting ↗" (secondary link, new tab), "Apply" (ink link,
   new tab, goes to `applyUrl`).
3. Card "Why it fits": summary (16px), "Khi apply:"-style advice line labelled with the
   localized word for the profile language (vi: "Khi apply:", en: "If applying:"), then a
   3-column auto-fit grid: Strengths (check icon `--accent`), Gaps (minus icon `--muted`),
   Red flags (warning icon, `--attention-fg` text; "None found" when empty).
4. Two-column auto-fit grid (min 320px): Score breakdown (label 96px · bar 8px high in
   `--neutral-fill` with `--accent` fill · "value/max" mono; footer: prompt version and
   model in mono muted, total right) and Eligibility (rule label 104px muted · value ·
   RuleResultTag).
5. Card "Extracted facts": `<dl>` in an auto-fit grid (min 260px), each pair label 130px.
6. Card "Description": header row with "Sources: via X" links; text collapsed to ~6 lines
   with a fade mask and a "Show full description" text button.
7. Two-column auto-fit grid: Notes (`<label>` + textarea 5 rows + hint "Autosaves as you
   type" → "Saved · just now", mono 12px) and History (time mono 12.5px muted · text).

### Mobile (< 1024px)
- `/` = list only. Header 56px: logo, "Board", mono count, search icon button (opens a
  full-width search field), menu icon button (links Board / Admin). Filter bar becomes a
  horizontally scrolling row: black "Filters · N" pill (opens a bottom sheet with all
  filters) followed by pills of the active filter values.
- Rows as desktop, title clamped to 2 lines, summary hidden, icon buttons 44×44. Tapping a
  row navigates to `/jobs/:id`.
- `/jobs/:id` = full page: top bar with back chevron (returns to `/` with filters kept),
  "Job N of M", "Posting ↗"; then `JobDetail` with cards stacked (padding 16px), and a fixed
  **bottom action bar** (white, top border, shadow): Dismiss icon button 48×48, secondary
  action, primary action (flex 1 each), safe-area padding at the bottom. Hide the desktop
  action row on mobile.
- Toast sits above the bottom action bar / at the bottom of the list, inset 12px.

### `/admin`
- Container max-width 1280px, centered, padding 28px 24px 64px, vertical gap 20px.
- Title row: "System" (22px) + muted line "Next scheduled run 19:00 Europe/Rome · digest
  08:30"; right: buttons Export for labeling (secondary), Re-score active jobs… (secondary),
  Run pipeline (primary with play icon; disabled "Running…" style while running).
- `PipelineBanner` while running: `--accent-tint-2` bg, 1px `--accent-tint` border, title in
  `--accent-deep`, mono progress text, slim progress bar.
- KPI row: auto-fit grid (min 220px) of 4 cards: Active jobs, New today, Scored today,
  To review — label (section label), mono 26px value, muted sub-line.
- Row: Sources table (flex 999 1 640px; columns Source, Status tag, Fetched, New jobs,
  Last success, Note; numbers right-aligned mono; table in an `overflow-x: auto` box) and
  LLM usage card (flex 1 1 340px: mono $ value "of $5.00 budget", meter 10px, mono calls
  and tokens line, 14-day cost bars 84px high — today's bar `--accent`, others accent mixed
  55% with white — first and last date labels).
- Row: Recent pipeline runs (time · summary · status tag) and Failed LLM work (purpose tag
  · job title · mono error · Retry button).
- Companies table (Company, ATS, Board slug mono, Check tag, Active jobs, Checked) with the
  hint "Edit list in config/companies.yaml".
- `RescoreDialog`: shadcn Dialog, max-width 460px; title "Re-score active jobs?", text with
  mono job count, a `--ground` box "Estimated cost ≈ $x", Cancel (secondary) and
  "Re-score N jobs" (primary).
- Source/company/run status tags: OK = accent tint; Partial, Budget, Not found n/3 =
  attention; Failed, Error = strong; Skipped, Off = neutral.

## Behavior

### Board
- Filters live in URL search params so views are bookmarkable; "Reset" returns to the
  default view (api-spec). Search debounced 300 ms.
- Status actions: only transitions allowed for the status (`lib/statusMachine.ts` mirrors
  data-model §2 and is covered by a test; the detail uses the server's `allowedTransitions`).
- Optimistic status update with rollback on error (error toast with the problem `detail`).
  Dismissing removes the row, selects the next row (or previous), and shows the Undo toast.
- Keyboard (`useBoardKeyboard`, attached to the board page only, ignored while focus is in
  an input/textarea/select or a dialog is open): `j`/`k` next/previous, `Enter` open (mobile
  route; on desktop focuses the pane), `s` shortlist toggle, `d` dismiss, `a` mark applied,
  `o` open original posting, `/` focus search, `Esc` blur search.
- Selected row is scrolled into view on keyboard moves.
- Pagination: "Load more" (infinite query).

### Job detail
- Notes autosave 1 s after typing stops; show "Saved · just now" after success.
- Description collapsed by default per job; expanding is remembered while the job is open.

### Admin
- Overview refetch every 3 s while `pipelineRunning`, else every 60 s.
- Run → 202 → banner; 409 → toast "Pipeline is already running".
- Re-score: dry run first; the dialog must show the count before executing.

## Rules

- All data access through hooks in `src/api/queries.ts`; components never call `fetch`.
- Never hand-edit `schema.d.ts`; run `npm run gen:api`.
- Loading, empty and error states for every query (shared components above).
- Only token names in styles; a new color needs a new token in this file first.
- Accessibility: buttons are `<button>`, links are `<a href>`, labels for inputs, visible
  focus ring (2px `--accent` outline, offset 2px), color never the only signal, icon-only
  buttons have `aria-label`, text contrast ≥ 4.5:1.
- No external analytics, no fonts/scripts from third-party CDNs (works offline).
- UI text in English; job summaries and apply advice arrive in the profile's language from
  the backend and are rendered with that `lang`.
- Responsive breakpoint for split view vs mobile: 1024px (`lib/useMediaQuery.ts`).
