import { CircleDot, EyeOff, MessageSquare, Send, Star, Trophy, Undo2, X, type LucideIcon } from 'lucide-react'
import { STATUS_LABELS, type TrackingStatus } from '@/lib/labels'
import { Tag } from './Tag'

const STYLES: Record<TrackingStatus, { className: string; icon: LucideIcon }> = {
  NEW: { className: 'bg-line-faint text-strong', icon: CircleDot },
  SHORTLISTED: { className: 'bg-st-shortlisted-bg text-st-shortlisted-fg', icon: Star },
  APPLIED: { className: 'bg-st-applied-bg text-st-applied-fg', icon: Send },
  INTERVIEWING: { className: 'bg-st-interviewing-bg text-st-interviewing-fg', icon: MessageSquare },
  OFFER: { className: 'bg-st-offer-bg text-white', icon: Trophy },
  REJECTED: { className: 'bg-strong text-white', icon: X },
  WITHDRAWN: { className: 'bg-st-withdrawn-bg text-st-withdrawn-fg', icon: Undo2 },
  DISMISSED: { className: 'bg-st-withdrawn-bg text-st-withdrawn-fg', icon: EyeOff },
}

/** Tracking status: own hue per status, icon + word. Cards render it only when status ≠ NEW (`hideNew`). */
export function StatusTag({ status, hideNew = false }: { status: TrackingStatus; hideNew?: boolean }) {
  if (hideNew && status === 'NEW') {
    return null
  }
  const { className, icon } = STYLES[status]
  return (
    <Tag className={`rounded-badge font-semibold ${className}`} icon={icon}>
      {STATUS_LABELS[status]}
    </Tag>
  )
}
