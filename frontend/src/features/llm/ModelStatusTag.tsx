import { CircleAlert, CircleCheck, Clock, Gauge, TriangleAlert, type LucideIcon } from 'lucide-react'
import { Tag } from '@/components/Tag'
import { type ModelStatus, statusText } from './llmLabels'

const STYLES: Record<ModelStatus, { className: string; icon: LucideIcon }> = {
  AVAILABLE: { className: 'bg-accent-tint text-accent-deep', icon: CircleCheck },
  NEAR_LIMIT: { className: 'bg-attention-bg text-attention-fg', icon: Gauge },
  RATE_LIMITED: { className: 'bg-attention-bg text-attention-fg', icon: Clock },
  EXHAUSTED: { className: 'bg-strong text-white', icon: TriangleAlert },
  ERROR: { className: 'bg-strong text-white', icon: CircleAlert },
}

/** Model status: Available, Near limit, Rate limited · 1 min, Exhausted until 02:00, Error (icon + word). */
export function ModelStatusTag({ status, until }: { status: ModelStatus; until?: string | null }) {
  const { className, icon } = STYLES[status]
  return (
    <Tag className={`font-semibold ${className}`} icon={icon}>
      {statusText(status, until)}
    </Tag>
  )
}
