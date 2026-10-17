/** Score bands (api-spec.md: EXCELLENT ≥ 85, GOOD 70–84, MAYBE 50–69, WEAK < 50, NONE unscored). */
export type ScoreBand = 'EXCELLENT' | 'GOOD' | 'MAYBE' | 'WEAK' | 'NONE'

export function scoreBand(score: number | null | undefined): ScoreBand {
  if (score === null || score === undefined || Number.isNaN(score)) {
    return 'NONE'
  }
  if (score >= 85) return 'EXCELLENT'
  if (score >= 70) return 'GOOD'
  if (score >= 50) return 'MAYBE'
  return 'WEAK'
}

/** Badge colors per band (frontend.md "ScoreBadge"); token classes only. */
export const scoreBandClasses: Record<ScoreBand, string> = {
  EXCELLENT: 'bg-accent text-white',
  GOOD: 'bg-accent-tint text-accent-deep',
  MAYBE: 'bg-maybe-bg text-ink-2',
  WEAK: 'bg-transparent text-muted shadow-[inset_0_0_0_1px_var(--control-border)]',
  NONE: 'bg-transparent text-muted shadow-[inset_0_0_0_1px_var(--line)]',
}

export const scoreBandLabels: Record<ScoreBand, string> = {
  EXCELLENT: 'Excellent',
  GOOD: 'Good',
  MAYBE: 'Maybe',
  WEAK: 'Weak',
  NONE: 'Not scored',
}
