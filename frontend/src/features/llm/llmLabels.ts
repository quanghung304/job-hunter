import type { LlmAlert, LlmModel, LlmPurpose } from '@/api/queries'
import { clockTime, shortDate } from '@/lib/format'

export type ModelStatus = LlmModel['status']
export type ModelTier = LlmModel['tier']

/** "Extraction" / "Scoring" (the steps the owner knows from the Admin page). */
export function purposeLabel(purpose: LlmPurpose): string {
  return purpose === 'EXTRACT' ? 'Extraction' : 'Scoring'
}

/** The owner action a purpose serves: "Analyze" / "Score". */
export function purposeAction(purpose: LlmPurpose): string {
  return purpose === 'EXTRACT' ? 'Analyze' : 'Score'
}

/** Status word of a model (frontend.md "LLM alerts and switching"): always a word, never color alone. */
export function statusText(status: ModelStatus, until: string | null | undefined, now = Date.now()): string {
  switch (status) {
    case 'AVAILABLE':
      return 'Available'
    case 'NEAR_LIMIT':
      return 'Near limit'
    case 'RATE_LIMITED': {
      if (!until) return 'Rate limited'
      const minutes = Math.max(1, Math.ceil((Date.parse(until) - now) / 60_000))
      return `Rate limited · ${minutes} min`
    }
    case 'EXHAUSTED':
      return until ? `Exhausted until ${clockTime(until)}` : 'Exhausted'
    case 'ERROR':
      return 'Error'
  }
}

/** "812 / 1000 today" from the requests-per-day limit, or "812 calls today · no known limit". */
export function usageText(model: LlmModel): string {
  const calls = model.usageToday.calls
  return model.limits.rpd ? `${calls} / ${model.limits.rpd} today` : `${calls} calls today · no known limit`
}

/**
 * Why a model cannot be chosen in the switcher, or null: ERROR (incl. a missing key) and EXHAUSTED
 * models, and PAID models while paid calls are off (ADR-0009).
 */
export function unusableReason(model: LlmModel, paidCallsAllowed: boolean): string | null {
  if (!model.keyConfigured) return `No key: add the ${model.provider} API key to .env and restart`
  if (model.status === 'ERROR') return `Error: ${model.detail ?? 'check the key and model id'}`
  if (model.status === 'EXHAUSTED') {
    return model.until ? `Limit reached until ${clockTime(model.until)}` : 'Limit reached'
  }
  if (model.tier === 'PAID' && !paidCallsAllowed) {
    return 'Paid calls are off: set LLM_ALLOW_PAID_CALLS=true in .env and restart'
  }
  return null
}

const percent = (value: number | null) => (value === null ? '—' : `${(value * 100).toFixed(1).replace(/\.0$/, '')}%`)

/** "Eval 25 Oct: false-eligible 2.4%, HIGH recall 83%" or "Not evaluated yet". */
export function evalText(model: LlmModel): string {
  const e = model.lastEval
  if (!e) return 'Not evaluated yet'
  return `Eval ${shortDate(e.date)}: false-eligible ${percent(e.falseEligible)}, HIGH recall ${percent(e.highRecall)}`
}

/** An alert's identity: dismissing hides it until the model, status or reset time changes. */
export function alertIdentity(alert: LlmAlert): string {
  return [alert.purpose, alert.modelId, alert.status, alert.until ?? '', alert.switchedFromModelId ?? ''].join('|')
}
