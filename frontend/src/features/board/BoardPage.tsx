import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { type JobDetail, type JobSummary, queryKeys, statusVisibleIn, useJobs } from '@/api/queries'
import { JobDetailPage } from '@/features/job/JobDetailPage'
import { postingUrl } from '@/features/job/jobActions'
import { useStatusChanger } from '@/features/job/useStatusChanger'
import type { TrackingStatus } from '@/lib/labels'
import { canTransition } from '@/lib/statusMachine'
import { useIsDesktop } from '@/lib/useMediaQuery'
import { type Density, FilterBar } from './FilterBar'
import { JobDetailPane } from './JobDetailPane'
import { JobList } from './JobList'
import { MobileFilters } from './MobileFilters'
import { toApiQuery, useBoardFilters } from './useBoardFilters'
import { useBoardKeyboard } from './useBoardKeyboard'

const DENSITY_KEY = 'jobHunter.density'
/** Row collapse before the job leaves the list (frontend.md "Motion"). */
export const LEAVE_MS = 150

function readDensity(): Density {
  try {
    return window.localStorage.getItem(DENSITY_KEY) === 'compact' ? 'compact' : 'comfortable'
  } catch {
    return 'comfortable'
  }
}

function writeDensity(density: Density) {
  try {
    window.localStorage.setItem(DENSITY_KEY, density)
  } catch {
    // storage unavailable (private mode): keep it for this page only
  }
}

function reducedMotion(): boolean {
  return typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

/**
 * `/` and `/jobs/:id`. Desktop: filter bar, list and detail pane (split view), keyboard first.
 * Mobile: the list on `/`, the full-page detail on `/jobs/:id`.
 */
export function BoardPage() {
  const params = useParams()
  const routeId = params.id !== undefined && /^\d+$/.test(params.id) ? Number(params.id) : null
  const isDesktop = useIsDesktop()
  if (!isDesktop && routeId !== null) {
    return <JobDetailPage key={routeId} id={routeId} />
  }
  return <Board routeId={routeId} isDesktop={isDesktop} />
}

function Board({ routeId, isDesktop }: { routeId: number | null; isDesktop: boolean }) {
  const { filters, update, reset, activeCount, search } = useBoardFilters()
  const query = useMemo(() => toApiQuery(filters), [filters])
  const jobs = useJobs(query)
  const items = useMemo(() => jobs.data?.pages.flatMap((page) => page.items) ?? [], [jobs.data])
  const total = jobs.data?.pages[jobs.data.pages.length - 1]?.totalItems
  const navigate = useNavigate()
  const client = useQueryClient()
  const { change } = useStatusChanger()
  const [density, setDensity] = useState<Density>(readDensity)
  const [leavingId, setLeavingId] = useState<number | null>(null)
  const searchRef = useRef<HTMLInputElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const scrollOnSelect = useRef(false)

  const selectedId = isDesktop ? (routeId ?? items[0]?.id ?? null) : null
  const selectedIndex = items.findIndex((job) => job.id === selectedId)
  const selected = selectedIndex >= 0 ? items[selectedIndex] : undefined

  useEffect(() => {
    if (!scrollOnSelect.current || selectedId === null) return
    scrollOnSelect.current = false
    document.querySelector(`[data-job-id="${selectedId}"]`)?.scrollIntoView?.({ block: 'nearest' })
  }, [selectedId])

  const select = (id: number, fromKeyboard = false) => {
    scrollOnSelect.current = fromKeyboard
    navigate({ pathname: `/jobs/${id}`, search }, { replace: fromKeyboard && routeId !== null })
  }

  /** Changes the status; a job leaving this list collapses first and the next one gets selected. */
  const changeStatus = (id: number, from: TrackingStatus, to: TrackingStatus) => {
    if (!canTransition(from, to)) return
    const index = items.findIndex((job) => job.id === id)
    if (index < 0 || statusVisibleIn(query, to)) {
      change(id, from, to)
      return
    }
    const neighbor = items[index + 1] ?? items[index - 1]
    if (isDesktop && id === selectedId && neighbor) select(neighbor.id, true)
    if (reducedMotion()) {
      change(id, from, to)
      return
    }
    setLeavingId(id)
    setTimeout(() => {
      setLeavingId(null)
      change(id, from, to)
    }, LEAVE_MS)
  }

  const shortlistToggle = (job: JobSummary) =>
    changeStatus(job.id, job.status, job.status === 'SHORTLISTED' ? 'NEW' : 'SHORTLISTED')

  useBoardKeyboard(
    {
      next: () => {
        const next = items[selectedIndex + 1] ?? (selectedIndex < 0 ? items[0] : undefined)
        if (next) select(next.id, true)
      },
      previous: () => {
        const previous = items[selectedIndex - 1]
        if (previous) select(previous.id, true)
      },
      open: () => {
        if (selected) headingRef.current?.focus()
      },
      shortlist: () => {
        if (selected) shortlistToggle(selected)
      },
      dismiss: () => {
        if (selected) changeStatus(selected.id, selected.status, 'DISMISSED')
      },
      applied: () => {
        if (selected) changeStatus(selected.id, selected.status, 'APPLIED')
      },
      posting: () => {
        if (selectedId === null) return
        const detail = client.getQueryData<JobDetail>(queryKeys.job(selectedId))
        const url = detail ? postingUrl(detail) : undefined
        if (url) window.open(url, '_blank', 'noopener,noreferrer')
      },
      focusSearch: () => searchRef.current?.focus(),
    },
    isDesktop,
  )

  const list = (
    <JobList
      jobs={items}
      search={search}
      selectedId={selectedId}
      density={isDesktop ? density : 'comfortable'}
      leavingId={leavingId}
      isPending={jobs.isPending}
      error={jobs.isError ? jobs.error : null}
      filtered={activeCount > 0}
      hasMore={jobs.hasNextPage}
      loadingMore={jobs.isFetchingNextPage}
      onLoadMore={() => void jobs.fetchNextPage()}
      onReset={reset}
      onShortlistToggle={shortlistToggle}
      onDismiss={(job) => changeStatus(job.id, job.status, 'DISMISSED')}
      showHints={isDesktop}
    />
  )

  return (
    <main>
      <h1 className="sr-only">Board</h1>
      {isDesktop ? (
        <>
          <FilterBar
            filters={filters}
            update={update}
            reset={reset}
            activeCount={activeCount}
            total={total}
            density={density}
            onDensityChange={(next) => {
              setDensity(next)
              writeDensity(next)
            }}
            searchRef={searchRef}
          />
          <div className="flex flex-wrap items-start">
            <div className="flex max-w-[600px] min-w-0 flex-[1_1_440px] self-stretch">
              <div className="flex-1">{list}</div>
            </div>
            <JobDetailPane
              jobId={selectedId}
              headingRef={headingRef}
              onStatusChange={(to, from) => selectedId !== null && changeStatus(selectedId, from, to)}
            />
          </div>
        </>
      ) : (
        <>
          <MobileFilters
            filters={filters}
            update={update}
            reset={reset}
            activeCount={activeCount}
            total={total}
            searchRef={searchRef}
          />
          {list}
        </>
      )}
    </main>
  )
}
