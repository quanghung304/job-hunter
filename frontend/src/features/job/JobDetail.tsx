import { ExternalLink } from 'lucide-react'
import type { Ref } from 'react'
import { cn } from 'cn'
import { type JobDetail as JobDetailData, useMeta } from '@/api/queries'
import { AnalysisTag } from '@/components/AnalysisTag'
import { ChecksTag } from '@/components/ChecksTag'
import { DecisionTag } from '@/components/DecisionTag'
import { FactChip } from '@/components/FactChip'
import { ScoreBadge } from '@/components/ScoreBadge'
import { StatusActions } from '@/components/StatusActions'
import { StatusTag } from '@/components/StatusTag'
import { Tag } from '@/components/Tag'
import { Button } from '@/components/ui/button'
import { postedAgo, salary } from '@/lib/format'
import { type Decision, EMPLOYMENT_LABELS, REMOTE_LABELS, type TrackingStatus } from '@/lib/labels'
import { AnalysisActions } from './AnalysisActions'
import { DescriptionPanel } from './DescriptionPanel'
import { EligibilityPanel } from './EligibilityPanel'
import { FactsPanel } from './FactsPanel'
import { HistoryList } from './HistoryList'
import { NotesEditor } from './NotesEditor'
import { ScoreBreakdown } from './ScoreBreakdown'
import { postingUrl } from './jobActions'
import { WhyItFits } from './WhyItFits'

/** Section cards inside the detail panel: radius 8px, padding 18px 20px (frontend.md split view). */
const PANEL_CARDS =
  '[&_[data-slot=section-card]]:rounded-[8px] [&_[data-slot=section-card]]:px-5 [&_[data-slot=section-card]]:py-[18px]'

/**
 * Job detail in the order of frontend.md "JobDetail content order". `layout="panel"` is the
 * desktop split view (sticky header with the action row); `layout="page"` is the mobile page,
 * where the fixed MobileActionBar carries the actions.
 */
export function JobDetail({
  job,
  onStatusChange,
  onDecisionChange,
  layout = 'panel',
  headingRef,
}: {
  job: JobDetailData
  onStatusChange: (to: TrackingStatus) => void
  onDecisionChange: (to: Decision | null) => void
  layout?: 'panel' | 'page'
  headingRef?: Ref<HTMLHeadingElement>
}) {
  const meta = useMeta()
  const language = meta.data?.summaryLanguage
  const pay = job.salary ? salary(job.salary) : null
  const url = postingUrl(job)
  const panel = layout === 'panel'

  const header = (
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
          <AnalysisTag stage={job.analysisStage} showNotAnalyzed />
          {job.eligibilityDecision ? (
            <DecisionTag decision={job.eligibilityDecision} />
          ) : (
            <ChecksTag checks={job.checks} />
          )}
          {job.jobStatus === 'EXPIRED' ? <Tag className="bg-neutral-fill text-muted">Expired</Tag> : null}
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
  )

  const actions = (
    <div className="mt-4 flex flex-wrap items-center gap-2">
      <StatusActions status={job.status} allowed={job.allowedTransitions} onChange={onStatusChange} />
      <AnalysisActions job={job} />
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
  )

  const scored = job.score !== null
  const body = (
    <>
      {job.analysisStage === 'NOT_ANALYZED' ? (
        <section className="flex flex-wrap items-center gap-3 rounded-card border border-dashed border-control-border bg-surface px-5 py-4">
          <div className="min-w-0 flex-1">
            <p className="m-0 font-semibold text-ink">Not analyzed yet</p>
            <p className="m-0 text-[13.5px] text-muted">
              Analyze reads the posting with one LLM call and checks each criterion; Score then rates the fit.
            </p>
          </div>
          {panel ? <AnalysisActions job={job} /> : null}
        </section>
      ) : null}
      {scored ? <WhyItFits job={job} language={language} /> : null}
      <EligibilityPanel job={job} onDecisionChange={onDecisionChange} />
      {scored ? <ScoreBreakdown job={job} /> : null}
      {job.extraction ? <FactsPanel job={job} /> : null}
      <DescriptionPanel key={`description-${job.id}`} job={job} />
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(320px,100%),1fr))] gap-4">
        <NotesEditor key={`notes-${job.id}`} jobId={job.id} initial={job.notes} />
        <HistoryList job={job} />
      </div>
    </>
  )

  if (!panel) {
    return (
      <article className="flex flex-col gap-4" aria-label={job.title}>
        {header}
        {body}
      </article>
    )
  }
  return (
    <article aria-label={job.title}>
      <div className="sticky top-0 z-10 border-b border-line bg-surface px-6 pt-[18px] pb-4">
        {header}
        {actions}
      </div>
      <div className={cn('flex flex-col gap-4 px-6 pt-5 pb-8', PANEL_CARDS)}>{body}</div>
    </article>
  )
}
