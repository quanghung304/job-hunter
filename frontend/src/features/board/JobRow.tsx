import { Star, X } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from 'cn'
import type { JobSummary } from '@/api/queries'
import { ChecksTag } from '@/components/ChecksTag'
import { DecisionTag } from '@/components/DecisionTag'
import { FactChip } from '@/components/FactChip'
import { ScoreBadge } from '@/components/ScoreBadge'
import { StatusTag } from '@/components/StatusTag'
import { Tag } from '@/components/Tag'
import { Button } from '@/components/ui/button'
import { AnalysisActions } from '@/features/job/AnalysisActions'
import { relativeTime } from '@/lib/format'
import { EMPLOYMENT_LABELS } from '@/lib/labels'
import { canTransition } from '@/lib/statusMachine'
import type { Density } from './FilterBar'

/**
 * One board row: a link that selects the job (score, title, company · sources, summary, tags),
 * the posted time, and the icon column (analysis action, shortlist toggle, dismiss).
 */
export function JobRow({
  job,
  search,
  selected,
  density,
  leaving,
  onShortlistToggle,
  onDismiss,
}: {
  job: JobSummary
  search: string
  selected: boolean
  density: Density
  leaving: boolean
  onShortlistToggle: () => void
  onDismiss: () => void
}) {
  const shortlisted = job.status === 'SHORTLISTED'
  const canShortlist = canTransition(job.status, shortlisted ? 'NEW' : 'SHORTLISTED')
  const compact = density === 'compact'

  return (
    <li
      data-job-id={job.id}
      className={cn(
        'grid overflow-hidden border-b border-line-soft transition-[grid-template-rows,opacity] duration-150 ease-out',
        leaving ? 'grid-rows-[0fr] opacity-0' : 'grid-rows-[1fr] opacity-100',
        selected && 'bg-accent-tint-2',
      )}
    >
      <div className="flex min-h-0 items-start">
        <Link
          to={{ pathname: `/jobs/${job.id}`, search }}
          aria-current={selected ? 'true' : undefined}
          className={cn('flex min-w-0 flex-1 gap-3 text-inherit no-underline', compact ? 'py-2.5 pl-4' : 'py-3.5 pl-4')}
        >
          <ScoreBadge score={job.score} />
          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="flex items-start gap-2">
              <span className="min-w-0 flex-1 text-[14.5px] font-semibold text-ink max-lg:line-clamp-2 lg:truncate">
                {job.title}
              </span>
              <span className="num shrink-0 pt-0.5 text-[12px] text-muted" title={job.postedAt ?? job.firstSeenAt}>
                {relativeTime(job.postedAt ?? job.firstSeenAt)}
              </span>
            </span>
            <span className="truncate text-[13px] text-muted">
              {job.company}
              {job.sources.length ? ` · ${job.sources.map((s) => s.name).join(', ')}` : ''}
            </span>
            {!compact && job.summary ? (
              <span className="line-clamp-2 text-[13px] text-ink-2 max-lg:hidden">{job.summary}</span>
            ) : null}
            <span className="mt-1 flex flex-wrap gap-1.5">
              {job.eligibilityDecision ? (
                <DecisionTag decision={job.eligibilityDecision} />
              ) : (
                <ChecksTag checks={job.checks} />
              )}
              <StatusTag status={job.status} hideNew />
              {job.analysisStage === 'FAILED' ? <Tag className="bg-strong text-white">Analysis failed</Tag> : null}
              {job.jobStatus === 'EXPIRED' ? <Tag className="bg-neutral-fill text-muted">Expired</Tag> : null}
              {job.employmentTypes.slice(0, 2).map((type) => (
                <FactChip key={type}>{EMPLOYMENT_LABELS[type]}</FactChip>
              ))}
              {job.allowedRegions.length ? <FactChip>{job.allowedRegions.slice(0, 3).join(', ')}</FactChip> : null}
            </span>
          </span>
        </Link>
        <div className={cn('flex shrink-0 flex-col items-center gap-0.5 px-2', compact ? 'py-2' : 'py-3')}>
          <AnalysisActions job={job} variant="icon" />
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
                className={cn('size-4', shortlisted ? 'fill-accent text-accent' : 'text-muted')}
                strokeWidth={1.8}
              />
            </Button>
          ) : null}
          {canTransition(job.status, 'DISMISSED') ? (
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
        </div>
      </div>
    </li>
  )
}
