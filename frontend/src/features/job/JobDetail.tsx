import { ExternalLink } from 'lucide-react'
import type { Ref } from 'react'
import { type JobDetail as JobDetailData, useMeta } from '@/api/queries'
import { EligibilityTag } from '@/components/EligibilityTag'
import { FactChip } from '@/components/FactChip'
import { ScoreBadge } from '@/components/ScoreBadge'
import { StatusActions } from '@/components/StatusActions'
import { StatusTag } from '@/components/StatusTag'
import { Tag } from '@/components/Tag'
import { Button } from '@/components/ui/button'
import { postedAgo, salary } from '@/lib/format'
import { EMPLOYMENT_LABELS, REMOTE_LABELS, type TrackingStatus } from '@/lib/labels'
import { AnalysisActions } from './AnalysisActions'
import { DescriptionPanel } from './DescriptionPanel'
import { EligibilityPanel } from './EligibilityPanel'
import { FactsPanel } from './FactsPanel'
import { HistoryList } from './HistoryList'
import { NotesEditor } from './NotesEditor'
import { ScoreBreakdown } from './ScoreBreakdown'
import { postingUrl } from './jobActions'
import { WhyItFits } from './WhyItFits'

/**
 * Job detail in the order of frontend.md "JobDetail content order". `showActions` is false on
 * mobile, where the fixed MobileActionBar carries the actions.
 */
export function JobDetail({
  job,
  onStatusChange,
  showActions = true,
  headingRef,
}: {
  job: JobDetailData
  onStatusChange: (to: TrackingStatus) => void
  showActions?: boolean
  headingRef?: Ref<HTMLHeadingElement>
}) {
  const meta = useMeta()
  const language = meta.data?.summaryLanguage
  const pay = job.salary ? salary(job.salary) : null
  const url = postingUrl(job)

  return (
    <article className="flex flex-col gap-5" aria-label={job.title}>
      <header className="flex gap-4">
        <ScoreBadge score={job.score} size="lg" />
        <div className="min-w-0">
          <h1
            ref={headingRef}
            tabIndex={-1}
            className="m-0 text-[19px] leading-tight font-semibold tracking-[-0.01em] text-ink outline-none lg:text-[22px]"
          >
            {job.title}
          </h1>
          <p className="mt-1 mb-0 text-[13.5px] text-muted">
            {[job.company, job.locationText].filter(Boolean).join(' · ')} ·{' '}
            <span className="num">{postedAgo(job.postedAt)}</span>
          </p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            <StatusTag status={job.status} />
            {job.eligibility ? <EligibilityTag eligibility={job.eligibility} /> : null}
            {job.jobStatus === 'EXPIRED' ? <Tag className="bg-neutral-fill text-muted">Expired</Tag> : null}
            {job.analysisStage === 'FAILED' ? <Tag className="bg-strong text-white">Analysis failed</Tag> : null}
            {job.remoteType ? <FactChip emphasis>{REMOTE_LABELS[job.remoteType]}</FactChip> : null}
            {job.employmentTypes.map((type) => (
              <FactChip key={type} emphasis>
                {EMPLOYMENT_LABELS[type]}
              </FactChip>
            ))}
            {job.allowedRegions.length ? <FactChip emphasis>{job.allowedRegions.join(', ')}</FactChip> : null}
            {pay ? (
              <FactChip emphasis>
                <span className="num">{pay}</span>
              </FactChip>
            ) : null}
          </div>
        </div>
      </header>

      {showActions ? (
        <div className="flex flex-wrap items-center gap-2">
          <AnalysisActions job={job} />
          <StatusActions status={job.status} allowed={job.allowedTransitions} onChange={onStatusChange} />
          <span className="flex-1" />
          {url ? (
            <Button variant="secondary" asChild>
              <a href={url} target="_blank" rel="noopener noreferrer">
                Open posting <ExternalLink aria-hidden />
                <span className="num text-[12px] opacity-70">o</span>
              </a>
            </Button>
          ) : null}
          {job.applyUrl ? (
            <Button variant="leave-app" asChild>
              <a href={job.applyUrl} target="_blank" rel="noopener noreferrer">
                Apply <ExternalLink aria-hidden />
              </a>
            </Button>
          ) : null}
        </div>
      ) : null}

      <WhyItFits job={job} language={language} />
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(320px,100%),1fr))] gap-5">
        <ScoreBreakdown job={job} />
        <EligibilityPanel job={job} />
      </div>
      <FactsPanel job={job} />
      <DescriptionPanel key={`description-${job.id}`} job={job} />
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(320px,100%),1fr))] gap-5">
        <NotesEditor key={`notes-${job.id}`} jobId={job.id} initial={job.notes} />
        <HistoryList job={job} />
      </div>
    </article>
  )
}
