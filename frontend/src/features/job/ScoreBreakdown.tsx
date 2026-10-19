import type { JobDetail } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'

/** Rubric keys and their maximum points (scoring-spec.md §5). */
const RUBRIC = [
  { key: 'skills', label: 'Skills', max: 40 },
  { key: 'experience', label: 'Experience', max: 20 },
  { key: 'arrangement', label: 'Arrangement', max: 20 },
  { key: 'domain', label: 'Domain', max: 10 },
  { key: 'other', label: 'Other', max: 10 },
] as const

/** Bars per rubric key (8px, neutral track, accent fill) and the total. */
export function ScoreBreakdown({ job }: { job: JobDetail }) {
  const breakdown = job.scoreBreakdown
  return (
    <SectionCard label="Score breakdown">
      {breakdown === null ? (
        <p className="m-0 text-[13.5px] text-muted">No score yet.</p>
      ) : (
        <>
          <dl className="m-0 flex flex-col gap-2.5">
            {RUBRIC.map(({ key, label, max }) => {
              const value = breakdown[key]
              return (
                <div key={key} className="grid grid-cols-[96px_1fr_auto] items-center gap-3">
                  <dt className="text-[13px] text-muted">{label}</dt>
                  <dd className="m-0 h-2 overflow-hidden rounded-badge bg-neutral-fill" aria-hidden>
                    <span
                      className="block h-full bg-accent"
                      style={{ width: `${Math.min(100, (value / max) * 100)}%` }}
                    />
                  </dd>
                  <dd
                    className="num m-0 min-w-12 text-right text-[13px] text-ink-2"
                    aria-label={`${label} ${value} of ${max}`}
                  >
                    {value}/{max}
                  </dd>
                </div>
              )
            })}
          </dl>
          <p className="mt-4 mb-0 flex justify-end border-t border-line-soft pt-3 text-[13px] text-muted">
            Total&nbsp;<span className="num font-semibold text-ink">{job.score}</span>/100
          </p>
        </>
      )}
    </SectionCard>
  )
}
