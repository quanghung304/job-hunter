import { cn } from 'cn'
import { scoreBand, scoreBandClasses } from '@/lib/scoreBand'

interface ScoreBadgeProps {
  score: number | null | undefined
  /** card 44×34 on grid/list cards; sm 40×32 in the list panel; lg 68×68 (60×60 on mobile) with the band word. */
  size?: 'card' | 'sm' | 'lg'
  className?: string
}

/** Mono score in a box; the number is always shown so color is never the only signal. */
export function ScoreBadge({ score, size = 'sm', className }: ScoreBadgeProps) {
  const band = scoreBand(score)
  const text = band === 'NONE' ? '—' : String(score)
  const label = band === 'NONE' ? 'Not scored' : `Score ${text} of 100, ${band.toLowerCase()}`

  if (size === 'lg') {
    return (
      <div
        role="img"
        aria-label={label}
        className={cn(
          'num flex size-[60px] shrink-0 flex-col items-center justify-center rounded-badge lg:size-[68px]',
          scoreBandClasses[band],
          className,
        )}
      >
        <span className="text-[22px] leading-none font-semibold lg:text-[26px]">{text}</span>
        {band !== 'NONE' ? (
          <span className="mt-1 font-sans text-[10.5px] font-semibold tracking-[0.08em] uppercase" aria-hidden>
            {band}
          </span>
        ) : null}
      </div>
    )
  }

  return (
    <span
      role="img"
      aria-label={label}
      className={cn(
        'num inline-flex shrink-0 items-center justify-center rounded-badge text-[15px] font-semibold',
        size === 'card' ? 'h-[34px] w-11' : 'h-8 w-10',
        scoreBandClasses[band],
        className,
      )}
    >
      {text}
    </span>
  )
}
