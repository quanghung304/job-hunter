import { useState } from 'react'
import { toast } from 'sonner'
import { type BulkAnalyzeDryRun, type BulkMode, type BulkScoreDryRun, useBulkAction } from '@/api/queries'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { errorText } from '@/features/job/useStatusChanger'
import { CostSummary } from './CostSummary'
import { paidBlocked } from './paidBlocked'

const SKIP_LABELS: Record<string, string> = {
  alreadyAnalyzed: 'already analyzed',
  alreadyScored: 'already scored',
  notAnalyzed: 'not analyzed',
  markedIneligible: 'marked not eligible',
  expired: 'expired',
  prefilterRejected: 'rejected by the prefilter',
  failedPermanently: 'failed 3 times',
  notFound: 'not found',
}

const VERB: Record<BulkMode, { title: string; button: string; running: string }> = {
  analyze: { title: 'Analyze', button: 'Analyze', running: 'Analyzing' },
  score: { title: 'Score', button: 'Score', running: 'Scoring' },
}

function willRun(plan: BulkAnalyzeDryRun | BulkScoreDryRun): number {
  return 'willAnalyze' in plan ? plan.willAnalyze : plan.willScore
}

function jobs(n: number): string {
  return `${n} ${n === 1 ? 'job' : 'jobs'}`
}

/**
 * "Analyze selected…" / "Score selected…" (ADR-0010): opening runs the dry run for the selected
 * jobs; the dialog shows how many will run, the skipped ones with reasons, the model and the
 * estimated calls and cost. Only the confirm button starts the LLM calls.
 */
export function BulkActionDialog({
  mode,
  jobIds,
  budgetUsd,
  onStarted,
}: {
  mode: BulkMode
  jobIds: number[]
  budgetUsd: number
  onStarted: () => void
}) {
  const [open, setOpen] = useState(false)
  const { dryRun, execute } = useBulkAction(mode)
  const plan = dryRun.data
  const count = plan ? willRun(plan) : 0
  const verb = VERB[mode]

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      execute.reset()
      dryRun.mutate(jobIds)
    }
  }

  const confirm = () =>
    execute.mutate(jobIds, {
      onSuccess: () => {
        toast(`${verb.running} ${jobs(count)} in the background`)
        setOpen(false)
        onStarted()
      },
      onError: (error) => toast.error(`${verb.title} not started: ${errorText(error)}`),
    })

  const skipped = plan ? Object.entries(plan.skipped as Record<string, number>).filter(([, value]) => value > 0) : []

  return (
    <>
      <Button variant="secondary" disabled={jobIds.length === 0} onClick={() => onOpenChange(true)}>
        {verb.title} selected…
      </Button>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="rounded-dialog shadow-dialog sm:max-w-[460px]">
          <DialogHeader>
            <DialogTitle className="text-[17px] font-semibold">
              {verb.title} {jobs(jobIds.length)}?
            </DialogTitle>
            <DialogDescription className="text-[13.5px] text-ink-2">
              {plan ? 'Skipped jobs make no LLM call.' : 'Checking the selected jobs…'}
            </DialogDescription>
          </DialogHeader>
          {dryRun.isError ? <ErrorState error={dryRun.error} title="Could not check the selected jobs" /> : null}
          {plan ? (
            <>
              <dl className="num m-0 grid grid-cols-[120px_1fr] gap-x-3 gap-y-1.5 text-[13.5px]">
                <dt className="font-sans text-muted">Will run</dt>
                <dd className="m-0 font-semibold text-ink">{count}</dd>
                <dt className="font-sans text-muted">Skipped</dt>
                <dd className="m-0 text-ink-2">
                  {skipped.length === 0
                    ? 0
                    : skipped.map(([key, value]) => `${value} ${SKIP_LABELS[key] ?? key}`).join(' · ')}
                </dd>
              </dl>
              <CostSummary
                model={plan.model}
                estimatedCalls={plan.estimatedCalls}
                estimatedCostUsd={plan.estimatedCostUsd}
                budgetUsd={budgetUsd}
              />
            </>
          ) : null}
          <DialogFooter>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              disabled={!plan || count === 0 || paidBlocked(plan.model) || execute.isPending}
              onClick={confirm}
            >
              {verb.button} {jobs(count)}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
