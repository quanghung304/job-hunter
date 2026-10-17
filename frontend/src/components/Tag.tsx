import type { ReactNode } from 'react'
import { cn } from 'cn'

/** Base for the word tags (status, eligibility, rule results): 12px, radius 3px, words always shown. */
export function Tag({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex h-[22px] items-center rounded-tag px-2 text-[12px] leading-none font-medium whitespace-nowrap',
        className,
      )}
    >
      {children}
    </span>
  )
}
