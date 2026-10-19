import { Ban, CircleCheck } from 'lucide-react'
import { DECISION_LABELS, type Decision } from '@/lib/labels'
import { Tag } from './Tag'

const STYLES = {
  ELIGIBLE: { className: 'bg-eligible-bg text-eligible-fg', icon: CircleCheck },
  INELIGIBLE: { className: 'bg-ineligible-bg text-ineligible-fg', icon: Ban },
} as const

/** The owner's eligibility decision (ADR-0011): "Eligible" (green) / "Not eligible" (orange-red). */
export function DecisionTag({ decision }: { decision: Decision }) {
  const { className, icon } = STYLES[decision]
  return (
    <Tag className={`font-semibold ${className}`} icon={icon}>
      {DECISION_LABELS[decision]}
    </Tag>
  )
}
