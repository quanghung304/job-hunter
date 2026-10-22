import { useMemo, useState } from 'react'
import type { FieldProblem } from '@/api/client'
import type { ProfileData } from '@/api/queries'

/** Region tokens of config-spec §1.1 (backend `RegionMatcher.TOKEN_REGEX`). */
export const REGION_TOKEN = /^(WORLDWIDE|EU|EEA|EUROPE|EMEA|AMERICAS|NORTH_AMERICA|LATAM|APAC|ASIA|[A-Z]{2})$/

/** A field's key: its YAML path without list indexes, e.g. `preferences.acceptRegions`. */
export function fieldKey(path: string): string {
  return path.replace(/\[\d+\]/g, '')
}

/** Whether a problem belongs to the field: same key, or a part of it (`candidate.languages[0].code`). */
function belongsTo(path: string, key: string): boolean {
  const own = fieldKey(path)
  return own === key || own.startsWith(`${key}.`)
}

const blank = (value: string | null | undefined) => value === null || value === undefined || value.trim() === ''
const missing = (value: number | null | undefined) => value === null || value === undefined || Number.isNaN(value)

/**
 * Required fields and ranges checked before saving (config-spec §1); the server checks everything
 * again with the startup rules (regexes, time zone, …). Empty number inputs are `NaN` in the draft.
 */
export function clientProblems(p: ProfileData): FieldProblem[] {
  const problems: FieldProblem[] = []
  const add = (path: string, message: string) => problems.push({ path, message })
  const c = p.candidate
  if (blank(c.headline)) add('candidate.headline', 'is required')
  if (missing(c.yearsExperience)) add('candidate.yearsExperience', 'is required')
  else if (c.yearsExperience < 0) add('candidate.yearsExperience', 'must be 0 or more')
  if (!/^[A-Z]{2}$/.test(c.residenceCountry)) {
    add('candidate.residenceCountry', 'must be an ISO-3166 alpha-2 code, e.g. IT')
  }
  if (blank(c.timezone)) add('candidate.timezone', 'is required')
  if (c.languages.length === 0) add('candidate.languages', 'add at least one language')
  if (blank(c.summary)) add('candidate.summary', 'is required')
  if (c.skills.core.length === 0) add('candidate.skills.core', 'add at least one core skill')

  const pr = p.preferences
  if (pr.employmentTypes.length === 0) add('preferences.employmentTypes', 'pick at least one')
  if (!missing(pr.maxHoursPerWeek) && ((pr.maxHoursPerWeek as number) < 1 || (pr.maxHoursPerWeek as number) > 168)) {
    add('preferences.maxHoursPerWeek', 'must be between 1 and 168')
  }
  if (pr.acceptRegions.length === 0) add('preferences.acceptRegions', 'add at least one region')
  pr.acceptRegions.forEach((region, i) => {
    if (!REGION_TOKEN.test(region)) {
      add(`preferences.acceptRegions[${i}]`, 'must be a region token or ISO-3166 alpha-2 code')
    }
  })
  const offset = pr.timezone.maxOffsetDiffHours
  if (missing(offset) || offset < 0 || offset > 26) {
    add('preferences.timezone.maxOffsetDiffHours', 'must be between 0 and 26')
  }
  if (pr.minSalary) {
    if (missing(pr.minSalary.amount) || pr.minSalary.amount <= 0)
      add('preferences.minSalary.amount', 'must be positive')
    if (!/^[A-Z]{3}$/.test(pr.minSalary.currency)) {
      add('preferences.minSalary.currency', 'must be an ISO-4217 code, e.g. EUR')
    }
  }
  if (pr.seniority.length === 0) add('preferences.seniority', 'pick at least one')

  const f = p.prefilter
  if (missing(f.maxAgeDays) || f.maxAgeDays < 1) add('prefilter.maxAgeDays', 'must be 1 or more')
  if (f.titleInclude.length === 0) add('prefilter.titleInclude', 'add at least one pattern')
  if (missing(f.minSkillHits) || f.minSkillHits < 1) add('prefilter.minSkillHits', 'must be 1 or more')

  const score = p.notify.digestMinScore
  if (missing(score) || score < 0 || score > 100) add('notify.digestMinScore', 'must be between 0 and 100')
  return problems
}

/**
 * The Profile page's draft (frontend.md "Behavior › Profile"): local copy of the loaded profile,
 * dirty flag, client checks, and the server's `errors[]` mapped to field keys. A field's server
 * error goes away once that field is edited.
 */
export function useProfileForm(loaded: ProfileData) {
  const [draft, setDraft] = useState<ProfileData>(loaded)
  const [server, setServer] = useState<FieldProblem[]>([])

  const dirty = useMemo(() => JSON.stringify(draft) !== JSON.stringify(loaded), [draft, loaded])
  const client = useMemo(() => clientProblems(draft), [draft])

  /** Updates the draft; `key` is the field being edited (its server error is cleared). */
  const change = (key: string, update: (draft: ProfileData) => ProfileData) => {
    setDraft(update)
    setServer((current) => current.filter((problem) => !belongsTo(problem.path, key)))
  }

  /** Problems shown: client checks, plus server problems not covered by a client one. */
  const problems = useMemo(() => {
    const paths = new Set(client.map((problem) => problem.path))
    return [...client, ...server.filter((problem) => !paths.has(problem.path))]
  }, [client, server])

  const errorOf = (key: string): string | undefined => {
    const problem = problems.find((p) => belongsTo(p.path, key))
    return problem ? problem.message : undefined
  }

  return {
    draft,
    dirty,
    valid: client.length === 0,
    problems,
    errorOf,
    change,
    discard: () => {
      setDraft(loaded)
      setServer([])
    },
    setServerProblems: setServer,
  }
}

export type ProfileForm = ReturnType<typeof useProfileForm>
