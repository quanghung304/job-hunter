import { cn } from 'cn'

/** Input classes of the Profile form; invalid = attention border and ring (frontend.md "/profile"). */
export function inputClass(invalid: boolean, extra?: string) {
  return cn(
    'h-9 w-full rounded-control border bg-surface px-2.5 text-[14px] text-ink placeholder:text-muted max-lg:h-11',
    invalid ? 'border-attention-icon ring-2 ring-attention-bg' : 'border-control-border',
    extra,
  )
}
