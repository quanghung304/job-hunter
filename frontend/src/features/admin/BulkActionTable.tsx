import { useState } from 'react'
import { Link } from 'react-router'
import { cn } from 'cn'
import { BULK_PAGE_SIZE, type BulkMode, useBulkCandidates } from '@/api/queries'
import { AnalysisTag } from '@/components/AnalysisTag'
import { ChecksTag } from '@/components/ChecksTag'
import { DecisionTag } from '@/components/DecisionTag'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { SectionCard } from '@/components/SectionCard'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Pagination } from '@/features/board/Pagination'
import { relativeTime } from '@/lib/format'
import { BulkActionDialog } from './BulkActionDialog'

/** At most this many jobs per bulk action (api-spec `POST /api/admin/jobs/analyze|score`). */
export const MAX_BULK = 200

const MODES: { value: BulkMode; label: string }[] = [
  { value: 'analyze', label: 'Analyze' },
  { value: 'score', label: 'Score' },
]

const TH = 'border-b border-line px-3 py-2 text-left text-[12px] font-semibold tracking-[0.04em] text-muted uppercase'
const TD = 'border-b border-line-faint px-3 py-2 align-middle whitespace-nowrap'

/**
 * Bulk actions (ADR-0010): pick jobs for Analyze (NOT_ANALYZED) or Score (analyzed, not scored,
 * not marked Not eligible), at most 200, then confirm in `BulkActionDialog`.
 */
export function BulkActionTable({ budgetUsd }: { budgetUsd: number }) {
  const [mode, setMode] = useState<BulkMode>('analyze')
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState<Set<number>>(() => new Set())
  const candidates = useBulkCandidates(mode, page)
  const items = candidates.data?.items ?? []
  const full = selected.size >= MAX_BULK

  const changeMode = (next: BulkMode) => {
    setMode(next)
    setPage(1)
    setSelected(new Set())
  }
  const toggle = (id: number, on: boolean) =>
    setSelected((current) => {
      const next = new Set(current)
      if (on && next.size < MAX_BULK) next.add(id)
      if (!on) next.delete(id)
      return next
    })
  const allVisible = items.length > 0 && items.every((job) => selected.has(job.id))
  const toggleVisible = (on: boolean) =>
    setSelected((current) => {
      const next = new Set(current)
      for (const job of items) {
        if (on && next.size < MAX_BULK) next.add(job.id)
        if (!on) next.delete(job.id)
      }
      return next
    })

  return (
    <SectionCard label="Bulk actions">
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <div
          role="group"
          aria-label="Bulk action"
          className="inline-flex overflow-hidden rounded-card border border-control-border bg-surface"
        >
          {MODES.map((option, i) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={mode === option.value}
              onClick={() => mode !== option.value && changeMode(option.value)}
              className={cn(
                'inline-flex h-9 items-center px-3.5 text-[13.5px] font-medium',
                i > 0 && 'border-l border-control-border',
                mode === option.value ? 'bg-neutral-fill font-semibold text-ink' : 'text-ink-2 hover:bg-surface-muted',
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
        <span className="text-[13px] text-muted">
          {mode === 'analyze'
            ? 'Jobs not analyzed yet (one EXTRACT call each).'
            : 'Analyzed jobs without a score that you have not marked Not eligible (one SCORE call each).'}
        </span>
        <span className="flex-1" />
        <span className="num text-[12.5px] text-muted" aria-live="polite">
          {selected.size} selected{full ? ` · max ${MAX_BULK}` : ''}
        </span>
        <Button variant="quiet" size="sm" disabled={selected.size === 0} onClick={() => setSelected(new Set())}>
          Clear
        </Button>
        <BulkActionDialog
          mode={mode}
          jobIds={[...selected]}
          budgetUsd={budgetUsd}
          onStarted={() => setSelected(new Set())}
        />
      </div>
      {candidates.isPending ? (
        <ListSkeleton rows={3} />
      ) : candidates.isError ? (
        <ErrorState error={candidates.error} title="Could not load the jobs" />
      ) : items.length === 0 ? (
        <p className="m-0 text-[13.5px] text-muted">
          {mode === 'analyze' ? 'Every job is analyzed.' : 'No analyzed job waits for a score.'}
        </p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-[13.5px]">
              <thead>
                <tr>
                  <th className={`${TH} w-10`}>
                    <Checkbox
                      aria-label="Select the jobs on this page"
                      checked={allVisible}
                      disabled={!allVisible && full}
                      onCheckedChange={(value) => toggleVisible(value === true)}
                    />
                  </th>
                  <th className={TH}>Title</th>
                  <th className={TH}>Company</th>
                  <th className={TH}>Eligibility</th>
                  <th className={TH}>Analysis</th>
                  <th className={TH}>Posted</th>
                </tr>
              </thead>
              <tbody>
                {items.map((job) => {
                  const checked = selected.has(job.id)
                  return (
                    <tr key={job.id} className={checked ? 'bg-accent-tint-2' : undefined}>
                      <td className={TD}>
                        <Checkbox
                          aria-label={`Select ${job.title}`}
                          checked={checked}
                          disabled={!checked && full}
                          onCheckedChange={(value) => toggle(job.id, value === true)}
                        />
                      </td>
                      <td className={`${TD} max-w-[360px] truncate`}>
                        <Link to={`/jobs/${job.id}`} className="text-ink" title={job.title}>
                          {job.title}
                        </Link>
                      </td>
                      <td className={`${TD} max-w-[200px] truncate text-ink-2`}>{job.company}</td>
                      <td className={TD}>
                        {job.eligibilityDecision ? (
                          <DecisionTag decision={job.eligibilityDecision} />
                        ) : job.checks && job.checks.unknown + job.checks.fail > 0 ? (
                          <ChecksTag checks={job.checks} />
                        ) : (
                          <span className="text-[12.5px] text-muted">Undecided</span>
                        )}
                      </td>
                      <td className={TD}>
                        <AnalysisTag stage={job.analysisStage} showNotAnalyzed />
                      </td>
                      <td className={`${TD} num text-[12.5px] text-muted`}>
                        {relativeTime(job.postedAt ?? job.firstSeenAt)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="mt-3">
            <Pagination
              page={page}
              pageSize={BULK_PAGE_SIZE}
              totalItems={candidates.data?.totalItems ?? 0}
              onChange={setPage}
            />
          </div>
        </>
      )}
    </SectionCard>
  )
}
