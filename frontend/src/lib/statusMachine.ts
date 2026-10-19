import type { TrackingStatus } from './labels'

/** Allowed tracking status transitions; mirrors data-model.md §2 (backend TrackingStatusMachine). */
const ALLOWED: Record<TrackingStatus, readonly TrackingStatus[]> = {
  NEW: ['SHORTLISTED', 'DISMISSED', 'APPLIED'],
  SHORTLISTED: ['DISMISSED', 'APPLIED', 'NEW'],
  DISMISSED: ['NEW'],
  APPLIED: ['INTERVIEWING', 'REJECTED', 'WITHDRAWN'],
  INTERVIEWING: ['OFFER', 'REJECTED', 'WITHDRAWN'],
  OFFER: [],
  REJECTED: [],
  WITHDRAWN: [],
}

export const ALL_STATUSES: readonly TrackingStatus[] = [
  'NEW',
  'SHORTLISTED',
  'DISMISSED',
  'APPLIED',
  'INTERVIEWING',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
]

/** Statuses reachable in one step; staying in the same status is not a transition. */
export function allowedTargets(from: TrackingStatus): readonly TrackingStatus[] {
  return ALLOWED[from]
}

export function canTransition(from: TrackingStatus, to: TrackingStatus): boolean {
  return ALLOWED[from].includes(to)
}

export function isTerminal(status: TrackingStatus): boolean {
  return ALLOWED[status].length === 0
}

/**
 * The next step of the status flow: the one primary button of a view (frontend.md "Buttons").
 * Null when nothing moves the job forward (terminal statuses).
 */
export function primaryTarget(from: TrackingStatus, allowed: readonly TrackingStatus[]): TrackingStatus | null {
  const order: Partial<Record<TrackingStatus, TrackingStatus>> = {
    NEW: 'SHORTLISTED',
    SHORTLISTED: 'APPLIED',
    DISMISSED: 'NEW',
    APPLIED: 'INTERVIEWING',
    INTERVIEWING: 'OFFER',
  }
  const next = order[from]
  return next !== undefined && allowed.includes(next) ? next : null
}
