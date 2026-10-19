import { cn } from 'cn'
import type { JobSummary } from '@/api/queries'
import { ScoreBadge } from '@/components/ScoreBadge'
import { StatusTag } from '@/components/StatusTag'
import { relativeTime } from '@/lib/format'
import { CARD_MOTION, CardButtons, CardTitleLink, JobStateTags } from './JobCard'

/**
 * Horizontal job card (list panel, board list layout, mobile): ScoreBadge · title (2 lines) ·
 * "company · posted" · tags · star (and dismiss where given). `selected` marks the open job.
 */
export function JobListCard({
  job,
  search,
  selected = false,
  leaving = false,
  compact = false,
  onShortlistToggle,
  onDismiss,
}: {
  job: JobSummary
  search: string
  selected?: boolean
  leaving?: boolean
  /** List panel: radius 8px, 40×32 badge, 14px title. */
  compact?: boolean
  onShortlistToggle: () => void
  onDismiss?: () => void
}) {
  const when = job.postedAt ?? job.firstSeenAt
  return (
    <li data-job-id={job.id} className="list-none">
      <article
        aria-label={job.title}
        className={cn(
          'relative flex items-start gap-3 border bg-surface py-3 pr-2 pl-3',
          compact ? 'rounded-job-sm' : 'rounded-job',
          selected
            ? 'border-accent bg-accent-tint-2 shadow-card-ring motion-safe:-translate-y-0.5 motion-safe:shadow-card-selected'
            : 'border-card-border hover:border-card-border-hover hover:bg-surface-hover motion-safe:hover:-translate-y-[3px] motion-safe:hover:shadow-card-hover',
          'focus-within:border-accent',
          CARD_MOTION,
          leaving && 'pointer-events-none opacity-0',
        )}
      >
        <ScoreBadge score={job.score} size={compact ? 'sm' : 'card'} />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <h3
            className={cn(
              'm-0 line-clamp-2 leading-[1.35] font-semibold text-ink',
              compact ? 'text-[14px]' : 'text-[14.5px]',
            )}
          >
            <CardTitleLink job={job} search={search} current={selected} />
          </h3>
          <p className="m-0 truncate text-[13px] text-muted">
            {job.company} ·{' '}
            <span className="num text-[12px]" title={when}>
              {relativeTime(when)}
            </span>
          </p>
          <div className="flex flex-wrap gap-1.5 empty:hidden">
            <StatusTag status={job.status} hideNew />
            <JobStateTags job={job} />
          </div>
        </div>
        <CardButtons job={job} onShortlistToggle={onShortlistToggle} onDismiss={onDismiss} />
      </article>
    </li>
  )
}
