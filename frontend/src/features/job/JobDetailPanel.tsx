import { type Ref, useLayoutEffect, useRef } from 'react'
import { useJob } from '@/api/queries'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import type { Decision, TrackingStatus } from '@/lib/labels'
import { JobDetail } from './JobDetail'

/**
 * Right panel of the split view: the job's detail with its own scroll and a sticky header.
 * Selecting another job resets this scroll to the top (the list keeps its own).
 */
export function JobDetailPanel({
  id,
  headingRef,
  onStatusChange,
  onDecisionChange,
}: {
  id: number
  headingRef?: Ref<HTMLHeadingElement>
  onStatusChange: (to: TrackingStatus, from: TrackingStatus) => void
  onDecisionChange: (to: Decision | null, from: Decision | null) => void
}) {
  const job = useJob(id)
  const scrollRef = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = 0
  }, [id])

  return (
    <section
      aria-label="Job detail"
      className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-panel border border-card-border bg-surface"
    >
      <div
        ref={scrollRef}
        data-testid="job-detail-scroll"
        className="min-h-0 flex-1 overflow-y-auto overscroll-contain bg-ground-2"
      >
        {job.isPending ? (
          <div className="p-6">
            <ListSkeleton rows={4} />
          </div>
        ) : job.isError ? (
          <div className="p-6">
            <ErrorState error={job.error} title="Could not load the job" />
          </div>
        ) : (
          <JobDetail
            job={job.data}
            headingRef={headingRef}
            onStatusChange={(to) => onStatusChange(to, job.data.status)}
            onDecisionChange={(to) => onDecisionChange(to, job.data.eligibilityDecision)}
          />
        )}
      </div>
    </section>
  )
}
