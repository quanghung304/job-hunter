import type { ReactNode } from 'react'
import { Inbox } from 'lucide-react'

/** Centered inbox icon, bold title, one muted sentence, optional secondary action. */
export function EmptyState({ title, text, action }: { title: string; text: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <Inbox className="size-8 text-icon-faint" strokeWidth={1.8} aria-hidden />
      <p className="mt-3 mb-1 font-semibold text-ink">{title}</p>
      <p className="m-0 text-[13.5px] text-muted">{text}</p>
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}
