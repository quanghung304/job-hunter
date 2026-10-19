import { CircleAlert, CircleCheck, CircleMinus, Loader2, type LucideIcon, TriangleAlert } from 'lucide-react'
import { Tag } from '@/components/Tag'

export type AdminTone = 'ok' | 'attention' | 'strong' | 'neutral' | 'running'

const TONES: Record<AdminTone, { className: string; icon: LucideIcon }> = {
  ok: { className: 'bg-accent-tint text-accent-deep', icon: CircleCheck },
  attention: { className: 'bg-attention-bg text-attention-fg', icon: TriangleAlert },
  strong: { className: 'bg-strong text-white', icon: CircleAlert },
  neutral: { className: 'bg-neutral-fill text-ink-2', icon: CircleMinus },
  running: { className: 'bg-accent-tint-2 text-accent-deep', icon: Loader2 },
}

/**
 * Source / company / run status tags (frontend.md "/admin"): OK = accent tint; Partial, Not found =
 * attention; Failed, Error = strong; Skipped, Off = neutral. Icon and word, never color alone.
 */
export function AdminTag({ tone, children }: { tone: AdminTone; children: string }) {
  const { className, icon } = TONES[tone]
  return (
    <Tag className={`font-semibold ${className}`} icon={icon}>
      {children}
    </Tag>
  )
}

/** A pipeline/collector run status as a tag; null status = still running. */
export function RunTag({
  status,
  nullText = 'Running',
}: {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED' | null
  nullText?: string
}) {
  if (status === 'SUCCESS') return <AdminTag tone="ok">OK</AdminTag>
  if (status === 'PARTIAL') return <AdminTag tone="attention">Partial</AdminTag>
  if (status === 'FAILED') return <AdminTag tone="strong">Failed</AdminTag>
  return <AdminTag tone={nullText === 'Running' ? 'running' : 'neutral'}>{nullText}</AdminTag>
}
