import type { JobDetail } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { dateTime } from '@/lib/format'
import { STATUS_LABELS } from '@/lib/labels'

/** Card "History": status changes, newest first (time · text). */
export function HistoryList({ job }: { job: JobDetail }) {
  const entries = [...job.history].reverse()
  return (
    <SectionCard label="History">
      <ul className="m-0 flex list-none flex-col gap-2 p-0">
        {entries.map((entry) => (
          <li key={`${entry.at}-${entry.to}`} className="flex gap-3 text-[13.5px] text-ink-2">
            <time className="num w-28 shrink-0 text-[12.5px] text-muted" dateTime={entry.at}>
              {dateTime(entry.at)}
            </time>
            <span>
              {STATUS_LABELS[entry.from]} → {STATUS_LABELS[entry.to]}
              {entry.note ? <span className="text-muted"> · {entry.note}</span> : null}
            </span>
          </li>
        ))}
        <li className="flex gap-3 text-[13.5px] text-ink-2">
          <time className="num w-28 shrink-0 text-[12.5px] text-muted" dateTime={job.firstSeenAt}>
            {dateTime(job.firstSeenAt)}
          </time>
          <span>First seen</span>
        </li>
      </ul>
    </SectionCard>
  )
}
