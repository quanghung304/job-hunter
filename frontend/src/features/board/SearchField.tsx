import { Search } from 'lucide-react'
import { type Ref, useEffect, useRef, useState } from 'react'
import { cn } from 'cn'
import { Kbd } from '@/components/Kbd'

export const SEARCH_DELAY_MS = 300

/** Title/company search, debounced 300 ms; `/` focuses it (useBoardKeyboard), `Esc` blurs it. */
export function SearchField({
  value,
  onChange,
  inputRef,
  className,
  autoFocus,
}: {
  value: string
  onChange: (value: string) => void
  inputRef?: Ref<HTMLInputElement>
  className?: string
  autoFocus?: boolean
}) {
  const [draft, setDraft] = useState(value)
  const [synced, setSynced] = useState(value)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // The URL changed from outside (Reset, back button): show its value.
  if (value !== synced) {
    setSynced(value)
    setDraft(value)
  }

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current)
    },
    [],
  )

  const change = (next: string) => {
    setDraft(next)
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => {
      setSynced(next.trim())
      onChange(next)
    }, SEARCH_DELAY_MS)
  }

  return (
    <label className={cn('relative flex min-w-60 items-center', className)}>
      <Search className="pointer-events-none absolute left-2.5 size-4 text-muted" aria-hidden />
      <input
        ref={inputRef}
        type="search"
        aria-label="Search title or company"
        value={draft}
        autoFocus={autoFocus}
        onChange={(event) => change(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Escape') event.currentTarget.blur()
        }}
        placeholder="Search title or company"
        className="h-9 w-full rounded-control border border-control-border bg-surface pr-9 pl-8 text-[13.5px] text-ink placeholder:text-muted max-lg:h-11"
      />
      <span className="pointer-events-none absolute right-2 max-lg:hidden">
        <Kbd>/</Kbd>
      </span>
    </label>
  )
}
