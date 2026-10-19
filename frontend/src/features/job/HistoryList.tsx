import type { JobDetail } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { dateTime } from '@/lib/format'
import { decisionHistoryLabel, STATUS_LABELS, type TrackingStatus } from '@/lib/labels'

function entryText(entry: JobDetail['history'][number]): string {
  if (entry.kind === 'ELIGIBILITY') {
    return `Decision: ${decisionHistoryLabel(entry.from)} → ${decisionHistoryLabel(entry.to)}`
  }
  const label = (value: string) => STATUS_LABELS[value as TrackingStatus] ?? value
  return `${label(entry.from)} → ${label(entry.to)}`
}

/** Card "History": status changes and eligibility decisions, newest first (time · text). */
export function HistoryList({ job }: { job: JobDetail }) {
  const entries = [...job.history].reverse()
  return (
    <SectionCard label="History">
      <ul className="m-0 flex list-none flex-col gap-2 p-0">
        {entries.map((entry) => (
          <li key={`${entry.at}-${entry.kind}-${entry.to}`} className="flex gap-3 text-[13.5px] text-ink-2">
            <time className="num w-28 shrink-0 text-[12.5px] text-muted" dateTime={entry.at}>
              {dateTime(entry.at)}
            </time>
            <span>
              {entryText(entry)}
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
