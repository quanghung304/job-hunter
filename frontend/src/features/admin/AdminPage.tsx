import { useQueryClient } from '@tanstack/react-query'
import { Download, Loader2, Play } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { type AdminOverview, queryKeys, useAdminOverview, useEvalExport, useRunPipeline } from '@/api/queries'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { Button } from '@/components/ui/button'
import { errorText } from '@/features/job/useStatusChanger'
import { clockTimeIn } from '@/lib/format'
import { BulkActionTable } from './BulkActionTable'
import { CompaniesTable } from './CompaniesTable'
import { FailuresList } from './FailuresList'
import { KpiRow } from './KpiRow'
import { LlmModelsCard } from './LlmModelsCard'
import { LlmUsageCard } from './LlmUsageCard'
import { PipelineBanner } from './PipelineBanner'
import { RescoreDialog } from './RescoreDialog'
import { RunsList } from './RunsList'
import { SourcesTable } from './SourcesTable'

/** "Next scheduled run 19:00 Europe/Rome · digest 08:30". */
function scheduleText(schedule: AdminOverview['schedule']): string {
  const run = schedule.nextRunAt
    ? `Next scheduled run ${clockTimeIn(schedule.nextRunAt, schedule.zone)} ${schedule.zone}`
    : 'No scheduled run'
  const digest = schedule.nextDigestAt ? ` · digest ${clockTimeIn(schedule.nextDigestAt, schedule.zone)}` : ''
  return run + digest
}

/** `/admin`: is the system healthy, and maintenance actions (frontend.md "/admin", PRD F2.6). */
export function AdminPage() {
  const overview = useAdminOverview()
  const run = useRunPipeline()
  const exportEval = useEvalExport()
  const data = overview.data
  const running = data?.pipelineRunning ?? false
  const busy = running || (data?.pipelineProgress ?? null) !== null
  const client = useQueryClient()
  const wasBusy = useRef(false)

  // A run or bulk action just finished: new jobs, scores and the header line.
  useEffect(() => {
    if (wasBusy.current && !busy) {
      void client.invalidateQueries({ queryKey: queryKeys.jobsAll })
      void client.invalidateQueries({ queryKey: queryKeys.status })
      void client.invalidateQueries({ queryKey: queryKeys.companies })
    }
    wasBusy.current = busy
  }, [busy, client])

  const startRun = () =>
    run.mutate(undefined, {
      onSuccess: () => toast('Pipeline started'),
      onError: (error) =>
        toast.error(
          error instanceof ApiError && error.code === 'PIPELINE_ALREADY_RUNNING'
            ? 'Pipeline is already running'
            : `Pipeline not started: ${errorText(error)}`,
        ),
    })

  const startExport = () =>
    exportEval.mutate(undefined, {
      onSuccess: ({ path, count }) => toast(`Exported ${count} jobs for labeling to ${path}`),
      onError: (error) =>
        toast.error(
          error instanceof ApiError && error.status === 501
            ? 'Export for labeling comes with the eval harness (T-016)'
            : `Export failed: ${errorText(error)}`,
        ),
    })

  return (
    <main className="mx-auto flex max-w-[1280px] flex-col gap-5 px-4 pt-7 pb-16 lg:px-6">
      <div className="flex flex-wrap items-start gap-x-4 gap-y-3">
        <div className="min-w-0 flex-1">
          <h1 className="m-0 text-[22px] font-semibold tracking-[-0.01em] text-ink">System</h1>
          <p className="num mt-1 mb-0 text-[13px] text-muted">{data ? scheduleText(data.schedule) : '…'}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="secondary" disabled={exportEval.isPending} onClick={startExport}>
            <Download aria-hidden />
            Export for labeling
          </Button>
          <RescoreDialog budgetUsd={data?.llmUsage.monthlyBudgetUsd ?? 0} disabled={!data} />
          <Button variant="primary" disabled={!data || running || run.isPending} onClick={startRun}>
            {running ? <Loader2 className="animate-spin" aria-hidden /> : <Play aria-hidden />}
            {running ? 'Running…' : 'Run pipeline'}
          </Button>
        </div>
      </div>

      {overview.isPending ? (
        <ListSkeleton rows={4} />
      ) : overview.isError ? (
        <ErrorState error={overview.error} title="Could not load the system overview" />
      ) : (
        <>
          <PipelineBanner progress={overview.data.pipelineProgress} running={overview.data.pipelineRunning} />
          <KpiRow kpis={overview.data.kpis} callsToday={overview.data.llmUsage.callsToday} />
          <LlmModelsCard />
          <div className="flex flex-wrap gap-5">
            <SourcesTable sources={overview.data.sources} />
            <LlmUsageCard usage={overview.data.llmUsage} />
          </div>
          <div className="flex flex-wrap gap-5">
            <RunsList reports={overview.data.pipelineReports} />
            <FailuresList failures={overview.data.failures} />
          </div>
          <CompaniesTable />
          <BulkActionTable budgetUsd={overview.data.llmUsage.monthlyBudgetUsd} />
        </>
      )}
    </main>
  )
}
