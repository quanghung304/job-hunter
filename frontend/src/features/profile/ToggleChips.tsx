import { Check } from 'lucide-react'
import { cn } from 'cn'

/** A set of options as toggle buttons (`aria-pressed`); the selection keeps the options' order. */
export function ToggleChips<T extends string>({
  label,
  options,
  values,
  onChange,
  invalid = false,
  describedBy,
}: {
  label: string
  options: readonly { value: T; label: string }[]
  values: readonly T[]
  onChange: (values: T[]) => void
  invalid?: boolean
  describedBy?: string
}) {
  return (
    <div role="group" aria-label={label} aria-describedby={describedBy} className="flex flex-wrap gap-1.5">
      {options.map((option) => {
        const pressed = values.includes(option.value)
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={pressed}
            onClick={() =>
              onChange(
                options
                  .map((o) => o.value)
                  .filter((value) => (value === option.value ? !pressed : values.includes(value))),
              )
            }
            className={cn(
              'inline-flex h-8 items-center gap-1 rounded-full border px-3 text-[13px] max-lg:h-11',
              pressed
                ? 'border-accent bg-accent-tint-2 font-medium text-accent-deep'
                : 'border-control-border bg-surface text-ink-2 hover:bg-surface-muted',
              invalid && !pressed && 'border-attention-icon',
            )}
          >
            {pressed ? <Check className="size-3.5" aria-hidden /> : null}
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
