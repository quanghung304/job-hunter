import type { ReactNode } from 'react'

/** Keyboard key: mono 12px, 1px control border (2px bottom), radius 4px, surface-muted bg. */
export function Kbd({ children }: { children: ReactNode }) {
  return (
    <kbd className="num inline-flex min-w-5 items-center justify-center rounded-badge border border-b-2 border-control-border bg-surface-muted px-1.5 text-[12px] leading-[18px] text-ink-2">
      {children}
    </kbd>
  )
}
