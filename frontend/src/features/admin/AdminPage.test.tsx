import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { FREE_MODEL, mockAdminApi, overviewFixture, PAID_MODEL } from '@/test/adminFixtures'
import { summary } from '@/test/jobFixtures'
import { renderWithProviders } from '@/test/render'
import { AdminPage } from './AdminPage'

function renderAdmin() {
  return renderWithProviders(
    <>
      <AdminPage />
      <Toaster />
    </>,
    { route: '/admin' },
  )
}

function section(name: string) {
  return screen.getByRole('heading', { name, level: 2 }).closest('section') as HTMLElement
}

describe('AdminPage', () => {
  it('shows schedule, KPIs, source health, runs, failures and companies; every status has a word', async () => {
    mockAdminApi()
    renderAdmin()

    expect(await screen.findByText('Next scheduled run 19:00 Europe/Rome · digest 08:30')).toBeInTheDocument()
    expect(screen.getByText('1384')).toBeInTheDocument()
    expect(screen.getByText('9 sources · 6 company boards')).toBeInTheDocument()
    expect(screen.getByText('31 rejected by the prefilter')).toBeInTheDocument()

    const sources = section('Sources')
    const row = (name: string) => within(sources).getByText(name).closest('tr') as HTMLElement
    expect(within(row('Himalayas')).getByText('OK')).toBeInTheDocument()
    expect(within(row('Jobicy')).getByText('Failed')).toBeInTheDocument()
    expect(within(row('Arbeitnow')).getByText('Off')).toBeInTheDocument()
    const error = within(row('Jobicy')).getByText(/HTTP 503/)
    expect(error).toHaveClass('truncate')
    expect(error).toHaveAttribute('title', expect.stringContaining('after 3 attempts'))

    expect(within(section('Recent pipeline runs')).getByText('Partial')).toBeInTheDocument()
    expect(
      within(section('Recent pipeline runs')).getByText(/12 new · 18 passed · 2 expired · 1m 05s/),
    ).toBeInTheDocument()
    expect(within(section('Failed LLM work')).getByRole('link', { name: 'Kotlin Engineer' })).toHaveAttribute(
      'href',
      '/jobs/41',
    )
    expect(await within(section('Companies')).findByText('Not found')).toBeInTheDocument()
    expect(within(section('Companies')).getByText('Edit list in config/companies.yaml')).toBeInTheDocument()
    expect(document.querySelectorAll('main [data-variant="primary"]')).toHaveLength(1)
  })

  it('Run pipeline: 202, the banner shows progress while polling, then the run is done', async () => {
    const user = userEvent.setup()
    const idle = overviewFixture()
    const running = overviewFixture({
      pipelineRunning: true,
      pipelineProgress: { stage: 'COLLECT', text: 'Collect 7/9 sources', percent: 62 },
    })
    const done = overviewFixture({
      pipelineReports: [{ ...idle.pipelineReports[0], runNumber: 8, status: 'SUCCESS', trigger: 'MANUAL' }],
    })
    const api = mockAdminApi([idle, running, done])
    renderAdmin()
    await screen.findByText('1384')

    await user.click(screen.getByRole('button', { name: 'Run pipeline' }))

    expect(await screen.findByText('Pipeline started')).toBeInTheDocument()
    expect(await screen.findByText('Collect 7/9 sources')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Progress' })).toHaveAttribute('aria-valuenow', '62')
    expect(screen.getByRole('button', { name: 'Running…' })).toBeDisabled()

    // Polled every 3 s while running.
    await waitFor(() => expect(screen.queryByRole('status', { name: 'Progress' })).not.toBeInTheDocument(), {
      timeout: 6000,
    })
    expect(screen.getByRole('button', { name: 'Run pipeline' })).toBeEnabled()
    expect(within(section('Recent pipeline runs')).getByText(/^Manual/)).toBeInTheDocument()
    expect(api.requests.filter((r) => r === 'POST /api/admin/pipeline/run')).toHaveLength(1)
  })

  it('a run refused with 409 says the pipeline is already running', async () => {
    const user = userEvent.setup()
    const api = mockAdminApi()
    api.run = 'conflict'
    renderAdmin()
    await screen.findByText('1384')

    await user.click(screen.getByRole('button', { name: 'Run pipeline' }))

    expect(await screen.findByText('Pipeline is already running')).toBeInTheDocument()
  })

  it('Re-score shows count, model and cost from the dry run and runs only after the confirmation', async () => {
    const user = userEvent.setup()
    const api = mockAdminApi()
    renderAdmin()
    await screen.findByText('1384')

    await user.click(screen.getByRole('button', { name: 'Re-score active jobs…' }))

    const dialog = await screen.findByRole('dialog', { name: 'Re-score active jobs?' })
    expect(await within(dialog).findByText('57', { selector: 'span' })).toBeInTheDocument()
    expect(within(dialog).getByText(FREE_MODEL.label)).toBeInTheDocument()
    expect(within(dialog).getByText('FREE')).toBeInTheDocument()
    expect(within(dialog).getByText('≈ $0.00')).toBeInTheDocument()
    expect(within(dialog).queryByText(/Paid model/)).not.toBeInTheDocument()
    expect(api.requests).toContain('POST /api/admin/rescore dryRun=true')
    expect(api.requests).not.toContain('POST /api/admin/rescore dryRun=false')

    await user.click(within(dialog).getByRole('button', { name: 'Re-score 57 jobs' }))

    expect(await screen.findByText('Re-scoring 57 jobs in the background')).toBeInTheDocument()
    expect(api.requests).toContain('POST /api/admin/rescore dryRun=false')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('Re-score with a PAID model names it; with paid calls off it cannot be confirmed', async () => {
    const user = userEvent.setup()
    const api = mockAdminApi()
    api.rescoreModel = PAID_MODEL
    const { unmount } = renderAdmin()
    await screen.findByText('1384')

    await user.click(screen.getByRole('button', { name: 'Re-score active jobs…' }))
    let dialog = await screen.findByRole('dialog')
    expect(await within(dialog).findByText('Paid model — counts against the $5.00 monthly budget.')).toBeInTheDocument()
    expect(within(dialog).getByText('PAID')).toBeInTheDocument()
    expect(within(dialog).getByText('≈ $0.09')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    unmount()

    api.rescoreModel = { ...PAID_MODEL, paidCallsAllowed: false }
    renderAdmin()
    await screen.findByText('1384')
    await user.click(screen.getByRole('button', { name: 'Re-score active jobs…' }))
    dialog = await screen.findByRole('dialog')
    expect(await within(dialog).findByText(/paid calls are switched off/)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Re-score 57 jobs' })).toBeDisabled()
    expect(api.requests).not.toContain('POST /api/admin/rescore dryRun=false')
  })

  it('Retry restarts one failed job', async () => {
    const user = userEvent.setup()
    const api = mockAdminApi()
    renderAdmin()
    await screen.findByText('1384')

    await user.click(within(section('Failed LLM work')).getByRole('button', { name: 'Retry' }))

    expect(await screen.findByText('Retry started for "Kotlin Engineer"')).toBeInTheDocument()
    expect(api.requests).toContain('POST /api/admin/jobs/41/retry EXTRACT')
  })

  it('bulk Analyze: at most 200 jobs, dry run with skipped reasons, runs only after the confirmation', async () => {
    const user = userEvent.setup()
    const api = mockAdminApi()
    api.bulkCandidates = Array.from({ length: 210 }, (_, i) =>
      summary(i + 1, { score: null, scoreBand: 'NONE', checks: null, analysisStage: 'NOT_ANALYZED' }),
    )
    renderAdmin()
    const bulk = await waitFor(() => section('Bulk actions'))
    await within(bulk).findByText('Java Developer 1')

    for (let page = 1; page <= 4; page++) {
      if (page > 1) {
        await user.click(within(bulk).getByRole('button', { name: `Page ${page}` }))
        await within(bulk).findByText(`Java Developer ${(page - 1) * 50 + 1}`)
      }
      await user.click(within(bulk).getByRole('checkbox', { name: 'Select the jobs on this page' }))
    }
    expect(within(bulk).getByText('200 selected · max 200')).toBeInTheDocument()

    await user.click(within(bulk).getByRole('button', { name: 'Page 5' }))
    await within(bulk).findByText('Java Developer 201')
    expect(within(bulk).getByRole('checkbox', { name: 'Select Java Developer 201' })).toBeDisabled()
    expect(within(bulk).getByRole('checkbox', { name: 'Select the jobs on this page' })).toBeDisabled()

    await user.click(within(bulk).getByRole('button', { name: 'Analyze selected…' }))

    const dialog = await screen.findByRole('dialog', { name: 'Analyze 200 jobs?' })
    expect((await within(dialog).findByText('Will run')).nextElementSibling).toHaveTextContent('199')
    expect(within(dialog).getByText('Estimated calls').nextElementSibling).toHaveTextContent('199')
    expect(within(dialog).getByText('1 already analyzed')).toBeInTheDocument()
    expect(within(dialog).getByText(FREE_MODEL.label)).toBeInTheDocument()
    expect(api.requests).toContain('POST /api/admin/jobs/analyze 200 dryRun=true')
    expect(api.requests).not.toContain('POST /api/admin/jobs/analyze 200 dryRun=false')

    await user.click(within(dialog).getByRole('button', { name: 'Analyze 199 jobs' }))

    expect(await screen.findByText('Analyzing 199 jobs in the background')).toBeInTheDocument()
    expect(api.requests).toContain('POST /api/admin/jobs/analyze 200 dryRun=false')
    expect(within(bulk).getByText('0 selected')).toBeInTheDocument()
  })

  it('bulk Score lists analyzed jobs with their decision and checks', async () => {
    const user = userEvent.setup()
    const api = mockAdminApi()
    api.bulkCandidates = [
      summary(1, { score: null, scoreBand: 'NONE', analysisStage: 'ANALYZED', checks: { ok: 8, unknown: 2, fail: 0 } }),
      summary(2, { score: null, scoreBand: 'NONE', analysisStage: 'ANALYZED', eligibilityDecision: 'ELIGIBLE' }),
      summary(3, { score: null, scoreBand: 'NONE', checks: null, analysisStage: 'NOT_ANALYZED' }),
    ]
    renderAdmin()
    const bulk = await waitFor(() => section('Bulk actions'))
    await within(bulk).findByText('Java Developer 3')

    await user.click(within(bulk).getByRole('button', { name: 'Score' }))

    await waitFor(() => expect(within(bulk).queryByText('Java Developer 3')).not.toBeInTheDocument())
    expect(within(bulk).getByRole('button', { name: 'Score' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(bulk).getByText('Check 2 criteria')).toBeInTheDocument()
    expect(within(bulk).getByText('Eligible')).toBeInTheDocument()
    expect(within(bulk).getAllByText('Extracted')).toHaveLength(2)
    expect(within(bulk).getByRole('button', { name: 'Score selected…' })).toBeDisabled()
  })

  it('Export for labeling explains that it comes with the eval harness', async () => {
    const user = userEvent.setup()
    mockAdminApi()
    renderAdmin()
    await screen.findByText('1384')

    await user.click(screen.getByRole('button', { name: 'Export for labeling' }))

    expect(await screen.findByText('Export for labeling comes with the eval harness (T-016)')).toBeInTheDocument()
  })
})
