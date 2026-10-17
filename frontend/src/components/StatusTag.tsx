import { STATUS_LABELS, type TrackingStatus } from '@/lib/labels'
import { Tag } from './Tag'

const STYLES: Record<TrackingStatus, string> = {
  NEW: 'bg-line-faint text-strong',
  SHORTLISTED: 'bg-accent-tint text-accent-deep',
  APPLIED: 'bg-accent-tint text-accent-deep',
  INTERVIEWING: 'bg-accent text-white',
  OFFER: 'bg-accent text-white',
  REJECTED: 'bg-neutral-fill text-muted',
  WITHDRAWN: 'bg-neutral-fill text-muted',
  DISMISSED: 'bg-neutral-fill text-muted',
}

/** Rows render it only when status ≠ NEW (`hideNew`). */
export function StatusTag({ status, hideNew = false }: { status: TrackingStatus; hideNew?: boolean }) {
  if (hideNew && status === 'NEW') {
    return null
  }
  return <Tag className={STYLES[status]}>{STATUS_LABELS[status]}</Tag>
}
