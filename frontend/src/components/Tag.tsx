import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from 'cn'

/**
 * Base for the word tags (status, analysis, decision, checks, rule results): 12px, words always
 * shown; colored state tags add a 12px icon before the word, so color is never the only signal.
 */
export function Tag({
  className,
  icon: Icon,
  children,
}: {
  className?: string
  icon?: LucideIcon
  children: ReactNode
}) {
  return (
    <span
      className={cn(
        'inline-flex h-[22px] items-center gap-1 rounded-tag px-2 text-[12px] leading-none font-medium whitespace-nowrap',
        className,
      )}
    >
      {Icon ? <Icon className="size-3 shrink-0" strokeWidth={2} aria-hidden /> : null}
      {children}
    </span>
  )
}
