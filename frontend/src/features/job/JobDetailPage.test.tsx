import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { llmRequests, mockJobsApi, summary } from '@/test/jobFixtures'
import { renderWithProviders } from '@/test/render'
import { JobDetailPage } from './JobDetailPage'
import { NOTES_DELAY_MS } from './NotesEditor'

function renderPage(id: number) {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/jobs/:id" element={<JobDetailPage id={id} />} />
      </Routes>
      <Toaster />
    </>,
    { route: `/jobs/${id}?status=NEW` },
  )
}

describe('JobDetailPage', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows every panel in order, with the summary in the profile language', async () => {
    mockJobsApi([summary(1, { score: 80, eligibility: 'UNCERTAIN' })])
    renderPage(1)

    expect(await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })).toBeInTheDocument()
    const labels = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(labels).toEqual([
      'Why it fits',
      'Score breakdown',
      'Eligibility',
      'Extracted facts',
      'Description',
      'Notes',
      'History',
    ])
    await waitFor(() => expect(screen.getByText('Good match 1')).toHaveAttribute('lang', 'vi'))
    expect(screen.getByText('Khi apply:')).toBeInTheDocument()
    expect(screen.getByText('Java 17 and Spring')).toBeInTheDocument()
    expect(screen.getByText('None found')).toBeInTheDocument()
    expect(screen.getByLabelText('Skills 32 of 40')).toHaveTextContent('32/40')
    const eligibility = within(screen.getByRole('heading', { name: 'Eligibility' }).closest('section') as HTMLElement)
    expect(eligibility.getByText('Time zone').closest('div')).toHaveTextContent('Unclear')
    expect(eligibility.getByText('Work mode').closest('div')).toHaveTextContent('OK')
    expect(screen.getByText('Backend role.')).toBeInTheDocument()
    expect(screen.getAllByText('Needs check').length).toBeGreaterThan(0)
  })

  it('links to the original posting in a new tab', async () => {
    mockJobsApi([summary(1)])
    renderPage(1)

    const via = await screen.findByRole('link', { name: 'Himalayas' })
    expect(via).toHaveAttribute('href', 'https://himalayas.app/jobs/1')
    expect(via).toHaveAttribute('target', '_blank')
    expect(via.getAttribute('rel')).toContain('noopener')
    expect(screen.getByRole('link', { name: /Posting/ })).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('hides "Job N of M" when opened directly and offers the back link with the filters', async () => {
    mockJobsApi([summary(1)])
    renderPage(1)

    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })
    expect(screen.queryByText(/Job \d+ of/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the board' })).toHaveAttribute('href', '/?status=NEW')
  })

  it('the action bar changes the status and the history follows', async () => {
    const user = userEvent.setup()
    const api = mockJobsApi([summary(1)])
    renderPage(1)
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })

    expect(document.querySelectorAll('[data-variant="primary"]')).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Shortlist' }))

    await waitFor(() => expect(api.jobs[0].status).toBe('SHORTLISTED'))
    expect(await screen.findByRole('button', { name: 'Mark applied' })).toBeInTheDocument()
    expect(screen.getByText('Marked shortlisted')).toBeInTheDocument()
    expect(within(screen.getByRole('article')).getByText('Shortlisted')).toBeInTheDocument()
  })

  it('a refused change restores the status and shows the server message', async () => {
    const user = userEvent.setup()
    const api = mockJobsApi([summary(1)])
    renderPage(1)
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })
    api.failStatusChange = { status: 409, code: 'ILLEGAL_STATUS_TRANSITION', detail: 'Cannot change status' }

    await user.click(screen.getByRole('button', { name: 'Shortlist' }))

    expect(await screen.findByText('Could not change the status: Cannot change status')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Shortlist' })).toBeInTheDocument()
    expect(within(screen.getByRole('article')).getByText('New')).toBeInTheDocument()
  })

  it('notes autosave one second after typing stops', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const api = mockJobsApi([summary(1)])
    renderPage(1)
    const notes = await screen.findByRole('textbox', { name: 'Notes' })
    expect(screen.getByText('Autosaves as you type')).toBeInTheDocument()

    await user.type(notes, 'Call Anna')
    act(() => vi.advanceTimersByTime(NOTES_DELAY_MS - 100))
    expect(api.requests.filter((r) => r.startsWith('PUT'))).toEqual([])

    act(() => vi.advanceTimersByTime(200))

    await waitFor(() => expect(api.notes[1]).toBe('Call Anna'))
    expect(api.requests.filter((r) => r.startsWith('PUT'))).toEqual(['PUT /api/jobs/1/notes'])
    expect(await screen.findByText('Saved · just now')).toBeInTheDocument()
  })

  it('Score on an analyzed eligible job sends one request; nothing calls the LLM on load', async () => {
    const user = userEvent.setup()
    const api = mockJobsApi([summary(1, { score: null, scoreBand: 'NONE', summary: null, analysisStage: 'ANALYZED' })])
    renderPage(1)
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })
    expect(llmRequests(api)).toEqual([])

    await user.click(screen.getByRole('button', { name: 'Score' }))

    expect(await screen.findByRole('img', { name: /Score 77 of 100/ })).toBeInTheDocument()
    expect(llmRequests(api)).toEqual(['POST /api/jobs/1/score'])
    expect(screen.queryByRole('button', { name: /Score|Scoring/ })).not.toBeInTheDocument()
  })

  it('unknown job shows the Problem Details', async () => {
    mockJobsApi([])
    renderPage(9)

    expect(await screen.findByText('Job 9 not found')).toBeInTheDocument()
    expect(screen.getByText('404 · JOB_NOT_FOUND')).toBeInTheDocument()
  })
})
