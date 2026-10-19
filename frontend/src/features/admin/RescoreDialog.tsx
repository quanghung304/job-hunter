import { useState } from 'react'
import { toast } from 'sonner'
import { useRescore } from '@/api/queries'
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

/**
 * "Re-score active jobs…" (ADR-0010): opening runs the dry run; the dialog shows the job count,
 * model and estimated cost, and only "Re-score N jobs" starts the LLM calls.
 */
export function RescoreDialog({ budgetUsd, disabled }: { budgetUsd: number; disabled?: boolean }) {
  const [open, setOpen] = useState(false)
  const { dryRun, execute } = useRescore()
  const plan = dryRun.data

  const onOpenChange = (next: boolean) => {
    setOpen(next)
    if (next) {
      execute.reset()
      dryRun.mutate()
    }
  }

  const confirm = () => {
    if (!plan) return
    execute.mutate(undefined, {
      onSuccess: () => {
        toast(`Re-scoring ${plan.jobCount} jobs in the background`)
        setOpen(false)
      },
      onError: (error) => toast.error(`Re-score not started: ${errorText(error)}`),
    })
  }

  return (
    <>
      <Button variant="secondary" disabled={disabled} onClick={() => onOpenChange(true)}>
        Re-score active jobs…
      </Button>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="rounded-dialog shadow-dialog sm:max-w-[460px]">
          <DialogHeader>
            <DialogTitle className="text-[17px] font-semibold">Re-score active jobs?</DialogTitle>
            <DialogDescription className="text-[13.5px] text-ink-2">
              {plan ? (
                <>
                  Scores <span className="num font-semibold text-ink">{plan.jobCount}</span> analyzed, already scored
                  jobs again with the current prompt and model. Jobs you marked Not eligible are left out.
                </>
              ) : (
                'Counting the jobs to re-score…'
              )}
            </DialogDescription>
          </DialogHeader>
          {dryRun.isError ? <ErrorState error={dryRun.error} title="Could not prepare the re-score" /> : null}
          {plan ? (
            <CostSummary
              model={plan.model}
              estimatedCalls={plan.estimatedCalls}
              estimatedCostUsd={plan.estimatedCostUsd}
              budgetUsd={budgetUsd}
            />
          ) : null}
          <DialogFooter>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              disabled={!plan || plan.jobCount === 0 || paidBlocked(plan.model) || execute.isPending}
              onClick={confirm}
            >
              {plan ? `Re-score ${plan.jobCount} ${plan.jobCount === 1 ? 'job' : 'jobs'}` : 'Re-score'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
