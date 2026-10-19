import type { ModelChoice } from '@/api/queries'

/** A PAID model while paid calls are off: the server refuses the run (409 PAID_CALLS_DISABLED, ADR-0009). */
export function paidBlocked(model: ModelChoice): boolean {
  return model.tier === 'PAID' && !model.paidCallsAllowed
}
