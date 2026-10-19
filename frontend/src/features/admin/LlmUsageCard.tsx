import { cn } from 'cn'
import type { LlmUsage } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { compactCount, shortDate, usd } from '@/lib/format'

/**
 * LLM usage: PAID cost this month against the budget (meter), calls and tokens today, and 14 daily
 * bars (CSS only). Bars show cost; while every day cost $0 (free models) they show the calls.
 */
export function LlmUsageCard({ usage }: { usage: LlmUsage }) {
  const budget = usage.monthlyBudgetUsd
  const ratio = budget > 0 ? Math.min(1, usage.monthToDateUsd / budget) : 0
  const today = usage.daily[usage.daily.length - 1]
  const tokensToday = today ? today.inputTokens + today.outputTokens : 0
  const byCost = usage.daily.some((day) => day.costUsd > 0)
  const values = usage.daily.map((day) => (byCost ? day.costUsd : day.calls))
  const max = Math.max(0, ...values)
  const first = usage.daily[0]

  return (
    <SectionCard label="LLM usage" className="min-w-0 flex-[1_1_340px]">
      <p className="m-0 flex items-baseline gap-2">
        <span className="num text-[26px] leading-tight font-semibold text-ink">{usd(usage.monthToDateUsd)}</span>
        <span className="text-[13px] text-muted">
          of <span className="num">{usd(budget)}</span> budget this month
        </span>
      </p>
      <div
        role="meter"
        aria-label="Monthly budget used"
        aria-valuemin={0}
        aria-valuemax={budget}
        aria-valuenow={usage.monthToDateUsd}
        className="mt-2 h-2.5 overflow-hidden rounded-badge bg-neutral-fill"
      >
        <span
          className={cn('block h-full', ratio >= 0.8 ? 'bg-attention-fg' : 'bg-accent')}
          style={{ width: `${ratio * 100}%` }}
        />
      </div>
      <p className="num mt-2 mb-0 text-[12.5px] text-muted">
        {usage.callsToday} calls today · {compactCount(tokensToday)} tokens
      </p>
      <div className="mt-4">
        <p className="mt-0 mb-1.5 text-[12px] text-muted">Last 14 days · {byCost ? 'cost' : 'calls'}</p>
        <ol
          aria-label={`Daily LLM ${byCost ? 'cost' : 'calls'}`}
          className="m-0 flex h-[84px] list-none items-end gap-1 p-0"
        >
          {usage.daily.map((day, i) => {
            const last = i === usage.daily.length - 1
            const height = max > 0 ? Math.max(2, (values[i] / max) * 100) : 2
            const text = `${shortDate(day.date)}: ${day.calls} calls, ${usd(day.costUsd)}`
            return (
              <li
                key={day.date}
                data-today={last || undefined}
                title={text}
                aria-label={text}
                className={cn('min-w-0 flex-1 rounded-t-[2px]', last ? 'bg-accent' : 'bg-accent-bar')}
                style={{ height: `${height}%` }}
              />
            )
          })}
        </ol>
        {first && today ? (
          <p className="num mt-1 mb-0 flex justify-between text-[11.5px] text-muted">
            <span>{shortDate(first.date)}</span>
            <span>{shortDate(today.date)}</span>
          </p>
        ) : null}
      </div>
    </SectionCard>
  )
}
