# Prompts

- One file per prompt version: `<purpose>-v<n>.md`. A released version is **never edited**;
  create the next version instead (AGENTS.md hard rule).
- Released = referenced by `application.yml` or by any stored `prompt_version`.
- Packaged into the jar as `classpath:prompts/` (see conventions).

## File format

```
---
id: extract-v1
purpose: EXTRACT          # EXTRACT | SCORE
maxOutputTokens: 1200
temperature: 0
---
## system
...system prompt...

## user
...user template with {{placeholders}}...
```

- Front matter is YAML; `## system` and `## user` headings split the two parts exactly.
- Placeholders: `{{name}}`. Rendering fails if a placeholder has no value (no silent blanks).
- Values are inserted verbatim inside XML-style tags in the template, e.g.
  `<description>{{description}}</description>`; the renderer escapes `</` sequences in values
  to prevent tag breaking.
- The model must answer with a single JSON object; the parser extracts the first top-level
  JSON object from the response text and validates it.

## Versions

| Id | Status | Notes |
|---|---|---|
| extract-v1 | released (T-011) | referenced by `app.llm.extract-prompt`; never edit, create extract-v2 |
| score-v1 | released (T-012) | was the default until T-025; stored `prompt_version` values keep it released; never edit |
| score-v2 | released (T-025) | referenced by `app.llm.score-prompt`; eligibility checks instead of a verdict (ADR-0011): variable `eligibilityChecks` replaces `eligibility` and `eligibilityReasons`; eval pending (T-016) |
| test-v1 | released (T-022) | admin "Test" of one model (`POST /api/admin/llm/models/{id}/test`, purpose TEST); answer `{"ok":true}`; not part of the eval |
