import type { Ref } from 'react'
import { useJob } from '@/api/queries'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { JobDetail } from '@/features/job/JobDetail'
import type { TrackingStatus } from '@/lib/labels'

/** Right column of the desktop split view: the selected job's detail. */
export function JobDetailPane({
  jobId,
  onStatusChange,
  headingRef,
}: {
  jobId: number | null
  onStatusChange: (to: TrackingStatus, from: TrackingStatus) => void
  headingRef: Ref<HTMLHeadingElement>
}) {
  const job = useJob(jobId)
  return (
    <section aria-label="Job detail" className="min-w-0 flex-[999_1_560px] bg-ground px-8 pt-7 pb-14">
      {jobId === null ? (
        <EmptyState title="No job selected" text="Pick a job from the list, or press j." />
      ) : job.isPending ? (
        <ListSkeleton rows={4} />
      ) : job.isError ? (
        <ErrorState error={job.error} title="Could not load the job" />
      ) : (
        <JobDetail
          job={job.data}
          headingRef={headingRef}
          onStatusChange={(to) => onStatusChange(to, job.data.status)}
        />
      )}
    </section>
  )
}
