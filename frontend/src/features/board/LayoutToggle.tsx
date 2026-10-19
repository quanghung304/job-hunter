import { LayoutGrid, List } from 'lucide-react'
import { cn } from 'cn'
import type { BoardLayout } from './boardLayout'

/** Grid | List icon buttons with `aria-pressed`. */
export function LayoutToggle({ value, onChange }: { value: BoardLayout; onChange: (layout: BoardLayout) => void }) {
  const options = [
    { value: 'grid' as const, label: 'Grid', icon: LayoutGrid },
    { value: 'list' as const, label: 'List', icon: List },
  ]
  return (
    <div
      role="group"
      aria-label="Layout"
      className="inline-flex overflow-hidden rounded-control border border-control-border bg-surface"
    >
      {options.map((option, i) => {
        const Icon = option.icon
        const pressed = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            aria-label={option.label}
            title={option.label}
            aria-pressed={pressed}
            onClick={() => onChange(option.value)}
            className={cn(
              'inline-flex size-9 items-center justify-center',
              i > 0 && 'border-l border-control-border',
              pressed ? 'bg-neutral-fill text-ink' : 'text-muted hover:bg-surface-muted',
            )}
          >
            <Icon className="size-4" strokeWidth={1.8} aria-hidden />
          </button>
        )
      })}
    </div>
  )
}
