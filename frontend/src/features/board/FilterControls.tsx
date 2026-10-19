import { useId } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { type Option, POSTED_OPTIONS } from './filterOptions'
import type { BoardFilters } from './useBoardFilters'

/** Checkbox list for one multi-value filter. */
export function MultiCheck<T extends string>({
  legend,
  options,
  selected,
  onChange,
}: {
  legend: string
  options: readonly Option<T>[]
  selected: readonly T[]
  onChange: (values: T[]) => void
}) {
  const base = useId()
  return (
    <fieldset className="m-0 flex flex-col gap-0.5 border-0 p-0">
      <legend className="sr-only">{legend}</legend>
      {options.map((option, i) => {
        const id = `${base}-${i}`
        const checked = selected.includes(option.value)
        return (
          <label
            key={option.value}
            htmlFor={id}
            className="flex min-h-9 cursor-pointer items-center gap-2.5 rounded-control px-2 text-[13.5px] text-ink-2 hover:bg-line-faint max-lg:min-h-11"
          >
            <Checkbox
              id={id}
              checked={checked}
              onCheckedChange={(value) =>
                onChange(value === true ? [...selected, option.value] : selected.filter((v) => v !== option.value))
              }
            />
            {option.label}
          </label>
        )
      })}
      {options.length === 0 ? <p className="m-0 px-2 text-[13px] text-muted">No values</p> : null}
    </fieldset>
  )
}

/** Min / max score inputs (0–100); empty means no bound. */
export function ScoreRange({
  filters,
  onChange,
}: {
  filters: BoardFilters
  onChange: (patch: Pick<BoardFilters, 'minScore' | 'maxScore'>) => void
}) {
  const id = useId()
  const parse = (raw: string) => {
    if (raw.trim() === '') return null
    const value = Math.round(Number(raw))
    return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : null
  }
  const input =
    'num h-9 w-20 rounded-control border border-control-border bg-surface px-2 text-[13.5px] text-ink max-lg:h-11'
  return (
    <div className="flex items-end gap-3 px-2 py-1">
      <label htmlFor={`${id}-min`} className="flex flex-col gap-1 text-[12.5px] text-muted">
        Min
        <input
          id={`${id}-min`}
          type="number"
          min={0}
          max={100}
          inputMode="numeric"
          className={input}
          value={filters.minScore ?? ''}
          onChange={(e) => onChange({ minScore: parse(e.target.value), maxScore: filters.maxScore })}
        />
      </label>
      <label htmlFor={`${id}-max`} className="flex flex-col gap-1 text-[12.5px] text-muted">
        Max
        <input
          id={`${id}-max`}
          type="number"
          min={0}
          max={100}
          inputMode="numeric"
          className={input}
          value={filters.maxScore ?? ''}
          onChange={(e) => onChange({ minScore: filters.minScore, maxScore: parse(e.target.value) })}
        />
      </label>
    </div>
  )
}

/** Radio list for "Posted within". */
export function PostedOptions({ value, onChange }: { value: number | null; onChange: (days: number | null) => void }) {
  const name = useId()
  return (
    <fieldset className="m-0 flex flex-col gap-0.5 border-0 p-0">
      <legend className="sr-only">Posted</legend>
      {POSTED_OPTIONS.map((option) => (
        <label
          key={option.value}
          className="flex min-h-9 cursor-pointer items-center gap-2.5 rounded-control px-2 text-[13.5px] text-ink-2 hover:bg-line-faint max-lg:min-h-11"
        >
          <input
            type="radio"
            name={name}
            className="accent-accent"
            checked={String(value ?? '') === option.value}
            onChange={() => onChange(option.value === '' ? null : Number(option.value))}
          />
          {option.label}
        </label>
      ))}
    </fieldset>
  )
}

/** "Include expired jobs" switch-like checkbox. */
export function ExpiredToggle({ checked, onChange }: { checked: boolean; onChange: (value: boolean) => void }) {
  const id = useId()
  return (
    <label
      htmlFor={id}
      className="flex min-h-9 cursor-pointer items-center gap-2.5 px-2 text-[13.5px] text-ink-2 max-lg:min-h-11"
    >
      <Checkbox id={id} checked={checked} onCheckedChange={(value) => onChange(value === true)} />
      Include expired jobs
    </label>
  )
}
