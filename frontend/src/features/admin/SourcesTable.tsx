import type { AdminSource } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { dateTime, relativeTime } from '@/lib/format'
import { AdminTag, RunTag } from './AdminTag'

function statusOf(source: AdminSource) {
  if (!source.enabled) return <AdminTag tone="neutral">Off</AdminTag>
  if (!source.lastRun) return <AdminTag tone="neutral">Never run</AdminTag>
  return <RunTag status={source.lastRun.status} />
}

const TH = 'border-b border-line px-3 py-2 text-left text-[12px] font-semibold tracking-[0.04em] text-muted uppercase'
const TD = 'border-b border-line-faint px-3 py-2.5 align-middle whitespace-nowrap'

/** Source health: status tag, fetched / new counts of the last run, last success, note (errors truncated). */
export function SourcesTable({ sources }: { sources: AdminSource[] }) {
  return (
    <SectionCard label="Sources" className="min-w-0 flex-[999_1_640px]">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-[13.5px]">
          <thead>
            <tr>
              <th className={TH}>Source</th>
              <th className={TH}>Status</th>
              <th className={`${TH} text-right`}>Fetched</th>
              <th className={`${TH} text-right`}>New jobs</th>
              <th className={TH}>Last success</th>
              <th className={TH}>Note</th>
            </tr>
          </thead>
          <tbody>
            {sources.map((source) => {
              const run = source.lastRun
              const note = !source.enabled ? 'Disabled in application.yml' : (run?.error ?? '')
              return (
                <tr key={source.code}>
                  <td className={`${TD} font-medium text-ink`}>{source.name}</td>
                  <td className={TD}>{statusOf(source)}</td>
                  <td className={`${TD} num text-right`}>{run ? run.fetched : '—'}</td>
                  <td className={`${TD} num text-right`}>{run ? run.newJobs : '—'}</td>
                  <td className={`${TD} num text-[12.5px] text-muted`}>
                    {source.lastSuccessAt ? (
                      <time dateTime={source.lastSuccessAt} title={dateTime(source.lastSuccessAt)}>
                        {relativeTime(source.lastSuccessAt)}
                      </time>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className={`${TD} max-w-[280px] truncate text-[13px] text-muted`} title={note || undefined}>
                    {note}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </SectionCard>
  )
}
