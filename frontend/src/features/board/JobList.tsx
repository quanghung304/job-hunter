import type { JobSummary } from '@/api/queries'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { Button } from '@/components/ui/button'
import type { Density } from './FilterBar'
import { JobRow } from './JobRow'

/** The board list: rows, "Load more", and (desktop) the sticky keyboard hint footer. */
export function JobList({
  jobs,
  search,
  selectedId,
  density,
  leavingId,
  isPending,
  error,
  filtered,
  hasMore,
  loadingMore,
  onLoadMore,
  onReset,
  onShortlistToggle,
  onDismiss,
  showHints,
}: {
  jobs: JobSummary[]
  search: string
  selectedId: number | null
  density: Density
  leavingId: number | null
  isPending: boolean
  error: unknown
  filtered: boolean
  hasMore: boolean
  loadingMore: boolean
  onLoadMore: () => void
  onReset: () => void
  onShortlistToggle: (job: JobSummary) => void
  onDismiss: (job: JobSummary) => void
  showHints: boolean
}) {
  let body
  if (isPending) {
    body = <ListSkeleton />
  } else if (error) {
    body = (
      <div className="p-4">
        <ErrorState error={error} title="Could not load jobs" />
      </div>
    )
  } else if (jobs.length === 0) {
    body = filtered ? (
      <EmptyState
        title="Inbox zero"
        text="No jobs match these filters."
        action={
          <Button variant="secondary" onClick={onReset}>
            Reset filters
          </Button>
        }
      />
    ) : (
      <EmptyState title="Inbox zero" text="Nothing new to review. The next pipeline run brings new jobs." />
    )
  } else {
    body = (
      <>
        <ul className="m-0 list-none p-0" aria-label="Jobs">
          {jobs.map((job) => (
            <JobRow
              key={job.id}
              job={job}
              search={search}
              selected={job.id === selectedId}
              density={density}
              leaving={job.id === leavingId}
              onShortlistToggle={() => onShortlistToggle(job)}
              onDismiss={() => onDismiss(job)}
            />
          ))}
        </ul>
        {hasMore ? (
          <div className="flex justify-center p-4">
            <Button variant="secondary" onClick={onLoadMore} disabled={loadingMore}>
              {loadingMore ? 'Loading…' : 'Load more'}
            </Button>
          </div>
        ) : null}
      </>
    )
  }

  return (
    <section aria-label="Job list" className="flex min-w-0 flex-col bg-surface lg:border-r lg:border-line">
      <div className="flex-1">{body}</div>
      {showHints ? (
        <footer className="num sticky bottom-0 border-t border-line bg-surface-muted px-4 py-2 text-[12px] text-muted">
          j/k move · Enter open · s shortlist · d dismiss · a applied · o posting · / search
        </footer>
      ) : null}
    </section>
  )
}
