import { RULE_RESULT_LABELS, type RuleResult } from '@/lib/labels'
import { Tag } from './Tag'

const STYLES: Record<RuleResult, string> = {
  OK: 'bg-accent-tint text-accent-deep',
  UNKNOWN: 'bg-attention-bg text-attention-fg',
  FAIL: 'bg-strong text-white',
}

/** Eligibility rule outcome (scoring-spec.md §4); 12px / 600. */
export function RuleResultTag({ result }: { result: RuleResult }) {
  return <Tag className={`${STYLES[result]} font-semibold`}>{RULE_RESULT_LABELS[result]}</Tag>
}
