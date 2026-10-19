import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { type JobDetail, type JobSummary, PAGE_SIZE, queryKeys, useJobs } from '@/api/queries'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { Button } from '@/components/ui/button'
import { postingUrl } from '@/features/job/jobActions'
import { FilterBar } from './FilterBar'
import { JobCard } from './JobCard'
import { JobListCard } from './JobListCard'
import { useBoardLayout } from './boardLayout'
import { MobileFilters } from './MobileFilters'
import { Pagination } from './Pagination'
import { StatusLegend } from './StatusLegend'
import { useCardActions } from './useCardActions'
import { toApiQuery, useBoardFilters } from './useBoardFilters'
import { useBoardKeyboard } from './useBoardKeyboard'

function focusCard(id: number) {
  const card = document.querySelector<HTMLElement>(`[data-job-id="${id}"]`)
  card?.querySelector<HTMLElement>('[data-card-link]')?.focus()
  card?.scrollIntoView?.({ block: 'nearest' })
}

/**
 * `/`: the board. Desktop: filter bar, legend, a grid (or list) of 12 cards and numbered pages;
 * the keyboard acts on the focused card. Mobile: filter pills and one column of list cards.
 */
export function BoardGrid({ isDesktop }: { isDesktop: boolean }) {
  const { filters, update, reset, activeCount, page, setPage, search } = useBoardFilters()
  const query = useMemo(() => toApiQuery(filters), [filters])
  const jobs = useJobs(query, page)
  const items = useMemo(() => jobs.data?.items ?? [], [jobs.data])
  const totalItems = jobs.data?.totalItems ?? 0
  const totalPages = jobs.data?.totalPages ?? 0
  const [layout, setLayout] = useBoardLayout()
  const actions = useCardActions(query)
  const client = useQueryClient()
  const searchRef = useRef<HTMLInputElement>(null)
  const topRef = useRef<HTMLDivElement>(null)
  const [activeId, setActiveId] = useState<number | null>(null)

  // A page past the end (e.g. the last card of the last page dismissed): show the last page.
  useEffect(() => {
    if (!jobs.isPlaceholderData && totalPages > 0 && page > totalPages) setPage(totalPages, true)
  }, [jobs.isPlaceholderData, page, totalPages, setPage])

  const activeIndex = items.findIndex((job) => job.id === activeId)
  const active = activeIndex >= 0 ? items[activeIndex] : undefined

  const changePage = (next: number) => {
    setPage(next)
    topRef.current?.scrollIntoView?.({ block: 'start' })
  }

  /** Dismiss; with the keyboard the focus moves to the neighbour of the leaving card. */
  const dismiss = (job: JobSummary, fromKeyboard = false) =>
    actions.changeStatus(job, 'DISMISSED', () => {
      if (!fromKeyboard) return
      const index = items.findIndex((j) => j.id === job.id)
      const neighbor = items[index + 1] ?? items[index - 1]
      if (neighbor) {
        setActiveId(neighbor.id)
        focusCard(neighbor.id)
      }
    })

  useBoardKeyboard(
    {
      next: () => {
        const next = items[activeIndex + 1] ?? (activeIndex < 0 ? items[0] : undefined)
        if (next) focusCard(next.id)
      },
      previous: () => {
        const previous = items[activeIndex - 1]
        if (previous) focusCard(previous.id)
      },
      shortlist: () => active && actions.shortlistToggle(active),
      dismiss: () => active && dismiss(active, true),
      applied: () => active && actions.changeStatus(active, 'APPLIED'),
      eligible: () => active && actions.toggleDecision(active, 'ELIGIBLE'),
      notEligible: () => active && actions.toggleDecision(active, 'INELIGIBLE'),
      posting: () => {
        const detail = active ? client.getQueryData<JobDetail>(queryKeys.job(active.id)) : undefined
        const url = detail ? postingUrl(detail) : undefined
        if (url) window.open(url, '_blank', 'noopener,noreferrer')
      },
      focusSearch: () => searchRef.current?.focus(),
    },
    isDesktop,
  )

  let content
  if (jobs.isPending) {
    content = <ListSkeleton />
  } else if (jobs.isError) {
    content = <ErrorState error={jobs.error} title="Could not load jobs" />
  } else if (items.length === 0) {
    content =
      activeCount > 0 ? (
        <EmptyState
          title="Inbox zero"
          text="No jobs match these filters."
          action={
            <Button variant="secondary" onClick={reset}>
              Reset filters
            </Button>
          }
        />
      ) : (
        <EmptyState title="Inbox zero" text="Nothing new to review. The next pipeline run brings new jobs." />
      )
  } else {
    const grid = isDesktop && layout === 'grid'
    content = (
      <>
        <ul
          aria-label="Jobs"
          className={
            grid
              ? 'm-0 grid grid-cols-1 gap-4 p-0 min-[700px]:grid-cols-2 min-[1100px]:grid-cols-3'
              : 'm-0 flex flex-col gap-2.5 p-0'
          }
          onFocus={(event) => {
            const id = (event.target as HTMLElement).closest<HTMLElement>('[data-job-id]')?.dataset.jobId
            if (id) setActiveId(Number(id))
          }}
        >
          {items.map((job) =>
            grid ? (
              <JobCard
                key={job.id}
                job={job}
                search={search}
                leaving={job.id === actions.leavingId}
                onShortlistToggle={() => actions.shortlistToggle(job)}
                onDismiss={() => dismiss(job)}
              />
            ) : (
              <JobListCard
                key={job.id}
                job={job}
                search={search}
                leaving={job.id === actions.leavingId}
                onShortlistToggle={() => actions.shortlistToggle(job)}
                onDismiss={() => dismiss(job)}
              />
            ),
          )}
        </ul>
        <Pagination page={page} pageSize={PAGE_SIZE} totalItems={totalItems} onChange={changePage} />
      </>
    )
  }

  return (
    <main>
      <h1 className="sr-only">Board</h1>
      {isDesktop ? (
        <FilterBar
          filters={filters}
          update={update}
          reset={reset}
          activeCount={activeCount}
          layout={layout}
          onLayoutChange={setLayout}
          searchRef={searchRef}
        />
      ) : (
        <MobileFilters
          filters={filters}
          update={update}
          reset={reset}
          activeCount={activeCount}
          total={jobs.data?.totalItems}
          searchRef={searchRef}
        />
      )}
      <div
        ref={topRef}
        className="mx-auto flex max-w-[1320px] scroll-mt-4 flex-col gap-4 px-3 pt-3 pb-8 lg:px-6 lg:pt-5"
      >
        {isDesktop ? <StatusLegend /> : null}
        {content}
      </div>
    </main>
  )
}
