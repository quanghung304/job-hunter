import type { JobDetail } from '@/api/queries'
import { RuleResultTag } from '@/components/RuleResultTag'
import { SectionCard } from '@/components/SectionCard'
import { cn } from 'cn'
import { dateTime } from '@/lib/format'
import { checkReasonLabel, type Decision, ELIGIBILITY_RULE_LABELS, prefilterReasonLabel } from '@/lib/labels'
import { EligibilityDecision } from './EligibilityDecision'

function decisionHint(job: JobDetail): string | null {
  if (job.eligibilityDecision !== null) {
    return job.eligibilityDecidedAt ? `Set by you · ${dateTime(job.eligibilityDecidedAt)}` : 'Set by you'
  }
  if (!job.checks) return null
  return job.checks.unknown + job.checks.fail > 0
    ? 'Review the highlighted criteria, then decide.'
    : 'All criteria OK. Confirm if you agree.'
}

/**
 * Card "Eligibility" (ADR-0011): the app checks each criterion (label · value · result, rows not OK
 * highlighted), the owner decides with the segmented control. Deciding works before analysis too.
 */
export function EligibilityPanel({
  job,
  onDecisionChange,
}: {
  job: JobDetail
  onDecisionChange: (to: Decision | null) => void
}) {
  const checks = job.eligibilityChecks
  const counts = job.checks
  const hint = decisionHint(job)
  return (
    <SectionCard label="Eligibility">
      <p className="mt-0 mb-3 text-[13px] text-muted">
        The app checks each criterion. The overall decision is yours.
        {counts ? (
          <span className="num">
            {' '}
            {counts.ok} OK · {counts.unknown} unclear · {counts.fail} fail
          </span>
        ) : null}
      </p>
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
      {checks.length === 0 ? (
        <p className="m-0 rounded-card bg-surface-muted px-3 py-2.5 text-[13.5px] text-muted">
          Criteria appear after analysis. You can still decide now from the description.
        </p>
      ) : (
        <dl className="m-0 flex flex-col">
          {checks.map((check) => (
            <div
              key={check.code}
              data-result={check.result}
              className={cn(
                'grid grid-cols-[112px_1fr_auto] items-center gap-3 border-b border-line-soft px-2 py-1.5 last:border-b-0',
                check.result !== 'OK' && 'bg-attention-row',
              )}
            >
              <dt className="text-[13px] text-muted">{ELIGIBILITY_RULE_LABELS[check.code]}</dt>
              <dd className="m-0 min-w-0 text-[13.5px] text-ink-2">
                <span className="block truncate" title={check.value}>
                  {check.value}
                </span>
                {check.reason ? (
                  <span className="block text-[12.5px] text-attention-fg">{checkReasonLabel(check.reason)}</span>
                ) : null}
              </dd>
              <dd className="m-0">
                <RuleResultTag result={check.result} />
              </dd>
            </div>
          ))}
        </dl>
      )}
      <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2 border-t border-line-soft pt-4">
        <span className="text-[13.5px] font-semibold text-ink">Your decision</span>
        <EligibilityDecision value={job.eligibilityDecision} onChange={onDecisionChange} />
        {hint ? <span className="text-[13px] text-muted">{hint}</span> : null}
      </div>
    </SectionCard>
  )
}
