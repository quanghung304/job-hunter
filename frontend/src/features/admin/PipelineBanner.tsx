import { Loader2 } from 'lucide-react'
import type { AdminOverview } from '@/api/queries'

const TITLES: Record<NonNullable<AdminOverview['pipelineProgress']>['stage'], string> = {
  COLLECT: 'Pipeline running',
  PREFILTER: 'Pipeline running',
  EXPIRE: 'Pipeline running',
  ANALYZE: 'Analyzing selected jobs',
  SCORE: 'Scoring jobs',
}

/** Shown while the pipeline or a bulk LLM action runs: title, mono progress text, slim bar. */
export function PipelineBanner({
  progress,
  running,
}: {
  progress: AdminOverview['pipelineProgress']
  running: boolean
}) {
  if (!progress && !running) return null
  const percent = Math.max(0, Math.min(100, progress?.percent ?? 0))
  return (
    <section
      role="status"
      aria-label="Progress"
      className="rounded-card border border-accent-tint bg-accent-tint-2 px-5 py-3.5"
    >
      <p className="m-0 flex items-center gap-2 font-semibold text-accent-deep">
        <Loader2 className="size-4 animate-spin" aria-hidden />
        {progress ? TITLES[progress.stage] : 'Pipeline running'}
      </p>
      <p className="num mt-1 mb-2 text-[12.5px] text-ink-2">{progress?.text ?? 'Starting…'}</p>
      <div
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-label="Progress"
        className="h-1.5 overflow-hidden rounded-badge bg-accent-tint"
      >
        <span className="block h-full bg-accent" style={{ width: `${percent}%` }} />
      </div>
    </section>
  )
}
