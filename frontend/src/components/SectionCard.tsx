import type { ReactNode } from 'react'
import { cn } from 'cn'
import { SectionLabel } from './SectionLabel'

/** Surface card: 1px line, radius 6px, padding 20/22 (16 on mobile), starts with a SectionLabel. */
export function SectionCard({
  label,
  actions,
  children,
  className,
  id,
}: {
  label: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
  id?: string
}) {
  return (
    <section
      id={id}
      data-slot="section-card"
      className={cn('rounded-card border border-line bg-surface p-4 lg:px-[22px] lg:py-5', className)}
    >
      <div className="mb-3 flex items-center justify-between gap-3">
        <SectionLabel>{label}</SectionLabel>
        {actions}
      </div>
      {children}
    </section>
  )
}
