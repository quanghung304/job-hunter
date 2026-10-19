import { AnalysisTag } from '@/components/AnalysisTag'
import { ChecksTag } from '@/components/ChecksTag'
import { DecisionTag } from '@/components/DecisionTag'
import { StatusTag } from '@/components/StatusTag'
import type { TrackingStatus } from '@/lib/labels'

const STATUSES: TrackingStatus[] = ['SHORTLISTED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN']

/** One row with every status, analysis and decision tag the cards can show. */
export function StatusLegend() {
  return (
    <div className="flex flex-wrap items-center gap-1.5" aria-label="Legend" role="note">
      <span className="mr-1 text-[12.5px] text-muted">Legend</span>
      {STATUSES.map((status) => (
        <StatusTag key={status} status={status} />
      ))}
      <AnalysisTag stage="ANALYZED" />
      <DecisionTag decision="ELIGIBLE" />
      <DecisionTag decision="INELIGIBLE" />
      <ChecksTag checks={{ unknown: 2, fail: 0 }} />
    </div>
  )
}
