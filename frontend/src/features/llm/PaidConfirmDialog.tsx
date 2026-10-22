import { TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { usd } from '@/lib/format'
import type { PaidConfirmation } from './paidConfirmation'

/** ADR-0009: names the PAID model and its cost per call; only "Confirm paid calls" repeats the action. */
export function PaidConfirmDialog({
  confirmation,
  onClose,
}: {
  confirmation: PaidConfirmation | null
  onClose: () => void
}) {
  return (
    <Dialog open={confirmation !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="rounded-dialog shadow-dialog sm:max-w-[460px]">
        {confirmation ? (
          <>
            <DialogHeader>
              <DialogTitle className="text-[17px] font-semibold">{confirmation.action}?</DialogTitle>
              <DialogDescription className="text-[13.5px] text-ink-2">
                {confirmation.modelLabel} is a paid model.
              </DialogDescription>
            </DialogHeader>
            <p className="m-0 flex gap-2 rounded-card bg-attention-bg px-3 py-2.5 text-[13.5px] text-attention-fg">
              <TriangleAlert className="mt-0.5 size-4 shrink-0 text-attention-icon" aria-hidden />
              <span>
                Each call costs{' '}
                <span className="num font-semibold">
                  {confirmation.costPerCallUsd === null
                    ? 'an unknown amount (no cost history yet)'
                    : `about ${usd(confirmation.costPerCallUsd)}`}
                </span>{' '}
                and counts against the monthly budget.
              </span>
            </p>
            <DialogFooter>
              <Button variant="secondary" onClick={onClose}>
                Cancel
              </Button>
              <Button
                variant="primary"
                onClick={() => {
                  confirmation.run()
                  onClose()
                }}
              >
                Confirm paid calls
              </Button>
            </DialogFooter>
          </>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
