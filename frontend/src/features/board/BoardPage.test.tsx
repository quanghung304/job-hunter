import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { type JobsApi, llmRequests, mockJobsApi, summary } from '@/test/jobFixtures'
import { renderWithProviders } from '@/test/render'
import { BoardPage } from './BoardPage'

const location = { current: '' }
function LocationSpy() {
  const loc = useLocation()
  useEffect(() => {
    location.current = loc.pathname + loc.search
  })
  return null
}

function renderBoard(route = '/') {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/" element={<BoardPage />} />
        <Route path="/jobs/:id" element={<BoardPage />} />
      </Routes>
      <LocationSpy />
      <Toaster />
    </>,
    { route },
  )
}

function rows() {
  return within(screen.getByRole('list', { name: 'Jobs' })).getAllByRole('listitem')
}

function rowOf(title: string) {
  return screen.getByRole('link', { name: new RegExp(title) }).closest('li') as HTMLElement
}

describe('BoardPage (desktop)', () => {
  let api: JobsApi

  beforeEach(() => {
    api = mockJobsApi([
      summary(1, { score: 91, scoreBand: 'EXCELLENT' }),
      summary(2, { score: 72 }),
      summary(3, { score: null, scoreBand: 'NONE', summary: null, eligibility: null, analysisStage: 'NOT_ANALYZED' }),
    ])
    vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('loads the list, selects the first job and shows its detail without any LLM request', async () => {
    renderBoard()

    expect(await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })).toBeInTheDocument()
    expect(rows()).toHaveLength(3)
    expect(screen.getByRole('link', { name: /Java Developer 1/ })).toHaveAttribute('aria-current', 'true')
    expect(screen.getByText('3 jobs')).toBeInTheDocument()
    expect(location.current).toBe('/')
    expect(llmRequests(api)).toEqual([])
    expect(screen.getByText(/j\/k move/)).toBeInTheDocument()
  })

  it('reads filters from the URL and sends them to the API', async () => {
    renderBoard('/?status=APPLIED&sort=posted')

    await waitFor(() => expect(api.requests).toContain('GET /api/jobs?status=APPLIED&sort=posted&page=0&size=25'))
    expect(await screen.findByText('Inbox zero')).toBeInTheDocument()
    expect(screen.getByText('No jobs match these filters.')).toBeInTheDocument()
  })

  it('a filter chosen in the bar goes to the URL and the API', async () => {
    const user = userEvent.setup()
    renderBoard()
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })

    await user.click(screen.getByRole('button', { name: 'Status: Any' }))
    await user.click(await screen.findByRole('checkbox', { name: 'Shortlisted' }))

    await waitFor(() => expect(location.current).toBe('/?status=SHORTLISTED'))
    await waitFor(() => expect(api.requests).toContain('GET /api/jobs?status=SHORTLISTED&sort=score&page=0&size=25'))
  })

  it('dismissing removes the row at once, selects the next one and offers Undo', async () => {
    const user = userEvent.setup()
    renderBoard()
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })

    await user.click(within(rowOf('Java Developer 1')).getByRole('button', { name: 'Dismiss' }))

    await waitFor(() => expect(rows()).toHaveLength(2))
    expect(api.jobs[0].status).toBe('DISMISSED')
    expect(await screen.findByRole('heading', { name: 'Java Developer 2', level: 1 })).toBeInTheDocument()
    expect(location.current).toBe('/jobs/2')

    await user.click(await screen.findByRole('button', { name: 'Undo' }))

    await waitFor(() => expect(api.jobs[0].status).toBe('NEW'))
    await waitFor(() => expect(rows()).toHaveLength(3))
  })

  it('a refused status change is rolled back and shows the server message', async () => {
    const user = userEvent.setup()
    renderBoard()
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })
    api.failStatusChange = {
      status: 409,
      code: 'ILLEGAL_STATUS_TRANSITION',
      detail: 'Cannot change status from NEW to DISMISSED',
    }

    await user.click(within(rowOf('Java Developer 2')).getByRole('button', { name: 'Dismiss' }))

    expect(
      await screen.findByText('Could not change the status: Cannot change status from NEW to DISMISSED'),
    ).toBeInTheDocument()
    await waitFor(() => expect(rows()).toHaveLength(3))
    expect(screen.getByRole('link', { name: /Java Developer 2/ })).toBeInTheDocument()
  })

  it('the star toggles the shortlist', async () => {
    const user = userEvent.setup()
    renderBoard()
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })

    await user.click(within(rowOf('Java Developer 2')).getByRole('button', { name: 'Shortlist' }))

    await waitFor(() => expect(api.jobs[1].status).toBe('SHORTLISTED'))
    expect(within(rowOf('Java Developer 2')).getByText('Shortlisted')).toBeInTheDocument()
    expect(within(rowOf('Java Developer 2')).getByRole('button', { name: 'Remove from shortlist' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('keyboard: j/k move, s shortlists, d dismisses, a marks applied, o opens the posting', async () => {
    const user = userEvent.setup()
    renderBoard()
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })

    await user.keyboard('j')
    expect(await screen.findByRole('heading', { name: 'Java Developer 2', level: 1 })).toBeInTheDocument()
    expect(location.current).toBe('/jobs/2')

    await user.keyboard('k')
    expect(await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })).toBeInTheDocument()

    await user.keyboard('s')
    await waitFor(() => expect(api.jobs[0].status).toBe('SHORTLISTED'))

    await user.keyboard('o')
    expect(window.open).toHaveBeenCalledWith('https://himalayas.app/jobs/1', '_blank', 'noopener,noreferrer')

    await user.keyboard('j')
    await screen.findByRole('heading', { name: 'Java Developer 2', level: 1 })
    await user.keyboard('a')
    await waitFor(() => expect(api.jobs[1].status).toBe('APPLIED'))
    await waitFor(() => expect(rows()).toHaveLength(2))

    await user.keyboard('d')
    await waitFor(() => expect(api.jobs[2].status).toBe('DISMISSED'))
  })

  it('keys are ignored while typing in the search field; / focuses it and Esc leaves it', async () => {
    const user = userEvent.setup()
    renderBoard()
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })
    const search = screen.getByRole('searchbox', { name: 'Search title or company' })

    await user.keyboard('/')
    expect(search).toHaveFocus()
    await user.keyboard('dj')
    expect(search).toHaveValue('dj')
    await user.keyboard('{Escape}')
    expect(search).not.toHaveFocus()

    await waitFor(() => expect(api.requests.some((r) => r.includes('q=dj'))).toBe(true))
    expect(api.requests.filter((r) => r.startsWith('POST'))).toEqual([])
  })

  it('Analyze sends exactly one request and updates the row', async () => {
    const user = userEvent.setup()
    renderBoard()
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })

    await user.click(within(rowOf('Java Developer 3')).getByRole('button', { name: 'Analyze' }))

    await waitFor(() => expect(within(rowOf('Java Developer 3')).getByRole('button', { name: 'Score' })).toBeEnabled())
    expect(llmRequests(api)).toEqual(['POST /api/jobs/3/analyze'])
  })

  it('opening /jobs/:id keeps the filters and selects that job', async () => {
    renderBoard('/jobs/2?sort=posted')

    expect(await screen.findByRole('heading', { name: 'Java Developer 2', level: 1 })).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('link', { name: /Java Developer 2/ })).toHaveAttribute('aria-current', 'true'),
    )
    expect(screen.getByRole('link', { name: /Java Developer 1/ })).toHaveAttribute('href', '/jobs/1?sort=posted')
  })
})
