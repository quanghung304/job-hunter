import { TriangleAlert } from 'lucide-react'
import type { ModelChoice } from '@/api/queries'
import { Tag } from '@/components/Tag'
import { usd } from '@/lib/format'

const TIERS: Record<ModelChoice['tier'], string> = {
  FREE: 'bg-accent-tint text-accent-deep',
  LOCAL: 'bg-line-faint text-strong',
  PAID: 'bg-attention-bg text-attention-fg',
}

/**
 * What a confirmed LLM run will cost (ADR-0009/0010): the model with its tier, the estimated calls
 * and cost, and for a PAID model an explicit note (budget, or paid calls switched off).
 */
export function CostSummary({
  model,
  estimatedCalls,
  estimatedCostUsd,
  budgetUsd,
}: {
  model: ModelChoice
  estimatedCalls: number
  estimatedCostUsd: number | null
  budgetUsd: number
}) {
  return (
    <div className="flex flex-col gap-3">
      <dl className="m-0 grid grid-cols-[120px_1fr] gap-x-3 gap-y-1.5 text-[13.5px]">
        <dt className="text-muted">Model</dt>
        <dd className="m-0 flex flex-wrap items-center gap-2 text-ink">
          {model.label}
          <Tag className={`font-semibold ${TIERS[model.tier]}`}>{model.tier}</Tag>
        </dd>
        <dt className="text-muted">Estimated calls</dt>
        <dd className="num m-0 text-ink">{estimatedCalls}</dd>
      </dl>
      <p className="m-0 rounded-card border border-line bg-ground px-3 py-2.5 text-[13.5px] text-ink-2">
        Estimated cost{' '}
        <span className="num font-semibold text-ink">
          {estimatedCostUsd === null ? 'unknown (no cost history yet)' : `≈ ${usd(estimatedCostUsd)}`}
        </span>
      </p>
      {model.tier === 'PAID' ? (
        <p
          role="alert"
          className="m-0 flex gap-2 rounded-card bg-attention-bg px-3 py-2.5 text-[13.5px] text-attention-fg"
        >
          <TriangleAlert className="mt-0.5 size-4 shrink-0 text-attention-icon" aria-hidden />
          {model.paidCallsAllowed
            ? `Paid model — counts against the ${usd(budgetUsd)} monthly budget.`
            : 'Paid model, but paid calls are switched off (LLM_ALLOW_PAID_CALLS=false): this run would be refused.'}
        </p>
      ) : null}
    </div>
  )
}
