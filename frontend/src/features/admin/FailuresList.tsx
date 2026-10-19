import { Link } from 'react-router'
import { toast } from 'sonner'
import { type AdminOverview, useRetry } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { Tag } from '@/components/Tag'
import { Button } from '@/components/ui/button'
import { errorText } from '@/features/job/useStatusChanger'

type Purpose = 'EXTRACT' | 'SCORE'

/** Failed LLM work: purpose tag · job title · mono error · Retry (one job, runs in the background). */
export function FailuresList({ failures }: { failures: AdminOverview['failures'] }) {
  const retry = useRetry()
  const rows = [
    ...failures.extractions.map((job) => ({ ...job, purpose: 'EXTRACT' as Purpose })),
    ...failures.scorings.map((job) => ({ ...job, purpose: 'SCORE' as Purpose })),
  ]
  const pending = (id: number, purpose: Purpose) =>
    retry.isPending && retry.variables?.id === id && retry.variables.purpose === purpose

  return (
    <SectionCard label="Failed LLM work" className="min-w-0 flex-[1_1_420px]">
      {rows.length === 0 ? (
        <p className="m-0 text-[13.5px] text-muted">Nothing failed.</p>
      ) : (
        <ul className="m-0 flex list-none flex-col p-0">
          {rows.map((row) => (
            <li
              key={`${row.purpose}-${row.jobId}`}
              className="flex items-center gap-3 border-b border-line-faint py-2 last:border-b-0"
            >
              <Tag className="w-[62px] justify-center bg-neutral-fill text-ink-2">
                {row.purpose === 'EXTRACT' ? 'Extract' : 'Score'}
              </Tag>
              <span className="min-w-0 flex-1">
                <Link to={`/jobs/${row.jobId}`} className="block truncate text-[13.5px] text-ink">
                  {row.title}
                </Link>
                <span className="num block truncate text-[12px] text-muted" title={row.lastError ?? undefined}>
                  {row.lastError ?? 'No error recorded'} · {row.attempts} {row.attempts === 1 ? 'attempt' : 'attempts'}
                </span>
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={pending(row.jobId, row.purpose)}
                onClick={() =>
                  retry.mutate(
                    { id: row.jobId, purpose: row.purpose },
                    {
                      onSuccess: () => toast(`Retry started for "${row.title}"`),
                      onError: (error) => toast.error(`Retry failed: ${errorText(error)}`),
                    },
                  )
                }
              >
                {pending(row.jobId, row.purpose) ? 'Retrying…' : 'Retry'}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  )
}
