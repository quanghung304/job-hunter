import { useQueryClient } from '@tanstack/react-query'
import { useMemo, useRef } from 'react'
import { useNavigate } from 'react-router'
import { type JobDetail, PAGE_SIZE, queryKeys, useInfiniteJobs } from '@/api/queries'
import { useCardActions } from '@/features/board/useCardActions'
import { toApiQuery, useBoardFilters } from '@/features/board/useBoardFilters'
import { useBoardKeyboard } from '@/features/board/useBoardKeyboard'
import { JobDetailPanel } from './JobDetailPanel'
import { postingUrl } from './jobActions'
import { JobListPanel } from './JobListPanel'

/** The 1-based grid page of the card at this index. */
function pageOf(index: number): number {
  return Math.floor(index / PAGE_SIZE) + 1
}

/**
 * `/jobs/:id` on desktop: the job list (left) and the detail (right), each with its own scroll; the
 * page itself does not scroll. The list is seeded with the grid pages up to `page`, and the URL's
 * `page` follows the selected job, so "All jobs", Esc and a reload return to the right page.
 * Selecting another job re-renders this view; it never remounts the list.
 */
export function JobSplitView({ id }: { id: number }) {
  const { filters, update, page, search, searchWithPage } = useBoardFilters()
  const query = useMemo(() => toApiQuery(filters), [filters])
  const list = useInfiniteJobs(query, page)
  const items = useMemo(() => list.data?.pages.flatMap((p) => p.items) ?? [], [list.data])
  const total = list.data?.pages[list.data.pages.length - 1]?.totalItems
  const actions = useCardActions(query)
  const navigate = useNavigate()
  const client = useQueryClient()
  const searchRef = useRef<HTMLInputElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)

  const index = items.findIndex((job) => job.id === id)
  const backSearch = index >= 0 ? searchWithPage(pageOf(index)) : search

  const select = (jobId: number) => {
    const at = items.findIndex((job) => job.id === jobId)
    navigate({ pathname: `/jobs/${jobId}`, search: searchWithPage(pageOf(at)) }, { replace: true })
  }

  /** The selected job as the list and the detail know it (the detail when it is not in the list). */
  const current = () => {
    const detail = client.getQueryData<JobDetail>(queryKeys.job(id))
    return items[index] ?? detail
  }

  /** Selects the next card (or the previous) when the selected job leaves the list. */
  const selectNeighbor = () => {
    const neighbor = items[index + 1] ?? items[index - 1]
    if (neighbor) select(neighbor.id)
  }

  useBoardKeyboard({
    next: () => {
      const next = items[index + 1] ?? (index < 0 ? items[0] : undefined)
      if (next) select(next.id)
    },
    previous: () => {
      const previous = items[index - 1]
      if (previous) select(previous.id)
    },
    open: () => headingRef.current?.focus(),
    shortlist: () => {
      const job = current()
      if (job) actions.shortlistToggle(job, selectNeighbor)
    },
    dismiss: () => {
      const job = current()
      if (job) actions.changeStatus(job, 'DISMISSED', selectNeighbor)
    },
    applied: () => {
      const job = current()
      if (job) actions.changeStatus(job, 'APPLIED', selectNeighbor)
    },
    eligible: () => {
      const job = current()
      if (job) actions.toggleDecision(job, 'ELIGIBLE')
    },
    notEligible: () => {
      const job = current()
      if (job) actions.toggleDecision(job, 'INELIGIBLE')
    },
    posting: () => {
      const detail = client.getQueryData<JobDetail>(queryKeys.job(id))
      const url = detail ? postingUrl(detail) : undefined
      if (url) window.open(url, '_blank', 'noopener,noreferrer')
    },
    focusSearch: () => searchRef.current?.focus(),
    back: () => navigate({ pathname: '/', search: backSearch }),
  })

  return (
    <main className="flex min-h-0 flex-1 gap-5 px-6 pt-5 pb-6">
      <h1 className="sr-only">Board</h1>
      <JobListPanel
        jobs={items}
        total={total}
        selectedId={id}
        cardSearch={(at) => searchWithPage(pageOf(at))}
        backSearch={backSearch}
        query={filters.q}
        onQueryChange={(q) => update({ q })}
        searchRef={searchRef}
        isPending={list.isPending}
        error={list.isError ? list.error : null}
        hasMore={list.hasNextPage}
        loadingMore={list.isFetchingNextPage}
        onLoadMore={() => void list.fetchNextPage()}
        leavingId={actions.leavingId}
        onShortlistToggle={(job) => actions.shortlistToggle(job, job.id === id ? selectNeighbor : undefined)}
        pageSize={PAGE_SIZE}
      />
      <JobDetailPanel
        id={id}
        headingRef={headingRef}
        onStatusChange={(to, from) => {
          const job = current()
          if (job) actions.changeStatus({ ...job, status: from }, to, selectNeighbor)
        }}
        onDecisionChange={(to, from) => {
          const job = current()
          if (job) actions.decide({ ...job, eligibilityDecision: from }, to)
        }}
      />
    </main>
  )
}
