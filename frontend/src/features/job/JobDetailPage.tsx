import { useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ExternalLink } from 'lucide-react'
import { Link } from 'react-router'
import { cachedJobIds, useJob } from '@/api/queries'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { toApiQuery, useBoardFilters } from '@/features/board/useBoardFilters'
import { JobDetail } from './JobDetail'
import { postingUrl } from './jobActions'
import { MobileActionBar } from './MobileActionBar'
import { useStatusChanger } from './useStatusChanger'

/**
 * Mobile job detail (< 1024px): back bar, "Job N of M" from the cached board list (hidden when
 * the page was opened directly), the detail, and the fixed action bar.
 */
export function JobDetailPage({ id }: { id: number }) {
  const job = useJob(id)
  const { filters, search } = useBoardFilters()
  const client = useQueryClient()
  const { change } = useStatusChanger()
  const cached = cachedJobIds(client, toApiQuery(filters))
  const position = cached ? cached.ids.indexOf(id) : -1
  const url = job.data ? postingUrl(job.data) : undefined

  return (
    <main className="pb-28">
      <nav className="flex h-12 items-center gap-2 border-b border-line bg-surface px-2" aria-label="Job navigation">
        <Link
          to={{ pathname: '/', search }}
          className="inline-flex size-11 items-center justify-center rounded-control text-ink-2"
          aria-label="Back to the board"
        >
          <ChevronLeft className="size-5" />
        </Link>
        {cached && position >= 0 ? (
          <span className="num text-[13px] text-muted">
            Job {position + 1} of {cached.total}
          </span>
        ) : null}
        {url ? (
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="ml-auto inline-flex min-h-11 items-center gap-1 px-3 text-[13.5px] text-accent-deep"
          >
            Posting <ExternalLink className="size-4" aria-hidden />
          </a>
        ) : null}
      </nav>
      <div className="px-4 py-4">
        {job.isPending ? (
          <ListSkeleton rows={3} />
        ) : job.isError ? (
          <ErrorState error={job.error} title="Could not load the job" />
        ) : (
          <JobDetail
            job={job.data}
            showActions={false}
            onStatusChange={(to) => change(job.data.id, job.data.status, to)}
          />
        )}
      </div>
      {job.data ? (
        <MobileActionBar job={job.data} onStatusChange={(to) => change(job.data.id, job.data.status, to)} />
      ) : null}
    </main>
  )
}
