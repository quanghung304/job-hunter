import { X } from 'lucide-react'
import type { JobDetail } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { statusActionLabel, type TrackingStatus } from '@/lib/labels'
import { primaryTarget } from '@/lib/statusMachine'
import { AnalysisActions } from './AnalysisActions'
import { analysisAction } from './jobActions'

/**
 * Fixed bottom bar of the mobile detail: Dismiss (48×48 icon), the analysis action when it
 * applies, and the next step of the status flow (the one primary button).
 */
export function MobileActionBar({
  job,
  onStatusChange,
}: {
  job: JobDetail
  onStatusChange: (to: TrackingStatus) => void
}) {
  const allowed = job.allowedTransitions
  const primary = primaryTarget(job.status, allowed)
  const fallback = primary ?? allowed.find((to) => to !== 'DISMISSED') ?? null
  return (
    <div className="fixed inset-x-0 bottom-0 z-40 flex items-center gap-2 border-t border-line bg-surface px-4 pt-3 pb-[max(12px,env(safe-area-inset-bottom))] shadow-action-bar">
      {allowed.includes('DISMISSED') ? (
        <Button
          variant="secondary"
          size="icon-lg"
          aria-label="Dismiss"
          title="Dismiss"
          onClick={() => onStatusChange('DISMISSED')}
        >
          <X className="size-5" />
        </Button>
      ) : null}
      {analysisAction(job) ? <AnalysisActions job={job} variant="bar" /> : null}
      {fallback ? (
        <Button
          variant={fallback === primary ? 'primary' : 'secondary'}
          size="lg"
          className="flex-1"
          onClick={() => onStatusChange(fallback)}
        >
          {statusActionLabel(job.status, fallback)}
        </Button>
      ) : (
        <span className="flex-1 text-center text-[13px] text-muted">Final status</span>
      )}
    </div>
  )
}
