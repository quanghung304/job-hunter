import { ChevronLeft } from 'lucide-react'
import { type Ref, useEffect, useRef } from 'react'
import { Link } from 'react-router'
import type { JobSummary } from '@/api/queries'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { Button } from '@/components/ui/button'
import { JobListCard } from '@/features/board/JobListCard'
import { SearchField } from '@/features/board/SearchField'

/**
 * Left panel of the split view: "All jobs" back button, "24 of 30", the `q` filter, and its own
 * scroll area of list cards. "Load more" appends the next 12 without moving the scroll position
 * (stable keys, nothing remounts, no scrolling); the selected card is scrolled into view when it is
 * first shown or selected.
 */
export function JobListPanel({
  jobs,
  total,
  selectedId,
  cardSearch,
  backSearch,
  query,
  onQueryChange,
  searchRef,
  isPending,
  error,
  hasMore,
  loadingMore,
  onLoadMore,
  leavingId,
  onShortlistToggle,
  pageSize,
}: {
  jobs: JobSummary[]
  total: number | undefined
  selectedId: number | null
  /** Search params of the link of the card at this index (keeps the filters, page of that card). */
  cardSearch: (index: number) => string
  backSearch: string
  query: string
  onQueryChange: (q: string) => void
  searchRef?: Ref<HTMLInputElement>
  isPending: boolean
  error: unknown
  hasMore: boolean
  loadingMore: boolean
  onLoadMore: () => void
  leavingId: number | null
  onShortlistToggle: (job: JobSummary) => void
  pageSize: number
}) {
  const scrolledFor = useRef<number | null>(null)
  const present = selectedId !== null && jobs.some((job) => job.id === selectedId)

  useEffect(() => {
    if (!present || scrolledFor.current === selectedId) return
    scrolledFor.current = selectedId
    document.querySelector(`[data-job-id="${selectedId}"]`)?.scrollIntoView?.({ block: 'nearest' })
  }, [present, selectedId])

  const loaded = jobs.length
  const count = total === undefined ? '' : `${loaded} of ${total}`
  const next = total === undefined ? pageSize : Math.min(pageSize, total - loaded)

  let body
  if (isPending) {
    body = <ListSkeleton rows={5} />
  } else if (error) {
    body = <ErrorState error={error} title="Could not load jobs" />
  } else if (jobs.length === 0) {
    body = <EmptyState title="Inbox zero" text="No jobs match these filters." />
  } else {
    body = (
      <>
        <ul aria-label="Jobs" className="m-0 flex flex-col gap-2.5 p-0">
          {jobs.map((job, index) => (
            <JobListCard
              key={job.id}
              job={job}
              compact
              search={cardSearch(index)}
              selected={job.id === selectedId}
              leaving={job.id === leavingId}
              onShortlistToggle={() => onShortlistToggle(job)}
            />
          ))}
        </ul>
        {hasMore ? (
          <button
            type="button"
            onClick={onLoadMore}
            disabled={loadingMore}
            className="num mt-2.5 min-h-10 w-full rounded-job-sm border border-dashed border-control-border bg-surface px-3 text-[13px] text-ink-2 hover:bg-surface-muted disabled:text-muted"
          >
            {loadingMore ? 'Loading…' : `Load ${next} more · ${count}`}
          </button>
        ) : (
          <p className="num mt-2.5 mb-0 text-center text-[12.5px] text-muted">
            {loaded === 1 ? 'The only job is loaded' : `All ${loaded} jobs loaded`}
          </p>
        )}
      </>
    )
  }

  return (
    <section
      aria-label="Job list"
      className="flex w-[400px] shrink-0 flex-col overflow-hidden rounded-panel border border-card-border bg-surface"
    >
      <div className="flex flex-col gap-2.5 border-b border-line bg-surface-muted px-3 py-3">
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" asChild>
            <Link to={{ pathname: '/', search: backSearch }}>
              <ChevronLeft aria-hidden />
              All jobs
            </Link>
          </Button>
          <span className="num ml-auto text-[12.5px] text-muted">{count}</span>
        </div>
        <SearchField value={query} onChange={onQueryChange} inputRef={searchRef} className="w-full min-w-0" />
      </div>
      <div data-testid="job-list-scroll" className="min-h-0 flex-1 overflow-y-auto overscroll-contain bg-ground-2 p-3">
        {body}
      </div>
      <footer className="num border-t border-line bg-surface-muted px-3 py-2 text-[11.5px] text-muted">
        j/k move · s shortlist · d dismiss · e eligible · n not eligible · Esc back
      </footer>
    </section>
  )
}
