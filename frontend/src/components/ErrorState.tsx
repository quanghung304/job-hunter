import type { ReactNode } from 'react'
import { TriangleAlert } from 'lucide-react'
import { ApiError } from '@/api/client'

/**
 * Error box: ground bg, 1px line, warning icon, bold title, the Problem Details `detail`, and
 * "status · code" in mono. A network failure reads "Backend not reachable — Is the app running?".
 */
export function ErrorState({
  error,
  title = 'Something went wrong',
  action,
}: {
  error: unknown
  title?: string
  action?: ReactNode
}) {
  const api = error instanceof ApiError ? error : null
  const network = api?.network ?? false
  const heading = network ? 'Backend not reachable' : title
  const detail = network
    ? 'Is the app running?'
    : (api?.detail ?? (error instanceof Error ? error.message : 'Unexpected error'))
  const meta = !network && api ? [api.status, api.code].filter((part) => part !== null).join(' · ') : ''

  return (
    <div role="alert" className="flex gap-3 rounded-card border border-line bg-ground p-4">
      <TriangleAlert className="mt-0.5 size-5 shrink-0 text-attention-icon" strokeWidth={1.8} aria-hidden />
      <div className="min-w-0">
        <p className="m-0 font-semibold text-ink">{heading}</p>
        <p className="mt-1 mb-0 text-[13.5px] text-ink-2">{detail}</p>
        {meta ? <p className="num mt-1 mb-0 text-[12px] text-muted">{meta}</p> : null}
        {action ? <div className="mt-3">{action}</div> : null}
      </div>
    </div>
  )
}
