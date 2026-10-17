import type { ReactNode } from 'react'
import { cn } from 'cn'

/** Small fact ("Contract", "EU", "€45/h"): line-faint bg (neutral-fill in the detail header). */
export function FactChip({ children, emphasis = false }: { children: ReactNode; emphasis?: boolean }) {
  return (
    <span
      className={cn(
        'inline-flex h-[22px] items-center rounded-tag px-2 text-[12px] leading-none whitespace-nowrap text-strong',
        emphasis ? 'bg-neutral-fill' : 'bg-line-faint',
      )}
    >
      {children}
    </span>
  )
}
