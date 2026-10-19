import { Gauge, Loader2, ScanSearch } from 'lucide-react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { cn } from 'cn'
import { useAnalysisAction, usePausedPurpose } from '@/api/queries'
import { Button } from '@/components/ui/button'
import type { AnalysisStage, Eligibility } from '@/lib/labels'
import { type Action, analysisAction } from './jobActions'
import { errorText } from './useStatusChanger'

const TEXT: Record<Action, { idle: string; busy: string; purpose: 'EXTRACT' | 'SCORE' }> = {
  analyze: { idle: 'Analyze', busy: 'Analyzing…', purpose: 'EXTRACT' },
  score: { idle: 'Score', busy: 'Scoring…', purpose: 'SCORE' },
}

/**
 * The Analyze / Score button of one job: one click sends one request, with a spinner while it
 * runs. Disabled with "Model paused" when the purpose is paused (switching comes with T-023).
 * Variants: `icon` (list row), `button` (detail action row), `bar` (mobile action bar).
 */
export function AnalysisActions({
  job,
  variant = 'button',
}: {
  job: { id: number; analysisStage: AnalysisStage; eligibility: Eligibility | null }
  variant?: 'icon' | 'button' | 'bar'
}) {
  const action = analysisAction(job)
  if (!action) return null
  return <AnalysisButton id={job.id} action={action} variant={variant} />
}

function AnalysisButton({ id, action, variant }: { id: number; action: Action; variant: 'icon' | 'button' | 'bar' }) {
  const mutation = useAnalysisAction(action)
  const paused = usePausedPurpose(TEXT[action].purpose)
  const busy = mutation.isPending
  const Icon = busy ? Loader2 : action === 'analyze' ? ScanSearch : Gauge
  const label = busy ? TEXT[action].busy : TEXT[action].idle

  const run = () =>
    mutation.mutate(id, {
      onSuccess: (detail) => {
        if (detail.analysisStage === 'FAILED') {
          toast.error(`${TEXT[action].idle} failed this time; you can try again.`)
        }
      },
      onError: (error) => toast.error(errorText(error)),
    })

  if (variant === 'icon') {
    return (
      <Button
        variant="quiet"
        size="icon"
        className="max-lg:size-11"
        aria-label={paused ? `${TEXT[action].idle}: model paused` : label}
        title={paused ? 'Model paused' : label}
        disabled={busy || paused !== undefined}
        onClick={run}
      >
        <Icon className={cn('size-4', busy && 'animate-spin')} strokeWidth={1.8} />
      </Button>
    )
  }
  return (
    <span className={cn('inline-flex items-center gap-2', variant === 'bar' && 'flex-1')}>
      <Button
        variant="secondary"
        size={variant === 'bar' ? 'lg' : 'default'}
        className={cn(variant === 'bar' && 'w-full')}
        disabled={busy || paused !== undefined}
        onClick={run}
      >
        <Icon className={cn(busy && 'animate-spin')} strokeWidth={1.8} aria-hidden />
        {paused ? 'Model paused' : label}
      </Button>
      {paused && variant === 'button' ? (
        <Link to="/admin#llm" className="text-[13px] text-accent-deep underline underline-offset-2">
          Switch model
        </Link>
      ) : null}
    </span>
  )
}
