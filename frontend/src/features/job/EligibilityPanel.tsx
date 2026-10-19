import type { JobDetail } from '@/api/queries'
import { EligibilityTag } from '@/components/EligibilityTag'
import { RuleResultTag } from '@/components/RuleResultTag'
import { SectionCard } from '@/components/SectionCard'
import { hoursRange, timezoneRange } from '@/lib/format'
import {
  ELIGIBILITY_RULE_LABELS,
  ELIGIBILITY_RULES,
  EMPLOYMENT_LABELS,
  type EligibilityRule,
  eligibilityResults,
  prefilterReasonLabel,
  REMOTE_LABELS,
  SENIORITY_LABELS,
} from '@/lib/labels'

type Attributes = NonNullable<JobDetail['extraction']>

/** What the posting says for each rule (from the extraction); "—" when it says nothing. */
function ruleValue(rule: EligibilityRule, a: Attributes): string {
  switch (rule) {
    case 'WORK_MODE':
      return REMOTE_LABELS[a.remoteType]
    case 'REGION':
      return a.allowedRegions?.length ? a.allowedRegions.join(', ') : '—'
    case 'WORK_AUTH':
      return a.workAuthorization ?? '—'
    case 'TIMEZONE':
      return a.timezoneRequirement ? timezoneRange(a.timezoneRequirement) : '—'
    case 'EMPLOYMENT_TYPE':
      return a.employmentTypes?.length ? a.employmentTypes.map((t) => EMPLOYMENT_LABELS[t]).join(', ') : '—'
    case 'HOURS':
      return (a.hoursPerWeek && hoursRange(a.hoursPerWeek)) ?? '—'
    case 'SENIORITY':
      return a.seniority ? (SENIORITY_LABELS[a.seniority] ?? a.seniority) : '—'
    case 'LANGUAGE':
      return a.requiredLanguages?.length
        ? a.requiredLanguages.map((l) => `${l.code.toUpperCase()} ${l.level}`).join(', ')
        : '—'
    case 'SCAM':
      return a.isScamSignal ? 'Scam signals' : 'No signals'
  }
}

/** Card "Eligibility": one line per rule (label · value · result), notes, and prefilter reasons. */
export function EligibilityPanel({ job }: { job: JobDetail }) {
  const attributes = job.extraction
  return (
    <SectionCard
      label="Eligibility"
      actions={job.eligibility ? <EligibilityTag eligibility={job.eligibility} /> : undefined}
    >
      {job.prefilterReasons.length > 0 ? (
        <div className="mb-3">
          <p className="m-0 text-[13.5px] text-ink-2">Rejected by the prefilter (no LLM call):</p>
          <ul className="mt-1 mb-0 pl-5 text-[13.5px] text-ink-2">
            {job.prefilterReasons.map((code) => (
              <li key={code}>{prefilterReasonLabel(code)}</li>
            ))}
          </ul>
        </div>
      ) : null}
      {job.eligibility === null || attributes === null ? (
        job.prefilterReasons.length === 0 ? (
          <p className="m-0 text-[13.5px] text-muted">Checked after Analyze.</p>
        ) : null
      ) : (
        <EligibilityRules reasons={job.eligibilityReasons} attributes={attributes} />
      )}
    </SectionCard>
  )
}

function EligibilityRules({ reasons, attributes }: { reasons: readonly string[]; attributes: Attributes }) {
  const { rules, notes } = eligibilityResults(reasons)
  return (
    <>
      <dl className="m-0 flex flex-col">
        {ELIGIBILITY_RULES.map((rule) => (
          <div
            key={rule}
            className="grid grid-cols-[104px_1fr_auto] items-center gap-3 border-b border-line-soft py-1.5 last:border-b-0"
          >
            <dt className="text-[13px] text-muted">{ELIGIBILITY_RULE_LABELS[rule]}</dt>
            <dd className="m-0 truncate text-[13.5px] text-ink-2">{ruleValue(rule, attributes)}</dd>
            <dd className="m-0">
              <RuleResultTag result={rules[rule]} />
            </dd>
          </div>
        ))}
      </dl>
      {notes.length > 0 ? (
        <ul className="mt-3 mb-0 pl-5 text-[13px] text-attention-fg">
          {notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      ) : null}
    </>
  )
}
