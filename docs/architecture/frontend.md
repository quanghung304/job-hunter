# Frontend design

React SPA in `frontend/` (ADR-0007). Single user, desktop first, usable at 360 px.
Visual source of truth: the approved mockups in the Claude Design canvas
**"Job Hunter UI mockups"** (artboards: Board grid → job detail · desktop, Admin · desktop,
Profile · desktop, Board · mobile, Job detail · mobile, Components & states). This file turns them
into rules; if this file and the mockups disagree, this file wins and the owner updates the mockup.

Style name: **triage board** — a calm, keyboard-friendly review tool. The board is a card grid
for scanning; opening a job switches to a split view (job list left, detail right) for reading
and deciding. The "Job detail · mobile" artboard predates the eligibility decision control;
follow this file.

The board grid, split view, new tokens and decision control (owner decisions 2026-10-19,
ADR-0011) were built in T-026, replacing the list + pane board of T-020. The Profile page
(ADR-0012) is T-028.

## Stack

| Concern | Choice |
|---|---|
| Build | Vite, TypeScript `strict: true` |
| Routing | React Router (data router not required) |
| Server state | TanStack Query (no Redux/Zustand; local UI state with `useState`) |
| API client | `openapi-fetch` with types from `openapi-typescript` (`src/api/schema.d.ts`, generated) |
| UI | Tailwind CSS + shadcn/ui components (copied into `src/components/ui`, restyled with the tokens below), lucide icons |
| Fonts | `@fontsource/ibm-plex-sans` (400/500/600/700) and `@fontsource/ibm-plex-mono` (400/500/600), self-hosted |
| Forms | plain controlled inputs (notes, filters, the profile form; no form library) |
| Lint/format | ESLint (typescript-eslint, react-hooks), Prettier |
| Tests | Vitest + React Testing Library + MSW; Playwright for 5 smoke flows (testing.md) |

Use current stable major versions at bootstrap time and record them in `progress.md`.

## Structure

```
frontend/
  src/
    api/            client.ts (openapi-fetch instance), schema.d.ts (generated), queries.ts (hooks)
    components/ui/  shadcn components (generated, restyled via tokens)
    components/     ScoreBadge, StatusTag, AnalysisTag, DecisionTag, ChecksTag, RuleResultTag, FactChip,
                    StatusActions, SourceLinks, SectionCard, SectionLabel, EmptyState, ErrorState,
                    ListSkeleton, AppHeader, Kbd
    features/board/ BoardPage (route switch), BoardGrid (`/`), FilterBar, MobileFilters, SearchField,
                    StatusLegend, LayoutToggle, JobCard, JobListCard, Pagination, useBoardFilters,
                    useBoardKeyboard, useCardActions, boardLayout, pageItems
    features/job/   JobSplitView, JobListPanel, JobDetailPanel, JobDetail (shared by panel and mobile
                    page), JobDetailPage (mobile), WhyItFits, ScoreBreakdown, EligibilityPanel,
                    EligibilityDecision, FactsPanel, DescriptionPanel, NotesEditor, HistoryList,
                    MobileActionBar, AnalysisActions, useStatusChanger, useDecisionChanger
    features/profile/ ProfilePage, ProfileForm, ChipsInput, ToggleChips, useProfileForm
    features/admin/ AdminPage, KpiRow, SourcesTable, LlmUsageCard, RunsList, FailuresList,
                    CompaniesTable, PipelineBanner, RescoreDialog, LlmModelsCard, BulkActionTable, BulkActionDialog
    features/llm/   LlmAlertBanner, SwitchModelDialog, TierTag, ModelStatusTag
    lib/            format.ts, labels.ts, statusMachine.ts, scoreBand.ts, useMediaQuery.ts
    styles/         tokens.css (CSS variables), index.css
    App.tsx, main.tsx, routes.tsx
  e2e/              Playwright specs
```

## Visual design

### Color tokens

Defined once as CSS variables in `src/styles/tokens.css` and mapped to Tailwind (v4) colors
in `src/index.css` with `@theme inline { --color-ground: var(--ground); … }` (the default
Tailwind palette is cleared, so only token colors exist). Components use only these
names — **no raw hex values in components**. shadcn's own variables (`--background`,
`--primary`, `--border`, `--ring`, …) are set from these tokens.

| Token | Value | Use |
|---|---|---|
| `--ground` | `#F3F3EF` | page background (was `#F6F6F3`) |
| `--ground-2` | `#F7F7F4` | inside the list panel and the detail panel body |
| `--surface-hover` | `#FCFCFA` | card background on hover |
| `--card-border` | `#DCDCD5` | job cards, panels, profile sections |
| `--card-border-hover` | `#B4B4AC` | card border on hover |
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
| `--accent-tint` | `#E0E6F8` | score 70–84, OK rule tags (no longer status tags) |
| `--accent-tint-2` | `#F0F4FC` | selected card background, running banner background |
| `--accent-deep` | `#1F3B89` | text on `--accent-tint`, text links inside cards |
| `--attention-bg` | `#FCEFD9` | "Check N criteria", "Unclear", Partial, warnings |
| `--attention-fg` | `#7A4A00` | text on `--attention-bg` |
| `--attention-icon` | `#B56A00` | warning icon stroke (ErrorState, attention rows) |
| `--attention-row` | `#FFFBF3` | eligibility check rows that are not OK |
| `--eligible-bg` / `--eligible-fg` | `#DDF2EA` / `#13573F` | "Eligible" decision (values changed; were `#E7F0EC` / `#1F5240`) |
| `--ineligible-bg` / `--ineligible-fg` | `#FBE3D6` / `#8A3510` | "Not eligible" decision |
| `--extracted-bg` / `--extracted-fg` | `#E6EEF5` / `#2D4A66` | "Extracted" (analyzed, not scored) |
| `--st-shortlisted-bg` / `-fg` / `-icon` | `#FBE4EF` / `#86204F` / `#C2397E` | Shortlisted tag; filled star |
| `--st-applied-bg` / `-fg` | `#DDEFF7` / `#0B5670` | Applied |
| `--st-interviewing-bg` / `-fg` | `#ECE7FB` / `#4A2FA0` | Interviewing |
| `--st-offer-bg` | `#1D7A4E` | Offer (text `--on-accent`) |
| `--st-withdrawn-bg` / `-fg` | `#E6E6E0` / `#4F545A` | Withdrawn, Dismissed |
| `--strong` | `#3D4147` | "Fail", "Rejected", "Failed" tags (white text) |
| `--maybe-bg` | `#EFEFEA` | score 50–69 badge (text `--ink-2`) |
| `--icon-faint` | `#9A9EA4` | EmptyState icon |
| `--on-accent` | `#FFFFFF` | text on `--accent`, `--strong` and `--ink` (Tailwind `white`) |

`--attention-icon`, `--icon-faint` and `--on-accent` were added in T-019: the spec used
these values inline, and inline hex is not allowed outside `tokens.css`. The text color
`#3D4147` used by StatusTag "New" and FactChip is `--strong`. Rejected keeps `--strong` with
`--on-accent` text. "Check N criteria" uses `--attention-*`.

Color rules:
- One accent hue for actions and scores (primary buttons, score badges, bars, selection, nav).
  Tints are derived from it (white mix 85% / 93%, black mix 35%); if the accent changes,
  recompute all three accent variables.
- Tracking status, analysis state and eligibility decision tags each have their own hue (table
  above), so one look at the grid shows where each job stands.
- Every colored tag has an icon **and** a word; color is never the only signal. This is what
  allows the green "Eligible" / orange-red "Not eligible" pair. Attention = orange family;
  failure = strong neutral. Badges show the number.
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
- Radius: tags 3px, badges/score 4px, inputs and buttons 5px, section cards 6px, dialog 8px,
  job cards 10px (8px in the list panel), panels 12px, section cards inside the detail panel 8px,
  profile sections 12px (as in the mockup), profile pill and mobile filter chips 999px.
- Borders over shadows. Shadows only on: toast `0 8px 24px rgb(0 0 0 / .18)`, dialog
  `0 20px 48px rgb(0 0 0 / .25)`, mobile action bar `0 -6px 16px rgb(0 0 0 / .05)`, job card
  hover `0 10px 24px rgb(27 29 32 / .10)`, selected card
  `0 0 0 1px var(--accent), 0 8px 20px rgb(27 29 32 / .10)`.
- Heights: desktop buttons/inputs 36–38px; icon buttons 32px in list rows (desktop),
  44px minimum touch targets on mobile; mobile action bar buttons 48px.

### Iconography

lucide-react, stroke 1.8–2, size 14–20px, color `--muted` unless the icon carries state
(filled star in `--st-shortlisted-icon` = shortlisted). No emoji. Icon-only buttons need `aria-label`
and a `title` that includes the shortcut, e.g. `title="Shortlist (s)"`.

### Motion

Minimal: a dismissed card fades out (opacity, 150ms ease-out; no height collapse, which would
reflow the grid) before it leaves; toast fades in;
card hover lifts 3px (selected 2px) with a 150ms ease on transform, box-shadow, border-color and
background-color; no page transitions, no decorative animation. No lift and no transition with
`prefers-reduced-motion`. Respect `prefers-reduced-motion` (no
transitions at all).

## Shared components (spec)

| Component | Spec |
|---|---|
| `ScoreBadge` | Mono number in a box. Sizes: `sm` 44×34 on grid/list cards, 40×32 in the list panel, `lg` 68×68 with band word below in 10.5px letter-spaced caps (detail header; mobile 60×60). Band styles from `lib/scoreBand.ts`: EXCELLENT ≥ 85 = `--accent` bg + white; GOOD 70–84 = `--accent-tint` + `--accent-deep`; MAYBE 50–69 = `--maybe-bg` + `--ink-2`; WEAK < 50 = transparent + 1px inset `--control-border` + `--muted`; NONE (not scored, also when analyzed) = "—" with 1px inset `--line` and a `title` ("Not analyzed" / "Extracted, not scored"). |
| `StatusTag` | 12px/600, radius 4px, 12px icon before the word: Shortlisted (star), Applied (send), Interviewing (message), Offer (trophy), Rejected (x), Withdrawn (undo); colors from the token table (`--st-*`; Rejected `--strong` / white; Dismissed like Withdrawn). New = `--line-faint` / `--strong`, shown only in the detail header. Cards show it only when status ≠ NEW. |
| `AnalysisTag` | "Extracted" (`--extracted-*`, file icon) on cards and detail when analyzed and not scored; "Not analyzed" (transparent, 1px inset `--control-border`, `--muted`, clock icon) only in the detail header; "Analysis failed" (`--strong` / white, alert icon) for `FAILED`. Scored jobs show only the ScoreBadge. |
| `DecisionTag` | "Eligible" (`--eligible-*`, circle-check icon) / "Not eligible" (`--ineligible-*`, ban icon). |
| `ChecksTag` | "Check N criteria" / "Check 1 criterion" (`--attention-*`, warning icon) when the decision is undecided and some checks are UNKNOWN or FAIL. Hidden once the owner decides. |
| `RuleResultTag` | OK = `--accent-tint` / `--accent-deep`; Unclear = `--attention-*`; Fail = `--strong` / white; 12px / 600. |
| `FactChip` | `--line-faint` bg (`--neutral-fill` in the detail header), `--strong` text, 12px, radius 3px. |
| Buttons | Primary = `--accent` bg, white; Secondary = white, 1px `--control-border`; Quiet = transparent, `--ink-2`; Leave-app ("Apply") = `--ink` bg, white; Disabled = `--neutral-fill`, `--muted`. **Exactly one primary per view**: the next step of the status flow. Desktop action buttons show their shortcut after the label in mono at 70% opacity. |
| `SectionCard` | `--surface`, 1px `--line`, radius 6px, padding 20/22, starts with a `SectionLabel`. |
| `Toast` | Bottom-left 24px, `--ink` bg, white text, max-width 520px, one at a time, 5 s; a status change toast has "Undo" (outlined `--muted` button) whenever the state machine allows the way back (e.g. no Undo after "Mark applied": APPLIED → NEW is not a transition); a decision toast always has "Undo". Uses shadcn Sonner restyled. |
| `EmptyState` | Centered inbox icon (`--icon-faint`, 32px), bold title, one muted sentence, optional secondary button. Board empty text: "Inbox zero" / "No jobs match these filters." |
| `ErrorState` | `--ground` box, 1px `--line`, warning icon `--attention-icon`, bold title, `detail` from Problem Details, and `status · code` in mono 12px muted. Network failure variant: "Backend not reachable — Is the app running?". |
| `ListSkeleton` | Rows with a 40×32 `--neutral-fill` block and three bars (12px, 10px, 10px) in `--neutral-fill`/`--line-faint`; no shimmer if reduced motion. |
| `Kbd` | Mono 12px, 1px `--control-border` (2px bottom), radius 4px, `--surface-muted` bg. |
| `JobCard`, `JobListCard`, `Pagination`, `LayoutToggle`, `StatusLegend` | See "`/` — Board grid" below. |
| `EligibilityDecision` | See "`JobDetail` content order" below. |

## Layout

### App header (all desktop pages)
Height 56px, `--surface`, bottom border `--line`, padding 0 24px. Left: logo icon (briefcase,
`--accent`) + "Job Hunter" 15px/700. Nav links Board / Profile / Admin: active link 600 weight with a
2px `--accent` bottom indicator (`box-shadow: inset 0 -2px 0`), inactive `--muted`. Right:
mono status text "Last run 07:00 · 42 new · 9 scored" (from `GET /api/status`) and the
profile pill (22px circle in `--accent` with the profile's initial + profile id; links to
`/profile`). Wraps on narrow screens.

### LLM alert banner (all pages, ADR-0008)
Rendered directly under the app header when `GET /api/status` returns `llmAlerts`
(not yet in the mockups; follow this spec). One row per alert, full width, padding
10px 24px (16px mobile), bottom border `--line`:
- EXHAUSTED / ERROR / paused: `--attention-bg` background, `--attention-fg` text, warning
  icon. NEAR_LIMIT: `--surface-muted` background, `--ink-2` text, gauge icon.
- Text (one line, wraps on mobile): **"Extraction paused"** · "GPT-OSS 120B (Groq) reached its
  daily limit (1000/1000)" · mono "resets 02:00" · "23 jobs waiting". For NEAR_LIMIT:
  "Scoring model at 81% of today's limit".
- Actions right-aligned: "Switch model" (secondary button, opens `SwitchModelDialog` for
  that purpose), "Details" text link to `/admin#llm`, dismiss icon button (hides this exact
  alert for the session).

### `SwitchModelDialog`
shadcn Dialog, max-width 560px. Title "Model for Extraction" / "Model for Scoring". Radio
list rows: label (600) + provider (muted) · tier tag (FREE = `--accent-tint`, LOCAL =
`--line-faint`, PAID = `--attention-bg` with text "PAID") · status tag · mono usage
"812 / 1000 today" (or "no known limit") · last eval line in muted 12.5px ("Eval Oct 25:
false-eligible 2.4%, HIGH recall 83%" or "Not evaluated yet"). Unusable models are disabled
with the reason in muted text. Below: for a PAID
selection an attention box "Paid model — counts against the $5.00 monthly budget". Footer:
Cancel (secondary), Save (primary).

### `/` — Board grid
- Under the header, the **filter bar** (`--surface-muted`, bottom border, padding 12px 24px,
  wraps): search field (min 240px, with `/` kbd hint), one dropdown button per filter showing
  "Label **value**": Status (default "All but dismissed"), Analysis, Decision (default "Not marked
  ineligible"), Checks, Score, Source, Type, Posted; "Reset" as an underlined text button; right
  side: `LayoutToggle` (Grid | List icon buttons with `aria-pressed`, stored in `localStorage`
  `board.layout`, try/catch, default Grid) and "Sort: Score". The job count is not in the bar;
  `Pagination` shows it.
- Content: max-width 1320px, centered, padding 20px 24px 32px, gap 16px.
- `StatusLegend`: one row of all status, analysis and decision tags (12.5px "Legend" label).
- Grid: `repeat(3, minmax(0,1fr))` ≥ 1100px, 2 columns 700–1099px, 1 column below; gap 16px.
  List layout: one column of the same jobs as `JobListCard`s, gap 10px.
- 12 jobs per page. `Pagination` at the bottom: mono "Showing 13–24 of 30 jobs" left; previous,
  numbered pages (current = `--ink` background, white, `aria-current="page"`), next (disabled
  at the ends). `page` is a 1-based URL param.
- `JobCard`: `--surface`, 1px `--card-border`, radius 10px, padding 16px 14px 12px 16px,
  min-height 216px, column gap 10px. Row 1: ScoreBadge sm, spacer, StatusTag. Title h3
  15px/600, 2-line clamp, min-height 2.7em, rendered as a link (`<a href="/jobs/:id?…">`, so it
  can open in a new tab; "links are `<a href>`") whose `::after` covers the whole card (one click
  target; icon buttons sit above it with `position: relative; z-index: 1`).
  "company · via sources" 13px `--muted`, one line. FactChips. Footer (top border
  `--line-faint`, pushed to the bottom): AnalysisTag / DecisionTag / ChecksTag, spacer, posted
  time (mono 12px), star and dismiss icon buttons 32×32. Hover: lift, hover shadow,
  `--card-border-hover`, `--surface-hover`. Focus within: border `--accent`. Cards do not show
  analysis buttons (those live in the detail action row and the mobile action bar).
- Clicking a card navigates to `/jobs/:id` keeping the search params (incl. `page`).
- A `page` past the end (e.g. after the last card of the last page was dismissed) shows the last
  page.

### `/jobs/:id` — split view (desktop ≥ 1024px)
- The page does not scroll: app root `height: 100dvh`, header on top, below it a flex row
  (gap 20px, padding 20px 24px 24px, `min-height: 0`) with two panels. Both panels: `--surface`,
  1px `--card-border`, radius 12px, `overflow: hidden`, own vertical scroll
  (`overflow-y: auto; overscroll-behavior: contain`).
- `JobListPanel` (400px): header (`--surface-muted`, bottom border): "All jobs" secondary button
  with a chevron (back to `/` with the params and the page that contains the job), mono
  "24 of 30", and a filter input bound to `q`. Scroll area: `--ground-2`, padding 12px, gap
  10px, `JobListCard`s. At the end: a dashed full-width button "Load 12 more · 24 of 30" that
  fetches the next page and **appends** it; the scroll position must not change (stable keys by
  job id, no scroll-to-bottom, no remount). "All N jobs loaded" when finished. On entry the list
  contains every page up to the grid page the job came from, and the selected card is scrolled
  into view (`block: 'nearest'`). The URL's `page` follows the selected job (the grid page that
  contains it), so "All jobs", `Esc` and a reload land on the right page. Footer (mono 11.5px):
  `j/k move · s shortlist · d dismiss · e eligible · n not eligible · Esc back`.
- `JobListCard`: like JobCard, horizontal: ScoreBadge 40×32 · title (14px/600, 2-line clamp,
  stretched button) · "company · posted" · tags row (StatusTag, AnalysisTag/DecisionTag/
  ChecksTag) · star button. Radius 8px, padding 12px 8px 12px 12px. Selected:
  `--accent-tint-2` background, `--accent` border ring, `aria-current="true"`.
- `JobDetailPanel`: background `--ground-2`. Sticky header (`--surface`, bottom border, padding
  18px 24px 16px): ScoreBadge lg, title, meta line, tags (StatusTag incl. New, AnalysisTag,
  DecisionTag or ChecksTag, FactChips), then the action row. Body: padding 20px 24px 32px, gap
  16px, section cards (`--surface`, 1px `--line`, radius 8px, padding 18px 20px). Selecting
  another job resets the detail scroll to the top; the list keeps its scroll.

### `JobDetail` content order (detail panel and mobile page)
1. Header (sticky in the panel): ScoreBadge lg · title (h1) · "company · location · posted X ago" ·
   tags (StatusTag incl. New, AnalysisTag, DecisionTag or ChecksTag, FactChips).
2. Action row: StatusActions from `allowedTransitions` (primary first, then secondary, then quiet
   Dismiss), AnalysisActions (secondary), spacer, "Open posting ↗" (secondary link, new tab),
   "Apply" (ink link, new tab, goes to `applyUrl`).
3. "Not analyzed yet" dashed card with an Analyze button (NOT_ANALYZED only; on mobile the
   button is in the action bar instead). Both Analyze buttons of a job share the running state.
4. Card "Why it fits" (scored only): summary (16px), "Khi apply:"-style advice line labelled with
   the localized word for the profile language (vi: "Khi apply:", en: "If applying:"), then a
   3-column auto-fit grid: Strengths (check icon `--accent`), Gaps (minus icon `--muted`),
   Red flags (warning icon, `--attention-fg` text; "None found" when empty).
5. Card **Eligibility** (full width): section label, the line "The app checks each criterion. The
   overall decision is yours.", mono counts "6 OK · 1 unclear · 0 fail"; the checks table (label
   112px `--muted` · value · RuleResultTag; rows not OK on `--attention-row`); before analysis a
   muted box "Criteria appear after analysis. You can still decide now from the description.";
   then the decision row: "Your decision" · `EligibilityDecision` · hint ("Set by you · <time>",
   "Review the highlighted criteria, then decide.", or "All criteria OK. Confirm if you agree.").
6. Card "Score breakdown" (scored only): label 96px · bar 8px high in `--neutral-fill` with
   `--accent` fill · "value/max" mono; footer: prompt version and model in mono muted (once the
   API exposes them), total right.
7. Card "Extracted facts" (analyzed only): `<dl>` in an auto-fit grid (min 260px), each pair label 130px.
8. Card "Description": header row with "Sources: via X" links; text collapsed to ~6 lines
   with a fade mask and a "Show full description" text button.
9. Two-column auto-fit grid: Notes (`<label>` + textarea 5 rows + hint "Autosaves as you
   type" → "Saved · just now", mono 12px) and History (time mono 12.5px muted · text; status and
   decision events).

`EligibilityDecision`: segmented control (1px `--control-border`, radius 6px) with three
`<button aria-pressed>`: "Eligible" (circle-check, key `e`), "Not eligible" (ban, key `n`),
"Undecided". Pressed styles: `--eligible-*`, `--ineligible-*`, `--neutral-fill` / `--ink`.

### AnalysisActions (ADR-0010)
Two owner actions, one job at a time. Shown in the detail action row and the mobile action bar
only (not on cards).
- `NOT_ANALYZED` → **Analyze** (secondary, `ScanSearch` icon). Calls `POST /api/jobs/{id}/analyze`.
- `ANALYZED` and decision ≠ INELIGIBLE → **Score** (secondary, `Gauge` icon).
  Calls `POST /api/jobs/{id}/score`.
- Otherwise hidden. A scored job shows only its score.
- While the request runs: spinner, button disabled, text "Analyzing…" or "Scoring…".
- Paused purpose (`LlmAlert.paused` for that purpose): button disabled, with "Model paused" and a
  "Switch model" link that opens `SwitchModelDialog` for that purpose (until T-023 the link goes to `/admin#llm`).
- Success updates the job in the list and detail caches (`['jobs', …]`, `['job', id]`).
  409 `BUDGET_REACHED` shows an error toast with the problem `detail`.
- These buttons are secondary, so the one-primary-per-view rule is unchanged.

### Mobile (< 1024px)
- `/` = one column of `JobListCard`s (radius 10px, page padding 12px, gap 10px) with tags, star
  and dismiss, and the same numbered `Pagination` (44px targets); the mono
  count and the search icon button (opens a full-width search field) sit at the start of the filter
  row, under the shared app header. Header 56px: logo, page name, menu icon button (links Board /
  Profile / Admin). Filter bar becomes a
  horizontally scrolling row: black "Filters · N" pill (opens a bottom sheet with all
  filters) followed by pills of the active filter values.
- Cards: title clamped to 2 lines, icon buttons 44×44. Tapping a card navigates to `/jobs/:id`.
- `/jobs/:id` = full page: top bar with back chevron (returns to `/` with filters kept),
  "Job N of M" (from the cached grid page), "Posting ↗"; then `JobDetail` with cards stacked (padding 16px; the Eligibility
  card contains `EligibilityDecision`), and a fixed
  **bottom action bar** (white, top border, shadow): Dismiss icon button 48×48, secondary
  action (the analysis action when it applies, see AnalysisActions), primary action (flex 1 each),
  safe-area padding at the bottom. Hide the desktop
  action row on mobile.
- Toast sits above the bottom action bar / at the bottom of the list, inset 12px.
- `/profile` = one column.

### `/profile` (ADR-0012)
- Container max-width 960px, centered, padding 24px, column gap 16px. No profile list.
- Header card (radius 12px): h1 "Your profile" + mono profile id; mono line "Saved <time> · hash
  <first 6> · config/profile.yaml"; dirty tag ("Unsaved changes" `--attention-*` / "No changes"
  neutral); Discard (quiet); Save changes (primary, disabled while clean or invalid).
- Error banner (`role="alert"`, attention colors): "Fix N field(s) to save" + mono list of
  `path: message`.
- Info box: "Saving re-runs the prefilter and the eligibility criteria for this profile (no LLM
  calls). Scores are kept; re-score from Admin when you want them updated."
- Sections (cards, radius 12px, title 16px/600 + muted one-line description), fields in a
  2-column grid (1 column < 760px). Each label shows the YAML path in mono 11.5px `--muted`.
  Candidate (headline wide, years, residence, city, timezone, summary language, languages as
  chips, summary textarea wide); Skills and domains (core wide, secondary, learning, domains
  wide); Preferences (employment types and seniority as `ToggleChips`, accepted regions chips,
  max hours, timezone difference, minimum salary, exclude companies); Prefilter (title include
  and exclude regex chips, max age, min skill hits); Notifications (digest minimum score).
- Invalid field: 1px `--attention-icon` border + ring, hint text in `--attention-fg`.

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
- **LLM models card** (`id="llm"`, full width, placed right after the KPI row so problems
  are seen first):
  - Two assignment blocks side by side (auto-fit, min 360px): "Extraction" and "Scoring". Each:
    model label + tier tag + status tag, mono usage meter "812 / 1000 requests today"
    (meter fill `--accent`, turns `--attention-fg` at ≥ 80%), "resets 02:00" when known,
    toggles "Auto-switch to fallback" and "Allow paid fallback" (shadcn Switch), fallback
    order as small chips, and a "Change model" secondary button (opens `SwitchModelDialog`).
  - Models table: Model, Provider, Tier, Status (+ detail), Today (calls · tokens · cost),
    Limits, Last eval, "Test" button. Rows with `keyConfigured=false` show "Add
    GROQ_API_KEY to .env" (name from the registry) in muted text.
  - Switch history (last 10): time · purpose · from → to · reason (User / Auto-fallback).
- Companies table (Company, ATS, Board slug mono, Check tag, Active jobs, Checked) with the
  hint "Edit list in config/companies.yaml".
- **Bulk actions** (ADR-0010): a table of jobs with a segmented control "Analyze | Score".
  Analyze mode lists NOT_ANALYZED jobs. Score mode lists ANALYZED jobs that are not scored and
  not marked INELIGIBLE. Columns: checkbox, title, company, DecisionTag + ChecksTag, analysis
  tag, posted. The header checkbox selects the visible page (max
  200). Above the table: mono "N selected", a Clear button, and a secondary button "Analyze
  selected…" or "Score selected…" (matching the mode, disabled at 0), which opens
  `BulkActionDialog`.
- `BulkActionDialog`: shadcn Dialog, max-width 460px; title "Analyze N jobs?" or "Score N
  jobs?"; mono counts for will run, skipped (with reasons) and estimated calls and cost;
  Cancel (secondary) and "Analyze N jobs" or "Score N jobs" (primary).
- `RescoreDialog`: shadcn Dialog, max-width 460px; title "Re-score active jobs?", text with
  mono job count, a `--ground` box "Estimated cost ≈ $x", Cancel (secondary) and
  "Re-score N jobs" (primary).
- Source/company/run status tags: OK = accent tint; Partial, Budget, Not found n/3 =
  attention; Failed, Error = strong; Skipped, Off = neutral.

## Behavior

### Board
- Filters, `page` and the selected job live in the URL so views are bookmarkable; the layout
  (Grid/List) lives in `localStorage`. "Reset" returns to the default view (api-spec). Search is
  debounced 300 ms and resets `page` to 1.
- Grid: `useJobs(filters, page)` with `size=12`. List panel: `useInfiniteJobs(filters)` with
  `size=12`, seeded with pages 1…`page`; "Load more" appends.
- Status actions: only transitions allowed for the status (`lib/statusMachine.ts` mirrors
  data-model §2 and is covered by a test; the detail uses the server's `allowedTransitions`).
- Optimistic status update with rollback on error (error toast with the problem `detail`).
  Dismiss: in the grid the card collapses and the page refills on refetch; in the split view
  the next card is selected (or the previous); Undo toast.
- Eligibility decision: `useSetDecision()`, optimistic on list and detail caches, rollback +
  error toast on failure, Undo toast ("Marked eligible", "Marked not eligible", "Decision
  cleared"). A job marked Not eligible keeps its card until the next refetch, then leaves the
  default view.
- Keyboard (`useBoardKeyboard`, ignored while focus is in an input/textarea/select or a dialog
  is open; on the grid the keys act on the focused card, in the split view on the selected job):
  `j`/`k` next/previous card, `Enter` on a focused card opens it, `s` shortlist toggle,
  `d` dismiss, `a` mark applied, `e` mark eligible (pressing it again clears), `n` mark not
  eligible (again clears), `o` open original posting (on the grid only once the job's detail
  was loaded: summaries carry no posting URL), `/` focus search, `Esc` blur search if
  focused, else in the split view go back to the grid.
- The selected card is scrolled into view on keyboard moves.
- Pagination: numbered pages on the grid, "Load more" in the list panel.

### Job detail
- Notes autosave 1 s after typing stops; show "Saved · just now" after success.
- Description collapsed by default per job; expanding is remembered while the job is open.

### Profile
- Draft in local state; Discard restores the loaded profile.
- Save → `PUT /api/profile` with `baseHash`; on success invalidate `['profile']`, `['jobs']`,
  `['job']`, toast "Profile saved · eligibility re-checked for N jobs".
- 400 → map `errors[].path` to fields + banner; 409 `PROFILE_CHANGED` → ErrorState with "Reload".
- Leaving with unsaved changes asks for confirmation.

### LLM alerts and switching
- `useStatus()` polls `GET /api/status` every 60 s, every 10 s while any alert is shown.
- Saving in `SwitchModelDialog` invalidates `['status']`, `['admin','llm']`,
  `['admin','overview']`; toast "Extraction now uses Nemotron 3 Super 120B (OpenRouter, free)".
- Model status tags: Available = accent tint "Available"; Near limit = attention "Near limit";
  Rate limited = attention "Rate limited · 1 min"; Exhausted = strong "Exhausted until 02:00";
  Error = strong "Error". Words always present.

### Admin
- Overview refetch every 3 s while `pipelineRunning`, else every 60 s.
- Run → 202 → banner; 409 → toast "Pipeline is already running".
- Re-score and bulk actions: dry run first; the dialog must show the count, and for bulk actions the skipped counts, before executing.

## Rules

- All data access through hooks in `src/api/queries.ts`; components never call `fetch`.
- Never hand-edit `schema.d.ts`; run `npm run gen:api`.
- Loading, empty and error states for every query (shared components above).
- Only token names in styles; a new color needs a new token in this file first.
- Accessibility: buttons are `<button>`, links are `<a href>`, labels for inputs, visible
  focus ring (2px `--accent` outline, offset 2px), color never the only signal, icon-only
  buttons have `aria-label`, text contrast ≥ 4.5:1.
- No external analytics, no fonts/scripts from third-party CDNs (works offline).
- UI text in English. Job summaries and apply advice arrive in the profile's language from
  the backend and are rendered with that `lang`.
- Responsive breakpoint for split view vs mobile: 1024px (`lib/useMediaQuery.ts`).
