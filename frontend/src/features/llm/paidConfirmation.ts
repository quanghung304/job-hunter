import { ApiError } from '@/api/client'

/** A request the server answered with PAID_CONFIRMATION_REQUIRED, to repeat with `confirmPaid: true`. */
export interface PaidConfirmation {
  /** What will happen, e.g. "Test Claude Haiku 5.5" or "Allow paid fallback for Scoring". */
  action: string
  modelLabel: string
  costPerCallUsd: number | null
  run: () => void
}

/** The confirmation data of a PAID_CONFIRMATION_REQUIRED error, or null for any other error. */
export function paidConfirmationOf(error: unknown, action: string, run: () => void): PaidConfirmation | null {
  if (!(error instanceof ApiError) || error.code !== 'PAID_CONFIRMATION_REQUIRED') return null
  const cost = error.problem.estimatedCostPerCallUsd
  return {
    action,
    modelLabel: typeof error.problem.modelLabel === 'string' ? error.problem.modelLabel : 'This model',
    costPerCallUsd: typeof cost === 'number' ? cost : null,
    run,
  }
}
