/** Enum → UI label (English UI text, frontend.md "Rules"). */

/** Tracking statuses (data-model.md §2). */
export type TrackingStatus =
  'NEW' | 'SHORTLISTED' | 'DISMISSED' | 'APPLIED' | 'INTERVIEWING' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'

export const STATUS_LABELS: Record<TrackingStatus, string> = {
  NEW: 'New',
  SHORTLISTED: 'Shortlisted',
  DISMISSED: 'Dismissed',
  APPLIED: 'Applied',
  INTERVIEWING: 'Interviewing',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

export type Eligibility = 'ELIGIBLE' | 'UNCERTAIN' | 'INELIGIBLE'

export const ELIGIBILITY_LABELS: Record<Eligibility, string> = {
  ELIGIBLE: 'Eligible',
  UNCERTAIN: 'Needs check',
  INELIGIBLE: 'Ineligible',
}

export type RuleResult = 'OK' | 'UNKNOWN' | 'FAIL'

export const RULE_RESULT_LABELS: Record<RuleResult, string> = { OK: 'OK', UNKNOWN: 'Unclear', FAIL: 'Fail' }
