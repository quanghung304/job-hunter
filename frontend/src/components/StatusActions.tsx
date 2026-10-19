import { statusActionLabel, type TrackingStatus } from '@/lib/labels'
import { primaryTarget } from '@/lib/statusMachine'
import { Button } from './ui/button'

/** Keyboard shortcuts of the board (useBoardKeyboard). */
function shortcutFor(from: TrackingStatus, to: TrackingStatus): string | undefined {
  if (to === 'DISMISSED') return 'd'
  if (to === 'APPLIED') return 'a'
  if (to === 'SHORTLISTED' || (from === 'SHORTLISTED' && to === 'NEW')) return 's'
  return undefined
}

/**
 * Status buttons from the server's `allowedTransitions`: the next step of the flow as the one
 * primary button, then secondary buttons, then a quiet Dismiss.
 */
export function StatusActions({
  status,
  allowed,
  onChange,
  disabled = false,
  showShortcuts = true,
}: {
  status: TrackingStatus
  allowed: readonly TrackingStatus[]
  onChange: (to: TrackingStatus) => void
  disabled?: boolean
  showShortcuts?: boolean
}) {
  const primary = primaryTarget(status, allowed)
  const secondary = allowed.filter((to) => to !== primary && to !== 'DISMISSED')
  const ordered = [
    ...(primary ? [primary] : []),
    ...secondary,
    ...(allowed.includes('DISMISSED') ? ['DISMISSED' as const] : []),
  ]

  if (ordered.length === 0) {
    return <span className="text-[13px] text-muted">Final status — notes stay editable.</span>
  }
  return (
    <>
      {ordered.map((to) => (
        <Button
          key={to}
          variant={to === primary ? 'primary' : to === 'DISMISSED' ? 'quiet' : 'secondary'}
          shortcut={showShortcuts ? shortcutFor(status, to) : undefined}
          disabled={disabled}
          onClick={() => onChange(to)}
        >
          {statusActionLabel(status, to)}
        </Button>
      ))}
    </>
  )
}
