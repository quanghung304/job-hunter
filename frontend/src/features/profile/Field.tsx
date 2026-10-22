import type { ReactNode } from 'react'
import { cn } from 'cn'

/**
 * One form field: label with the YAML path in mono, the control, then the error (attention text)
 * or a muted hint. `wide` spans both columns of the section grid.
 */
export function Field({
  id,
  label,
  path,
  error,
  hint,
  wide = false,
  asGroup = false,
  children,
}: {
  id: string
  label: string
  path: string
  error?: string
  hint?: string
  wide?: boolean
  /** The control is a group of buttons: the label names it instead of a `<label for>`. */
  asGroup?: boolean
  children: ReactNode
}) {
  const LabelTag = asGroup ? 'span' : 'label'
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', wide && 'min-[760px]:col-span-2')}>
      <LabelTag
        {...(asGroup ? { id: `${id}-label` } : { htmlFor: id })}
        className="flex flex-wrap items-baseline gap-x-2"
      >
        <span className="text-[13.5px] font-medium text-ink">{label}</span>
        <span className="num text-[11.5px] text-muted">{path}</span>
      </LabelTag>
      {children}
      {error ? (
        <p id={`${id}-error`} className="m-0 text-[12.5px] text-attention-fg">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="m-0 text-[12.5px] text-muted">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
