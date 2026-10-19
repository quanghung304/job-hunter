import type { AnalysisStage, Eligibility } from '@/lib/labels'

export type Action = 'analyze' | 'score'

/** Which owner action applies (ADR-0010): Analyze for NOT_ANALYZED, Score for analyzed eligible jobs. */
export function analysisAction(job: { analysisStage: AnalysisStage; eligibility: Eligibility | null }): Action | null {
  if (job.analysisStage === 'NOT_ANALYZED') return 'analyze'
  if (job.analysisStage === 'ANALYZED' && (job.eligibility === 'ELIGIBLE' || job.eligibility === 'UNCERTAIN')) {
    return 'score'
  }
  return null
}

/** The first posting link: "Open posting ↗" and the `o` key. */
export function postingUrl(job: { postings: readonly { url: string }[] }): string | undefined {
  return job.postings[0]?.url
}
