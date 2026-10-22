import { X } from 'lucide-react'
import { useState } from 'react'
import { cn } from 'cn'

/**
 * A list of short values as chips with one text input: Enter (or leaving the field) adds the typed
 * value, Backspace on an empty input removes the last chip, each chip has a remove button.
 * Duplicates are ignored; `normalize` can e.g. upper-case region tokens.
 */
export function ChipsInput({
  id,
  values,
  onChange,
  placeholder,
  invalid = false,
  describedBy,
  normalize = (value) => value,
  mono = false,
}: {
  id: string
  values: readonly string[]
  onChange: (values: string[]) => void
  placeholder?: string
  invalid?: boolean
  describedBy?: string
  normalize?: (value: string) => string
  mono?: boolean
}) {
  const [text, setText] = useState('')

  const add = () => {
    const value = normalize(text.trim())
    setText('')
    if (value === '' || values.includes(value)) return
    onChange([...values, value])
  }

  return (
    <div
      className={cn(
        'flex min-h-9 flex-wrap items-center gap-1.5 rounded-control border bg-surface px-1.5 py-1 focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-accent',
        invalid ? 'border-attention-icon ring-2 ring-attention-bg' : 'border-control-border',
      )}
    >
      {values.map((value, i) => (
        <span
          key={value}
          className={cn(
            'inline-flex h-6 max-w-full items-center gap-1 rounded-tag bg-line-faint pr-0.5 pl-2 text-[12.5px] text-strong',
            mono && 'num',
          )}
        >
          <span className="truncate">{value}</span>
          <button
            type="button"
            aria-label={`Remove ${value}`}
            onClick={() => onChange(values.filter((_, j) => j !== i))}
            className="inline-flex size-5 items-center justify-center rounded-tag text-muted hover:bg-neutral-fill hover:text-ink max-lg:size-7"
          >
            <X className="size-3" aria-hidden />
          </button>
        </span>
      ))}
      <input
        id={id}
        value={text}
        placeholder={values.length === 0 ? placeholder : undefined}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        onChange={(event) => setText(event.target.value)}
        onBlur={add}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            add()
          } else if (event.key === 'Backspace' && text === '' && values.length > 0) {
            onChange(values.slice(0, -1))
          }
        }}
        className={cn(
          'h-7 min-w-24 flex-1 border-0 bg-transparent px-1 text-[13.5px] text-ink outline-none placeholder:text-muted',
          mono && 'num',
        )}
      />
    </div>
  )
}
