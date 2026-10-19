import type { AnalysisStage, Decision } from '@/lib/labels'

export type Action = 'analyze' | 'score'

/**
 * Which owner action applies (ADR-0010/0011): Analyze for NOT_ANALYZED, Score for analyzed jobs the
 * owner has not marked Not eligible.
 */
export function analysisAction(job: {
  analysisStage: AnalysisStage
  eligibilityDecision: Decision | null
}): Action | null {
  if (job.analysisStage === 'NOT_ANALYZED') return 'analyze'
  if (job.analysisStage === 'ANALYZED' && job.eligibilityDecision !== 'INELIGIBLE') return 'score'
  return null
}

/** The first posting link: "Open posting ↗" and the `o` key. */
export function postingUrl(job: { postings: readonly { url: string }[] }): string | undefined {
  return job.postings[0]?.url
}
