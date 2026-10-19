import type { JobDetail } from '@/api/queries'
import { ChecksTag } from '@/components/ChecksTag'
import { DecisionTag } from '@/components/DecisionTag'
import { RuleResultTag } from '@/components/RuleResultTag'
import { SectionCard } from '@/components/SectionCard'
import { checkReasonLabel, ELIGIBILITY_RULE_LABELS, prefilterReasonLabel } from '@/lib/labels'

/**
 * Card "Eligibility": one line per criterion (label · value · result) as checked by the app, and
 * the owner's decision (ADR-0011: the app gives no overall verdict). The decision control comes
 * with T-026.
 */
export function EligibilityPanel({ job }: { job: JobDetail }) {
  const checks = job.eligibilityChecks
  const counts = job.checks
  return (
    <SectionCard
      label="Eligibility"
      actions={
        job.eligibilityDecision ? <DecisionTag decision={job.eligibilityDecision} /> : <ChecksTag checks={counts} />
      }
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
      {checks.length === 0 ? (
        job.prefilterReasons.length === 0 ? (
          <p className="m-0 text-[13.5px] text-muted">Criteria appear after analysis.</p>
        ) : null
      ) : (
        <>
          <p className="mt-0 mb-2 text-[13px] text-muted">
            The app checks each criterion. The overall decision is yours.
            {counts ? (
              <span className="num">
                {' '}
                {counts.ok} OK · {counts.unknown} unclear · {counts.fail} fail
              </span>
            ) : null}
          </p>
          <dl className="m-0 flex flex-col">
            {checks.map((check) => (
              <div
                key={check.code}
                className="grid grid-cols-[104px_1fr_auto] items-center gap-3 border-b border-line-soft py-1.5 last:border-b-0"
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
        </>
      )}
    </SectionCard>
  )
}
