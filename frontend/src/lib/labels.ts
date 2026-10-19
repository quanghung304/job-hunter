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

/** Button text for moving a job to a status, depending on where it comes from. */
export function statusActionLabel(from: TrackingStatus, to: TrackingStatus): string {
  if (to === 'NEW') return from === 'DISMISSED' ? 'Restore' : 'Unshortlist'
  const labels: Record<TrackingStatus, string> = {
    NEW: 'Restore',
    SHORTLISTED: 'Shortlist',
    DISMISSED: 'Dismiss',
    APPLIED: 'Mark applied',
    INTERVIEWING: 'Interviewing',
    OFFER: 'Offer',
    REJECTED: 'Rejected',
    WITHDRAWN: 'Withdraw',
  }
  return labels[to]
}

/** Past-tense toast text after a status change. */
export function statusChangedText(from: TrackingStatus, to: TrackingStatus): string {
  if (to === 'NEW') return from === 'DISMISSED' ? 'Restored' : 'Removed from shortlist'
  return `Marked ${STATUS_LABELS[to].toLowerCase()}`
}

export type Eligibility = 'ELIGIBLE' | 'UNCERTAIN' | 'INELIGIBLE'

export const ELIGIBILITY_LABELS: Record<Eligibility, string> = {
  ELIGIBLE: 'Eligible',
  UNCERTAIN: 'Needs check',
  INELIGIBLE: 'Ineligible',
}

export type RuleResult = 'OK' | 'UNKNOWN' | 'FAIL'

export const RULE_RESULT_LABELS: Record<RuleResult, string> = { OK: 'OK', UNKNOWN: 'Unclear', FAIL: 'Fail' }

export type AnalysisStage = 'NOT_ANALYZED' | 'ANALYZED' | 'SCORED' | 'FAILED'

export const ANALYSIS_STAGE_LABELS: Record<AnalysisStage, string> = {
  NOT_ANALYZED: 'Not analyzed',
  ANALYZED: 'Analyzed',
  SCORED: 'Scored',
  FAILED: 'Failed',
}

export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'FREELANCE' | 'INTERNSHIP'

export const EMPLOYMENT_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: 'Full-time',
  PART_TIME: 'Part-time',
  CONTRACT: 'Contract',
  FREELANCE: 'Freelance',
  INTERNSHIP: 'Internship',
}

export type RemoteType = 'FULLY_REMOTE' | 'REMOTE_RESTRICTED' | 'HYBRID' | 'ONSITE' | 'UNKNOWN'

export const REMOTE_LABELS: Record<RemoteType, string> = {
  FULLY_REMOTE: 'Remote anywhere',
  REMOTE_RESTRICTED: 'Remote (restricted)',
  HYBRID: 'Hybrid',
  ONSITE: 'On-site',
  UNKNOWN: 'Unclear',
}

export const SENIORITY_LABELS: Record<string, string> = {
  JUNIOR: 'Junior',
  MID: 'Mid',
  SENIOR: 'Senior',
  LEAD: 'Lead',
  PRINCIPAL: 'Principal',
}

export const CONFIDENCE_LABELS: Record<string, string> = { HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' }

export const SALARY_PERIOD_LABELS: Record<string, string> = { HOUR: 'hour', MONTH: 'month', YEAR: 'year' }

/** Eligibility rules (scoring-spec.md §4), in rule order. */
export type EligibilityRule =
  'WORK_MODE' | 'REGION' | 'WORK_AUTH' | 'TIMEZONE' | 'EMPLOYMENT_TYPE' | 'HOURS' | 'SENIORITY' | 'LANGUAGE' | 'SCAM'

export const ELIGIBILITY_RULES: readonly EligibilityRule[] = [
  'WORK_MODE',
  'REGION',
  'WORK_AUTH',
  'TIMEZONE',
  'EMPLOYMENT_TYPE',
  'HOURS',
  'SENIORITY',
  'LANGUAGE',
  'SCAM',
]

export const ELIGIBILITY_RULE_LABELS: Record<EligibilityRule, string> = {
  WORK_MODE: 'Work mode',
  REGION: 'Region',
  WORK_AUTH: 'Work permit',
  TIMEZONE: 'Time zone',
  EMPLOYMENT_TYPE: 'Employment',
  HOURS: 'Hours',
  SENIORITY: 'Seniority',
  LANGUAGE: 'Language',
  SCAM: 'Scam check',
}

/** Eligibility reason codes that are not `RULE:VERDICT` pairs. */
const ELIGIBILITY_NOTES: Record<string, string> = {
  FULL_TIME_ONLY_SOFT: 'Full-time only: you accept it as a fallback',
  'CONFIDENCE:LOW': 'The extraction is not confident; check the posting',
}

/**
 * Rule results from the stored reasons (`RULE:UNKNOWN`, `RULE:FAIL`; rules not listed are OK)
 * plus the notes that are not rule results.
 */
export function eligibilityResults(reasons: readonly string[]): {
  rules: Record<EligibilityRule, RuleResult>
  notes: string[]
} {
  const rules = Object.fromEntries(ELIGIBILITY_RULES.map((rule) => [rule, 'OK'])) as Record<EligibilityRule, RuleResult>
  const notes: string[] = []
  for (const reason of reasons) {
    const [rule, verdict] = reason.split(':')
    if (ELIGIBILITY_RULES.includes(rule as EligibilityRule) && (verdict === 'UNKNOWN' || verdict === 'FAIL')) {
      rules[rule as EligibilityRule] = verdict
    } else {
      notes.push(ELIGIBILITY_NOTES[reason] ?? reason)
    }
  }
  return { rules, notes }
}

/** Prefilter reject reasons (scoring-spec.md §2). */
export const PREFILTER_REASON_LABELS: Record<string, string> = {
  TOO_OLD: 'Posted too long ago',
  EXCLUDED_COMPANY: 'Company is on your exclude list',
  TITLE_EXCLUDED: 'Title matches an excluded word',
  NOT_RELEVANT: 'No skill or title keyword of your profile',
  ONSITE_SOURCE_HINT: 'The source marks it on-site',
}

export function prefilterReasonLabel(code: string): string {
  return PREFILTER_REASON_LABELS[code] ?? code
}

/** Profile summary language → the label before the apply advice (frontend.md "JobDetail"). */
export function applyAdviceLabel(language: string | undefined): string {
  return language === 'vi' ? 'Khi apply:' : 'If applying:'
}
