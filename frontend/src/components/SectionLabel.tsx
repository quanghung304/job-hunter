import type { ReactNode } from 'react'
import { cn } from 'cn'

/** 12px / 600, uppercase, letter-spacing 0.07em, muted. */
export function SectionLabel({
  children,
  className,
  as: Component = 'h2',
}: {
  children: ReactNode
  className?: string
  as?: 'h2' | 'h3' | 'div' | 'span'
}) {
  return (
    <Component className={cn('m-0 text-[12px] font-semibold tracking-[0.07em] text-muted uppercase', className)}>
      {children}
    </Component>
  )
}
