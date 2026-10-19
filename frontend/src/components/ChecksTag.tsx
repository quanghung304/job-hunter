import { Tag } from './Tag'

/** "Check N criteria" while the owner is undecided and some checks are UNKNOWN or FAIL (ADR-0011). */
export function ChecksTag({ checks }: { checks: { unknown: number; fail: number } | null }) {
  const count = checks ? checks.unknown + checks.fail : 0
  if (count === 0) return null
  return (
    <Tag className="bg-attention-bg text-attention-fg">
      {count === 1 ? 'Check 1 criterion' : `Check ${count} criteria`}
    </Tag>
  )
}
