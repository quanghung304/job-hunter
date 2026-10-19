import { useState } from 'react'
import { type JobsQuery, statusVisibleIn } from '@/api/queries'
import { toggledDecision, useDecisionChanger } from '@/features/job/useDecisionChanger'
import { useStatusChanger } from '@/features/job/useStatusChanger'
import type { Decision, TrackingStatus } from '@/lib/labels'
import { canTransition } from '@/lib/statusMachine'

/** Card fade before the job leaves the list (frontend.md "Motion"). */
export const LEAVE_MS = 150

function reducedMotion(): boolean {
  return typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

interface CardJob {
  id: number
  status: TrackingStatus
  eligibilityDecision: Decision | null
}

/**
 * Status and decision actions of the cards of one board query. A job that leaves the query (e.g.
 * dismissed) fades out first; `onLeave` runs at once so the caller can select a neighbour.
 */
export function useCardActions(query: JobsQuery) {
  const { change } = useStatusChanger()
  const { change: decide } = useDecisionChanger()
  const [leavingId, setLeavingId] = useState<number | null>(null)

  const changeStatus = (job: CardJob, to: TrackingStatus, onLeave?: () => void) => {
    if (!canTransition(job.status, to)) return
    if (statusVisibleIn(query, to)) {
      change(job.id, job.status, to)
      return
    }
    onLeave?.()
    if (reducedMotion()) {
      change(job.id, job.status, to)
      return
    }
    setLeavingId(job.id)
    setTimeout(() => {
      setLeavingId(null)
      change(job.id, job.status, to)
    }, LEAVE_MS)
  }

  return {
    leavingId,
    changeStatus,
    shortlistToggle: (job: CardJob, onLeave?: () => void) =>
      changeStatus(job, job.status === 'SHORTLISTED' ? 'NEW' : 'SHORTLISTED', onLeave),
    decide: (job: CardJob, to: Decision | null) => decide(job.id, job.eligibilityDecision, to),
    /** `e` / `n`: pressing the key of the current decision clears it. */
    toggleDecision: (job: CardJob, key: Decision) =>
      decide(job.id, job.eligibilityDecision, toggledDecision(job.eligibilityDecision, key)),
  }
}
