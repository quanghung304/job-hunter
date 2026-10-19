import type { PipelineReport } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { dateTime, duration } from '@/lib/format'
import { RunTag } from './AdminTag'

const TRIGGERS: Record<PipelineReport['trigger'], string> = {
  SCHEDULED: 'Scheduled',
  CATCH_UP: 'Catch-up',
  MANUAL: 'Manual',
}

/** Recent pipeline runs (newest first): time · summary · status tag. */
export function RunsList({ reports }: { reports: PipelineReport[] }) {
  return (
    <SectionCard label="Recent pipeline runs" className="min-w-0 flex-[1_1_420px]">
      {reports.length === 0 ? (
        <p className="m-0 text-[13.5px] text-muted">No run yet. Run the pipeline to collect jobs.</p>
      ) : (
        <ul className="m-0 flex list-none flex-col p-0">
          {reports.slice(0, 10).map((report) => (
            <li
              key={report.runNumber}
              className="flex items-start gap-3 border-b border-line-faint py-2 last:border-b-0"
            >
              <time className="num w-28 shrink-0 pt-0.5 text-[12.5px] text-muted" dateTime={report.startedAt}>
                {dateTime(report.startedAt)}
              </time>
              <span className="min-w-0 flex-1 text-[13.5px] text-ink-2">
                {TRIGGERS[report.trigger]} ·{' '}
                <span className="num">
                  {report.newJobs} new · {report.prefilterPassed} passed · {report.expired} expired ·{' '}
                  {duration(report.durationMs)}
                </span>
                {report.error ? (
                  <span className="block truncate text-[12.5px] text-muted" title={report.error}>
                    {report.error}
                  </span>
                ) : null}
              </span>
              <RunTag status={report.status} />
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  )
}
