import { X } from 'lucide-react'
import { type ReactNode, useState } from 'react'
import type { ProfileData } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { cn } from 'cn'
import { EMPLOYMENT_LABELS, type EmploymentType, SENIORITY_LABELS } from '@/lib/labels'
import { ChipsInput } from './ChipsInput'
import { Field } from './Field'
import { inputClass } from './inputClass'
import { ToggleChips } from './ToggleChips'
import type { ProfileForm as Form } from './useProfileForm'

type Candidate = ProfileData['candidate']
type Preferences = ProfileData['preferences']
type Prefilter = ProfileData['prefilter']
type Language = Candidate['languages'][number]
type Seniority = Preferences['seniority'][number]
type SalaryPeriod = NonNullable<Preferences['minSalary']>['period']

const LEVELS: Language['level'][] = ['NATIVE', 'C2', 'C1', 'B2', 'B1', 'A2', 'A1']
const EMPLOYMENT_OPTIONS = (Object.keys(EMPLOYMENT_LABELS) as EmploymentType[]).map((value) => ({
  value,
  label: EMPLOYMENT_LABELS[value],
}))
const SENIORITY_OPTIONS: { value: Seniority; label: string }[] = (
  ['JUNIOR', 'MID', 'SENIOR', 'LEAD', 'PRINCIPAL'] as Seniority[]
).map((value) => ({ value, label: SENIORITY_LABELS[value] }))
const PERIODS: { value: SalaryPeriod; label: string }[] = [
  { value: 'HOUR', label: 'per hour' },
  { value: 'MONTH', label: 'per month' },
  { value: 'YEAR', label: 'per year' },
]

/** IANA zones for the time zone suggestions (empty where the browser cannot list them). */
const TIME_ZONES: string[] = (() => {
  try {
    return Intl.supportedValuesOf('timeZone')
  } catch {
    return []
  }
})()

/** Number input text → draft value: empty is NaN (required) or null (optional). */
const required = (text: string) => (text.trim() === '' ? Number.NaN : Number(text))
const optional = (text: string) => (text.trim() === '' ? null : Number(text))
const shown = (value: number | null | undefined) =>
  value === null || value === undefined || Number.isNaN(value) ? '' : String(value)

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <section className="rounded-panel border border-card-border bg-surface px-6 py-5 max-lg:px-4">
      <h2 className="m-0 text-[16px] font-semibold text-ink">{title}</h2>
      <p className="mt-0.5 mb-4 text-[13px] text-muted">{description}</p>
      <div className="grid grid-cols-1 gap-x-5 gap-y-4 min-[760px]:grid-cols-2">{children}</div>
    </section>
  )
}

/** Languages as chips "en · B2"; a code input and a level select add one. */
function LanguagesInput({
  id,
  values,
  onChange,
  invalid,
}: {
  id: string
  values: Language[]
  onChange: (values: Language[]) => void
  invalid: boolean
}) {
  const [code, setCode] = useState('')
  const [level, setLevel] = useState<Language['level']>('B2')
  const add = () => {
    const value = code.trim().toLowerCase()
    if (value === '' || values.some((l) => l.code === value)) return
    onChange([...values, { code: value, level }])
    setCode('')
  }
  return (
    <div className="flex flex-col gap-2">
      {values.length ? (
        <ul className="m-0 flex list-none flex-wrap gap-1.5 p-0" aria-label="Your languages">
          {values.map((language, i) => (
            <li
              key={language.code}
              className="num inline-flex h-7 items-center gap-1 rounded-tag bg-line-faint pr-0.5 pl-2 text-[12.5px] text-strong"
            >
              {language.code} · {language.level}
              <button
                type="button"
                aria-label={`Remove ${language.code}`}
                onClick={() => onChange(values.filter((_, j) => j !== i))}
                className="inline-flex size-5 items-center justify-center rounded-tag text-muted hover:bg-neutral-fill max-lg:size-7"
              >
                <X className="size-3" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="flex gap-2">
        <input
          id={id}
          value={code}
          placeholder="code, e.g. en"
          aria-invalid={invalid || undefined}
          onChange={(event) => setCode(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              add()
            }
          }}
          className={inputClass(invalid, 'num w-28')}
        />
        <select
          aria-label="Level"
          value={level}
          onChange={(event) => setLevel(event.target.value as Language['level'])}
          className={inputClass(false, 'w-28')}
        >
          {LEVELS.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
        <Button variant="secondary" onClick={add} disabled={code.trim() === ''}>
          Add
        </Button>
      </div>
    </div>
  )
}

/**
 * The profile as editable sections (frontend.md "/profile"): Candidate, Skills and domains,
 * Preferences, Prefilter, Notifications. Every label shows the YAML path; `id` is not editable.
 */
export function ProfileForm({ form }: { form: Form }) {
  const { draft, errorOf, change } = form
  const c = draft.candidate
  const p = draft.preferences
  const f = draft.prefilter

  const candidate = (key: keyof Candidate, value: Candidate[keyof Candidate]) =>
    change(`candidate.${key}`, (d) => ({ ...d, candidate: { ...d.candidate, [key]: value } }))
  const skills = (key: keyof Candidate['skills'], value: string[]) =>
    change(`candidate.skills.${key}`, (d) => ({
      ...d,
      candidate: { ...d.candidate, skills: { ...d.candidate.skills, [key]: value } },
    }))
  const preferences = (key: keyof Preferences, value: Preferences[keyof Preferences]) =>
    change(`preferences.${key}`, (d) => ({ ...d, preferences: { ...d.preferences, [key]: value } }))
  const prefilter = (key: keyof Prefilter, value: Prefilter[keyof Prefilter]) =>
    change(`prefilter.${key}`, (d) => ({ ...d, prefilter: { ...d.prefilter, [key]: value } }))
  const salary = (key: 'amount' | 'currency' | 'period', value: number | string) =>
    change(`preferences.minSalary.${key}`, (d) => {
      const current = d.preferences.minSalary
      if (!current) return d
      return { ...d, preferences: { ...d.preferences, minSalary: { ...current, [key]: value } } }
    })

  /** aria attributes linking a control to its error or hint. */
  const aria = (id: string, key: string) => ({
    'aria-invalid': errorOf(key) ? true : undefined,
    'aria-describedby': errorOf(key) ? `${id}-error` : undefined,
  })

  return (
    <div className="flex flex-col gap-4">
      <Section title="Candidate" description="Who you are. Headline and summary are sent to the LLM (no contact data).">
        <Field id="headline" label="Headline" path="candidate.headline" error={errorOf('candidate.headline')} wide>
          <input
            id="headline"
            value={c.headline}
            onChange={(e) => candidate('headline', e.target.value)}
            className={inputClass(!!errorOf('candidate.headline'))}
            {...aria('headline', 'candidate.headline')}
          />
        </Field>
        <Field
          id="years"
          label="Years of experience"
          path="candidate.yearsExperience"
          error={errorOf('candidate.yearsExperience')}
        >
          <input
            id="years"
            type="number"
            min={0}
            step={0.5}
            inputMode="decimal"
            value={shown(c.yearsExperience)}
            onChange={(e) => candidate('yearsExperience', required(e.target.value))}
            className={inputClass(!!errorOf('candidate.yearsExperience'), 'num')}
            {...aria('years', 'candidate.yearsExperience')}
          />
        </Field>
        <Field
          id="residence"
          label="Country of residence"
          path="candidate.residenceCountry"
          error={errorOf('candidate.residenceCountry')}
          hint="ISO code, e.g. IT"
        >
          <input
            id="residence"
            value={c.residenceCountry}
            maxLength={2}
            onChange={(e) => candidate('residenceCountry', e.target.value.toUpperCase())}
            className={inputClass(!!errorOf('candidate.residenceCountry'), 'num uppercase')}
            {...aria('residence', 'candidate.residenceCountry')}
          />
        </Field>
        <Field
          id="city"
          label="City"
          path="candidate.city"
          error={errorOf('candidate.city')}
          hint="Keeps on-site and hybrid jobs in your city"
        >
          <input
            id="city"
            value={c.city ?? ''}
            onChange={(e) => candidate('city', e.target.value === '' ? null : e.target.value)}
            className={inputClass(false)}
          />
        </Field>
        <Field id="timezone" label="Time zone" path="candidate.timezone" error={errorOf('candidate.timezone')}>
          <input
            id="timezone"
            list="time-zones"
            value={c.timezone}
            onChange={(e) => candidate('timezone', e.target.value)}
            className={inputClass(!!errorOf('candidate.timezone'), 'num')}
            {...aria('timezone', 'candidate.timezone')}
          />
          <datalist id="time-zones">
            {TIME_ZONES.map((zone) => (
              <option key={zone} value={zone} />
            ))}
          </datalist>
        </Field>
        <Field id="summary-language" label="Summary language" path="summaryLanguage" error={errorOf('summaryLanguage')}>
          <select
            id="summary-language"
            value={draft.summaryLanguage}
            onChange={(e) =>
              change('summaryLanguage', (d) => ({
                ...d,
                summaryLanguage: e.target.value as ProfileData['summaryLanguage'],
              }))
            }
            className={inputClass(false)}
          >
            <option value="vi">Vietnamese (vi)</option>
            <option value="en">English (en)</option>
          </select>
        </Field>
        <Field id="languages" label="Languages" path="candidate.languages" error={errorOf('candidate.languages')} wide>
          <LanguagesInput
            id="languages"
            values={c.languages}
            onChange={(values) => candidate('languages', values)}
            invalid={!!errorOf('candidate.languages')}
          />
        </Field>
        <Field id="summary" label="Summary" path="candidate.summary" error={errorOf('candidate.summary')} wide>
          <Textarea
            id="summary"
            rows={7}
            value={c.summary}
            onChange={(e) => candidate('summary', e.target.value)}
            className={cn(
              'field-sizing-fixed bg-surface text-[14px] md:text-[14px]',
              errorOf('candidate.summary') && 'border-attention-icon ring-2 ring-attention-bg',
            )}
            {...aria('summary', 'candidate.summary')}
          />
        </Field>
      </Section>

      <Section
        title="Skills and domains"
        description="Core skills drive the score most; the prefilter counts them too."
      >
        <Field id="core" label="Core skills" path="candidate.skills.core" error={errorOf('candidate.skills.core')} wide>
          <ChipsInput
            id="core"
            values={c.skills.core}
            onChange={(values) => skills('core', values)}
            placeholder="Type a skill and press Enter"
            invalid={!!errorOf('candidate.skills.core')}
            describedBy={errorOf('candidate.skills.core') ? 'core-error' : undefined}
          />
        </Field>
        <Field
          id="secondary"
          label="Secondary skills"
          path="candidate.skills.secondary"
          error={errorOf('candidate.skills.secondary')}
        >
          <ChipsInput id="secondary" values={c.skills.secondary} onChange={(values) => skills('secondary', values)} />
        </Field>
        <Field
          id="learning"
          label="Learning"
          path="candidate.skills.learning"
          error={errorOf('candidate.skills.learning')}
        >
          <ChipsInput id="learning" values={c.skills.learning} onChange={(values) => skills('learning', values)} />
        </Field>
        <Field id="domains" label="Domains" path="candidate.domains" error={errorOf('candidate.domains')} wide>
          <ChipsInput
            id="domains"
            values={c.domains}
            onChange={(values) => candidate('domains', values)}
            placeholder="e.g. banking"
          />
        </Field>
      </Section>

      <Section title="Preferences" description="What you accept. Eligibility checks compare each job with these.">
        <Field
          id="employment"
          label="Employment types"
          path="preferences.employmentTypes"
          error={errorOf('preferences.employmentTypes')}
          asGroup
        >
          <ToggleChips
            label="Employment types"
            options={EMPLOYMENT_OPTIONS}
            values={p.employmentTypes}
            onChange={(values) => preferences('employmentTypes', values)}
            invalid={!!errorOf('preferences.employmentTypes')}
          />
        </Field>
        <Field
          id="seniority"
          label="Seniority"
          path="preferences.seniority"
          error={errorOf('preferences.seniority')}
          asGroup
        >
          <ToggleChips
            label="Seniority"
            options={SENIORITY_OPTIONS}
            values={p.seniority}
            onChange={(values) => preferences('seniority', values)}
            invalid={!!errorOf('preferences.seniority')}
          />
        </Field>
        <Field
          id="regions"
          label="Accepted regions"
          path="preferences.acceptRegions"
          error={errorOf('preferences.acceptRegions')}
          hint="EU, EEA, EUROPE, EMEA, WORLDWIDE, … or a country code"
          wide
        >
          <ChipsInput
            id="regions"
            values={p.acceptRegions}
            onChange={(values) => preferences('acceptRegions', values)}
            normalize={(value) => value.toUpperCase().replace(/\s+/g, '_')}
            invalid={!!errorOf('preferences.acceptRegions')}
            describedBy={errorOf('preferences.acceptRegions') ? 'regions-error' : 'regions-hint'}
            mono
          />
        </Field>
        <Field
          id="max-hours"
          label="Max hours per week"
          path="preferences.maxHoursPerWeek"
          error={errorOf('preferences.maxHoursPerWeek')}
          hint="Empty: no limit"
        >
          <input
            id="max-hours"
            type="number"
            min={1}
            max={168}
            value={shown(p.maxHoursPerWeek)}
            onChange={(e) => preferences('maxHoursPerWeek', optional(e.target.value))}
            className={inputClass(!!errorOf('preferences.maxHoursPerWeek'), 'num')}
            {...aria('max-hours', 'preferences.maxHoursPerWeek')}
          />
        </Field>
        <Field
          id="offset"
          label="Max time zone difference (hours)"
          path="preferences.timezone.maxOffsetDiffHours"
          error={errorOf('preferences.timezone.maxOffsetDiffHours')}
        >
          <input
            id="offset"
            type="number"
            min={0}
            max={26}
            value={shown(p.timezone.maxOffsetDiffHours)}
            onChange={(e) =>
              change('preferences.timezone.maxOffsetDiffHours', (d) => ({
                ...d,
                preferences: { ...d.preferences, timezone: { maxOffsetDiffHours: required(e.target.value) } },
              }))
            }
            className={inputClass(!!errorOf('preferences.timezone.maxOffsetDiffHours'), 'num')}
            {...aria('offset', 'preferences.timezone.maxOffsetDiffHours')}
          />
        </Field>
        <Field
          id="salary"
          label="Minimum salary"
          path="preferences.minSalary"
          error={errorOf('preferences.minSalary.amount') ?? errorOf('preferences.minSalary.currency')}
          hint="Soft: lowers the score, never fails a check"
          wide
        >
          {p.minSalary ? (
            <div className="flex flex-wrap gap-2">
              <input
                id="salary"
                type="number"
                min={0}
                aria-label="Amount"
                value={shown(p.minSalary.amount)}
                onChange={(e) => salary('amount', required(e.target.value))}
                className={inputClass(!!errorOf('preferences.minSalary.amount'), 'num w-32')}
              />
              <input
                aria-label="Currency"
                value={p.minSalary.currency}
                maxLength={3}
                onChange={(e) => salary('currency', e.target.value.toUpperCase())}
                className={inputClass(!!errorOf('preferences.minSalary.currency'), 'num w-20 uppercase')}
              />
              <select
                aria-label="Period"
                value={p.minSalary.period}
                onChange={(e) => salary('period', e.target.value)}
                className={inputClass(false, 'w-32')}
              >
                {PERIODS.map((period) => (
                  <option key={period.value} value={period.value}>
                    {period.label}
                  </option>
                ))}
              </select>
              <Button variant="quiet" onClick={() => preferences('minSalary', null)}>
                Remove
              </Button>
            </div>
          ) : (
            <div>
              <Button
                id="salary"
                variant="secondary"
                onClick={() => preferences('minSalary', { amount: Number.NaN, currency: 'EUR', period: 'YEAR' })}
              >
                Set a minimum salary
              </Button>
            </div>
          )}
        </Field>
        <Field
          id="exclude"
          label="Exclude companies"
          path="preferences.excludeCompanies"
          error={errorOf('preferences.excludeCompanies')}
          wide
        >
          <ChipsInput
            id="exclude"
            values={p.excludeCompanies}
            onChange={(values) => preferences('excludeCompanies', values)}
          />
        </Field>
      </Section>

      <Section
        title="Prefilter"
        description="Cheap rules before any LLM call. Patterns are case-insensitive regular expressions."
      >
        <Field
          id="title-include"
          label="Title must match one of"
          path="prefilter.titleInclude"
          error={errorOf('prefilter.titleInclude')}
          hint="Or the description has enough core skills (min skill hits)"
          wide
        >
          <ChipsInput
            id="title-include"
            values={f.titleInclude}
            onChange={(values) => prefilter('titleInclude', values)}
            invalid={!!errorOf('prefilter.titleInclude')}
            describedBy={errorOf('prefilter.titleInclude') ? 'title-include-error' : 'title-include-hint'}
            mono
          />
        </Field>
        <Field
          id="title-exclude"
          label="Title must not match"
          path="prefilter.titleExclude"
          error={errorOf('prefilter.titleExclude')}
          wide
        >
          <ChipsInput
            id="title-exclude"
            values={f.titleExclude}
            onChange={(values) => prefilter('titleExclude', values)}
            invalid={!!errorOf('prefilter.titleExclude')}
            mono
          />
        </Field>
        <Field
          id="max-age"
          label="Max posting age (days)"
          path="prefilter.maxAgeDays"
          error={errorOf('prefilter.maxAgeDays')}
        >
          <input
            id="max-age"
            type="number"
            min={1}
            value={shown(f.maxAgeDays)}
            onChange={(e) => prefilter('maxAgeDays', required(e.target.value))}
            className={inputClass(!!errorOf('prefilter.maxAgeDays'), 'num')}
            {...aria('max-age', 'prefilter.maxAgeDays')}
          />
        </Field>
        <Field
          id="skill-hits"
          label="Min skill hits"
          path="prefilter.minSkillHits"
          error={errorOf('prefilter.minSkillHits')}
        >
          <input
            id="skill-hits"
            type="number"
            min={1}
            value={shown(f.minSkillHits)}
            onChange={(e) => prefilter('minSkillHits', required(e.target.value))}
            className={inputClass(!!errorOf('prefilter.minSkillHits'), 'num')}
            {...aria('skill-hits', 'prefilter.minSkillHits')}
          />
        </Field>
      </Section>

      <Section title="Notifications" description="The daily Telegram digest.">
        <Field
          id="digest"
          label="Digest minimum score"
          path="notify.digestMinScore"
          error={errorOf('notify.digestMinScore')}
        >
          <input
            id="digest"
            type="number"
            min={0}
            max={100}
            value={shown(draft.notify.digestMinScore)}
            onChange={(e) =>
              change('notify.digestMinScore', (d) => ({ ...d, notify: { digestMinScore: required(e.target.value) } }))
            }
            className={inputClass(!!errorOf('notify.digestMinScore'), 'num')}
            {...aria('digest', 'notify.digestMinScore')}
          />
        </Field>
      </Section>
    </div>
  )
}
