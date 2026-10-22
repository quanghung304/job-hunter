import { Tag } from '@/components/Tag'
import type { ModelTier } from './llmLabels'

const STYLES: Record<ModelTier, string> = {
  FREE: 'bg-accent-tint text-accent-deep',
  LOCAL: 'bg-line-faint text-strong',
  PAID: 'bg-attention-bg text-attention-fg',
}

/** FREE / LOCAL / PAID (frontend.md `SwitchModelDialog`). */
export function TierTag({ tier }: { tier: ModelTier }) {
  return <Tag className={`font-semibold ${STYLES[tier]}`}>{tier}</Tag>
}
