import { Star, X } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from 'cn'
import type { JobSummary } from '@/api/queries'
import { AnalysisTag } from '@/components/AnalysisTag'
import { ChecksTag } from '@/components/ChecksTag'
import { DecisionTag } from '@/components/DecisionTag'
import { FactChip } from '@/components/FactChip'
import { ScoreBadge } from '@/components/ScoreBadge'
import { StatusTag } from '@/components/StatusTag'
import { Tag } from '@/components/Tag'
import { Button } from '@/components/ui/button'
import { relativeTime } from '@/lib/format'
import { EMPLOYMENT_LABELS } from '@/lib/labels'
import { canTransition } from '@/lib/statusMachine'

/** Analysis state and the owner's decision (or the criteria to check) of a card. */
export function JobStateTags({ job }: { job: JobSummary }) {
  return (
    <>
      <AnalysisTag stage={job.analysisStage} />
      {job.eligibilityDecision ? <DecisionTag decision={job.eligibilityDecision} /> : <ChecksTag checks={job.checks} />}
      {job.jobStatus === 'EXPIRED' ? <Tag className="bg-neutral-fill text-muted">Expired</Tag> : null}
    </>
  )
}

/** Star (shortlist toggle) and, when given, dismiss: 32×32 icon buttons above the stretched link (44×44 on mobile). */
export function CardButtons({
  job,
  onShortlistToggle,
  onDismiss,
}: {
  job: JobSummary
  onShortlistToggle: () => void
  onDismiss?: () => void
}) {
  const shortlisted = job.status === 'SHORTLISTED'
  const canShortlist = canTransition(job.status, shortlisted ? 'NEW' : 'SHORTLISTED')
  return (
    <span className="relative z-[1] flex shrink-0 items-center">
      {canShortlist ? (
        <Button
          variant="quiet"
          size="icon"
          className="max-lg:size-11"
          aria-label={shortlisted ? 'Remove from shortlist' : 'Shortlist'}
          aria-pressed={shortlisted}
          title={shortlisted ? 'Remove from shortlist (s)' : 'Shortlist (s)'}
          onClick={onShortlistToggle}
        >
          <Star
            className={cn('size-4', shortlisted ? 'fill-st-shortlisted-icon text-st-shortlisted-icon' : 'text-muted')}
            strokeWidth={1.8}
          />
        </Button>
      ) : null}
      {onDismiss && canTransition(job.status, 'DISMISSED') ? (
        <Button
          variant="quiet"
          size="icon"
          className="max-lg:size-11"
          aria-label="Dismiss"
          title="Dismiss (d)"
          onClick={onDismiss}
        >
          <X className="size-4 text-muted" strokeWidth={1.8} />
        </Button>
      ) : null}
    </span>
  )
}

/** The card's one click target: the title link, stretched over the whole card by `::after`. */
export function CardTitleLink({ job, search, current }: { job: JobSummary; search: string; current?: boolean }) {
  return (
    <Link
      to={{ pathname: `/jobs/${job.id}`, search }}
      aria-current={current ? 'true' : undefined}
      data-card-link
      className="text-inherit no-underline outline-none after:absolute after:inset-0 after:rounded-[inherit] after:content-['']"
    >
      {job.title}
    </Link>
  )
}

/** Card motion (frontend.md "Motion"): lift and shadow only without reduced motion; leaving cards fade out. */
export const CARD_MOTION =
  'motion-safe:transition-[transform,box-shadow,border-color,background-color,opacity] motion-safe:duration-150 motion-safe:ease-out'

/**
 * Grid card: ScoreBadge and status on top, the title (2 lines), "company · via sources", fact chips,
 * and a footer with analysis/decision tags, posted time, star and dismiss. No analysis buttons.
 */
export function JobCard({
  job,
  search,
  leaving = false,
  onShortlistToggle,
  onDismiss,
}: {
  job: JobSummary
  search: string
  leaving?: boolean
  onShortlistToggle: () => void
  onDismiss: () => void
}) {
  const when = job.postedAt ?? job.firstSeenAt
  return (
    <li data-job-id={job.id} className="list-none">
      <article
        aria-label={job.title}
        className={cn(
          'relative flex h-full min-h-[216px] flex-col gap-2.5 rounded-job border border-card-border bg-surface pt-4 pr-3.5 pb-3 pl-4',
          'hover:border-card-border-hover hover:bg-surface-hover focus-within:border-accent focus-within:shadow-card-ring',
          'motion-safe:hover:-translate-y-[3px] motion-safe:hover:shadow-card-hover',
          CARD_MOTION,
          leaving && 'pointer-events-none opacity-0',
        )}
      >
        <div className="flex items-start gap-2">
          <ScoreBadge score={job.score} size="card" />
          <span className="flex-1" />
          <StatusTag status={job.status} hideNew />
        </div>
        <h3 className="m-0 line-clamp-2 min-h-[2.7em] text-[15px] leading-[1.35] font-semibold text-ink">
          <CardTitleLink job={job} search={search} />
        </h3>
        <p className="m-0 truncate text-[13px] text-muted">
          {job.company}
          {job.sources.length ? ` · via ${job.sources.map((s) => s.name).join(', ')}` : ''}
        </p>
        <div className="flex flex-wrap gap-1.5">
          {job.employmentTypes.slice(0, 2).map((type) => (
            <FactChip key={type}>{EMPLOYMENT_LABELS[type]}</FactChip>
          ))}
          {job.allowedRegions.length ? <FactChip>{job.allowedRegions.slice(0, 3).join(', ')}</FactChip> : null}
        </div>
        <footer className="mt-auto flex items-center gap-1.5 border-t border-line-faint pt-2.5">
          <span className="flex min-w-0 flex-wrap gap-1.5">
            <JobStateTags job={job} />
          </span>
          <span className="flex-1" />
          <span className="num shrink-0 text-[12px] text-muted" title={when}>
            {relativeTime(when)}
          </span>
          <CardButtons job={job} onShortlistToggle={onShortlistToggle} onDismiss={onDismiss} />
        </footer>
      </article>
    </li>
  )
}
