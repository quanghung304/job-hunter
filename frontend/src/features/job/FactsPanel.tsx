import type { JobDetail } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { hoursRange, salary, timezoneRange } from '@/lib/format'
import { CONFIDENCE_LABELS, EMPLOYMENT_LABELS, REMOTE_LABELS, SENIORITY_LABELS } from '@/lib/labels'

/** Card "Extracted facts": `<dl>` pairs; source fields first, then what the LLM extracted. */
export function FactsPanel({ job }: { job: JobDetail }) {
  const a = job.extraction
  const facts: [string, string | null | undefined][] = [
    ['Location', job.locationText],
    ['Salary (source)', job.salary ? salary(job.salary) : null],
    ['Tags', job.tags.length ? job.tags.join(', ') : null],
  ]
  if (a) {
    facts.push(
      ['Role', a.roleSummary],
      ['Work mode', REMOTE_LABELS[a.remoteType]],
      ['Regions', a.allowedRegions?.length ? a.allowedRegions.join(', ') : null],
      ['Region evidence', a.regionEvidence],
      ['Time zone', a.timezoneRequirement ? timezoneRange(a.timezoneRequirement) : null],
      ['Employment', a.employmentTypes?.length ? a.employmentTypes.map((t) => EMPLOYMENT_LABELS[t]).join(', ') : null],
      ['Hours / week', a.hoursPerWeek ? hoursRange(a.hoursPerWeek) : null],
      ['Seniority', a.seniority ? (SENIORITY_LABELS[a.seniority] ?? a.seniority) : null],
      [
        'Experience',
        a.minYearsExperience !== undefined && a.minYearsExperience !== null ? `${a.minYearsExperience}+ years` : null,
      ],
      ['Required skills', a.requiredSkills?.length ? a.requiredSkills.join(', ') : null],
      ['Nice to have', a.niceToHaveSkills?.length ? a.niceToHaveSkills.join(', ') : null],
      [
        'Languages',
        a.requiredLanguages?.length
          ? a.requiredLanguages.map((l) => `${l.code.toUpperCase()} ${l.level}`).join(', ')
          : null,
      ],
      ['Work permit', a.workAuthorization],
      ['Salary (posting)', a.salary ? salary(a.salary) : null],
      ['Agency / repost', a.isAgencyOrReposting ? 'Yes' : 'No'],
      ['Scam signals', a.isScamSignal ? 'Yes' : 'No'],
      ['Confidence', CONFIDENCE_LABELS[a.confidence] ?? a.confidence],
    )
  }
  const shown = facts.filter((fact): fact is [string, string] => typeof fact[1] === 'string' && fact[1] !== '')

  return (
    <SectionCard label="Extracted facts">
      {!a ? <p className="mt-0 mb-3 text-[13.5px] text-muted">The LLM extracts the facts on Analyze.</p> : null}
      <dl className="m-0 grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-x-6 gap-y-2">
        {shown.map(([label, value]) => (
          <div key={label} className="grid grid-cols-[130px_1fr] gap-3">
            <dt className="text-[13px] text-muted">{label}</dt>
            <dd className="m-0 text-[13.5px] break-words text-ink-2">{value}</dd>
          </div>
        ))}
      </dl>
    </SectionCard>
  )
}
