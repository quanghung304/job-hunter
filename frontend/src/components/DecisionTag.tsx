import { DECISION_LABELS, type Decision } from '@/lib/labels'
import { Tag } from './Tag'

const STYLES: Record<Decision, string> = {
  ELIGIBLE: 'bg-eligible-bg text-eligible-fg',
  INELIGIBLE: 'bg-strong text-white',
}

/** The owner's eligibility decision (ADR-0011): "Eligible" / "Not eligible". Colors change in T-026. */
export function DecisionTag({ decision }: { decision: Decision }) {
  return <Tag className={STYLES[decision]}>{DECISION_LABELS[decision]}</Tag>
}
