import { ELIGIBILITY_LABELS, type Eligibility } from '@/lib/labels'
import { Tag } from './Tag'

const STYLES: Record<Eligibility, string> = {
  ELIGIBLE: 'bg-eligible-bg text-eligible-fg',
  UNCERTAIN: 'bg-attention-bg text-attention-fg',
  INELIGIBLE: 'bg-strong text-white',
}

/** Rows render only "Needs check" (`rowMode`). */
export function EligibilityTag({ eligibility, rowMode = false }: { eligibility: Eligibility; rowMode?: boolean }) {
  if (rowMode && eligibility !== 'UNCERTAIN') {
    return null
  }
  return <Tag className={STYLES[eligibility]}>{ELIGIBILITY_LABELS[eligibility]}</Tag>
}
