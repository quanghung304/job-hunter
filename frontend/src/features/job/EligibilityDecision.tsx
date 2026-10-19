import { Ban, CircleCheck, type LucideIcon } from 'lucide-react'
import { cn } from 'cn'
import type { Decision } from '@/lib/labels'

const OPTIONS: { value: Decision | null; label: string; icon?: LucideIcon; key?: string; pressed: string }[] = [
  { value: 'ELIGIBLE', label: 'Eligible', icon: CircleCheck, key: 'e', pressed: 'bg-eligible-bg text-eligible-fg' },
  { value: 'INELIGIBLE', label: 'Not eligible', icon: Ban, key: 'n', pressed: 'bg-ineligible-bg text-ineligible-fg' },
  { value: null, label: 'Undecided', pressed: 'bg-neutral-fill text-ink' },
]

/**
 * The owner's eligibility decision (ADR-0011): a segmented control of three `aria-pressed`
 * buttons. Pressing the current value again changes nothing; "Undecided" clears the decision.
 */
export function EligibilityDecision({
  value,
  onChange,
  showShortcuts = true,
}: {
  value: Decision | null
  onChange: (to: Decision | null) => void
  showShortcuts?: boolean
}) {
  return (
    <div
      role="group"
      aria-label="Your decision"
      className="inline-flex overflow-hidden rounded-card border border-control-border bg-surface"
    >
      {OPTIONS.map((option, i) => {
        const pressed = option.value === value
        const Icon = option.icon
        return (
          <button
            key={option.label}
            type="button"
            aria-pressed={pressed}
            title={option.key ? `${option.label} (${option.key})` : option.label}
            onClick={() => {
              if (!pressed) onChange(option.value)
            }}
            className={cn(
              'inline-flex h-9 items-center gap-1.5 px-3 text-[13.5px] font-medium max-lg:h-11',
              i > 0 && 'border-l border-control-border',
              pressed ? `${option.pressed} font-semibold` : 'text-ink-2 hover:bg-surface-muted',
            )}
          >
            {Icon ? <Icon className="size-4" strokeWidth={1.8} aria-hidden /> : null}
            {option.label}
            {showShortcuts && option.key ? (
              <span className="num text-[12px] opacity-70 max-lg:hidden" aria-hidden>
                {option.key}
              </span>
            ) : null}
          </button>
        )
      })}
    </div>
  )
}
