import { useIsMutating } from '@tanstack/react-query'
import { useState } from 'react'
import { Gauge, Loader2, ScanSearch } from 'lucide-react'
import { toast } from 'sonner'
import { cn } from 'cn'
import { useAnalysisAction, usePausedPurpose } from '@/api/queries'
import { Button } from '@/components/ui/button'
import type { AnalysisStage, Decision } from '@/lib/labels'
import { SwitchModelDialog } from '@/features/llm/SwitchModelDialog'
import { type Action, analysisAction } from './jobActions'
import { errorText } from './useStatusChanger'

const TEXT: Record<Action, { idle: string; busy: string; purpose: 'EXTRACT' | 'SCORE' }> = {
  analyze: { idle: 'Analyze', busy: 'Analyzing…', purpose: 'EXTRACT' },
  score: { idle: 'Score', busy: 'Scoring…', purpose: 'SCORE' },
}

/**
 * The Analyze / Score button of one job (detail action row and mobile action bar only, never on
 * cards): one click sends one request, with a spinner while it runs. Disabled with "Model paused"
 * when the purpose is paused, with "Switch model" (the model dialog of that step). Variants: `button` (detail action row,
 * "Not analyzed yet" card), `bar` (mobile action bar).
 */
export function AnalysisActions({
  job,
  variant = 'button',
}: {
  job: { id: number; analysisStage: AnalysisStage; eligibilityDecision: Decision | null }
  variant?: 'button' | 'bar'
}) {
  const action = analysisAction(job)
  if (!action) return null
  return <AnalysisButton id={job.id} action={action} variant={variant} />
}

function AnalysisButton({ id, action, variant }: { id: number; action: Action; variant: 'button' | 'bar' }) {
  const mutation = useAnalysisAction(action, id)
  const [switching, setSwitching] = useState(false)
  const paused = usePausedPurpose(TEXT[action].purpose)
  // Another button of the same job (e.g. the "Not analyzed yet" card) may be running it.
  const busy = useIsMutating({ mutationKey: [action, id] }) > 0
  const Icon = busy ? Loader2 : action === 'analyze' ? ScanSearch : Gauge
  const label = busy ? TEXT[action].busy : TEXT[action].idle

  const run = () =>
    mutation.mutate(undefined, {
      onSuccess: (detail) => {
        if (detail.analysisStage === 'FAILED') {
          toast.error(`${TEXT[action].idle} failed this time; you can try again.`)
        }
      },
      onError: (error) => toast.error(errorText(error)),
    })

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
        <>
          <Button variant="link" className="text-[13px]" onClick={() => setSwitching(true)}>
            Switch model
          </Button>
          <SwitchModelDialog purpose={TEXT[action].purpose} open={switching} onOpenChange={setSwitching} />
        </>
      ) : null}
    </span>
  )
}
