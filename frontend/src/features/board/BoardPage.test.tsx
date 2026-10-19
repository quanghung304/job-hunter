import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { JobSummary } from '@/api/queries'
import { Toaster } from '@/components/ui/sonner'
import { type JobsApi, llmRequests, mockJobsApi, summary } from '@/test/jobFixtures'
import { renderWithProviders } from '@/test/render'
import { BoardPage } from './BoardPage'
import { LAYOUT_KEY } from './boardLayout'

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

/** Jobs 1…n, best score first (the backend's default order). */
function jobs(n: number, overrides: (id: number) => Partial<JobSummary> = () => ({})): JobSummary[] {
  return Array.from({ length: n }, (_, i) => summary(i + 1, { score: 99 - i, ...overrides(i + 1) }))
}

function cards() {
  return within(screen.getByRole('list', { name: 'Jobs' })).queryAllByRole('article')
}

function card(title: string) {
  return screen.getByRole('article', { name: title })
}

async function gridLoaded() {
  await waitFor(() => expect(cards().length).toBeGreaterThan(0))
}

describe('BoardPage — grid (desktop)', () => {
  let api: JobsApi

  beforeEach(() => {
    window.localStorage.clear()
    api = mockJobsApi(
      jobs(30, (id) =>
        id === 3 ? { score: null, scoreBand: 'NONE', summary: null, checks: null, analysisStage: 'NOT_ANALYZED' } : {},
      ),
    )
    vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows 12 cards with the legend and numbered pages, without any LLM request', async () => {
    renderBoard()
    await gridLoaded()

    expect(cards()).toHaveLength(12)
    expect(screen.getByText('Showing 1–12 of 30 jobs')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('note', { name: 'Legend' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Status: All but dismissed' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Decision: Not marked ineligible' })).toBeInTheDocument()
    expect(api.requests).toContain('GET /api/jobs?sort=score&page=0&size=12')
    expect(llmRequests(api)).toEqual([])
    expect(screen.queryByRole('button', { name: /^(Analyze|Score)$/ })).not.toBeInTheDocument()
  })

  it('pages are in the URL (1-based) and sent 0-based to the API', async () => {
    const user = userEvent.setup()
    renderBoard()
    await gridLoaded()

    await user.click(screen.getByRole('button', { name: 'Page 3' }))

    await waitFor(() => expect(location.current).toBe('/?page=3'))
    await waitFor(() => expect(screen.getByText('Showing 25–30 of 30 jobs')).toBeInTheDocument())
    expect(cards()).toHaveLength(6)
    expect(api.requests).toContain('GET /api/jobs?sort=score&page=2&size=12')
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
  })

  it('reads filters and page from the URL and sends them to the API', async () => {
    renderBoard('/?status=APPLIED&sort=posted&page=2')

    await waitFor(() => expect(api.requests).toContain('GET /api/jobs?status=APPLIED&sort=posted&page=1&size=12'))
    expect(await screen.findByText('No jobs match these filters.')).toBeInTheDocument()
  })

  it('a filter chosen in the bar goes to the URL and the API and resets the page', async () => {
    const user = userEvent.setup()
    renderBoard('/?page=2')
    await gridLoaded()

    await user.click(screen.getByRole('button', { name: 'Status: All but dismissed' }))
    await user.click(await screen.findByRole('checkbox', { name: 'Shortlisted' }))

    await waitFor(() => expect(location.current).toBe('/?status=SHORTLISTED'))
    await waitFor(() => expect(api.requests).toContain('GET /api/jobs?status=SHORTLISTED&sort=score&page=0&size=12'))
  })

  it('dismissing removes the card, the page refills, Undo brings it back; a reload keeps it gone', async () => {
    const user = userEvent.setup()
    const { unmount } = renderBoard()
    await gridLoaded()

    await user.click(within(card('Java Developer 1')).getByRole('button', { name: 'Dismiss' }))

    await waitFor(() => expect(screen.queryByRole('article', { name: 'Java Developer 1' })).not.toBeInTheDocument())
    expect(api.jobs[0].status).toBe('DISMISSED')
    await waitFor(() => expect(screen.getByRole('article', { name: 'Java Developer 13' })).toBeInTheDocument())
    expect(cards()).toHaveLength(12)

    await user.click(await screen.findByRole('button', { name: 'Undo' }))
    await waitFor(() => expect(api.jobs[0].status).toBe('NEW'))

    await user.click(within(card('Java Developer 2')).getByRole('button', { name: 'Dismiss' }))
    await waitFor(() => expect(api.jobs[1].status).toBe('DISMISSED'))
    unmount()

    renderBoard()
    await gridLoaded()
    expect(screen.getByRole('article', { name: 'Java Developer 1' })).toBeInTheDocument()
    expect(screen.queryByRole('article', { name: 'Java Developer 2' })).not.toBeInTheDocument()
  })

  it('a refused status change is rolled back and shows the server message', async () => {
    const user = userEvent.setup()
    renderBoard()
    await gridLoaded()
    api.failStatusChange = {
      status: 409,
      code: 'ILLEGAL_STATUS_TRANSITION',
      detail: 'Cannot change status from NEW to DISMISSED',
    }

    await user.click(within(card('Java Developer 2')).getByRole('button', { name: 'Dismiss' }))

    expect(
      await screen.findByText('Could not change the status: Cannot change status from NEW to DISMISSED'),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('article', { name: 'Java Developer 2' })).toBeInTheDocument())
    expect(api.jobs[1].status).toBe('NEW')
  })

  it('the star toggles the shortlist', async () => {
    const user = userEvent.setup()
    renderBoard()
    await gridLoaded()

    await user.click(within(card('Java Developer 2')).getByRole('button', { name: 'Shortlist' }))

    await waitFor(() => expect(api.jobs[1].status).toBe('SHORTLISTED'))
    expect(within(card('Java Developer 2')).getByText('Shortlisted')).toBeInTheDocument()
    expect(within(card('Java Developer 2')).getByRole('button', { name: 'Remove from shortlist' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('the layout toggle switches to the list and is remembered', async () => {
    const user = userEvent.setup()
    const { unmount } = renderBoard()
    await gridLoaded()
    expect(screen.getByRole('button', { name: 'Grid' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'List' }))

    expect(screen.getByRole('button', { name: 'List' })).toHaveAttribute('aria-pressed', 'true')
    expect(window.localStorage.getItem(LAYOUT_KEY)).toBe('list')
    expect(cards()).toHaveLength(12)
    unmount()

    renderBoard()
    await gridLoaded()
    expect(screen.getByRole('button', { name: 'List' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('a card opens the job keeping the filters and the page', async () => {
    const user = userEvent.setup()
    renderBoard('/?sort=posted&page=2')
    await gridLoaded()

    await user.click(screen.getByRole('link', { name: 'Java Developer 14' }))

    await waitFor(() => expect(location.current).toBe('/jobs/14?sort=posted&page=2'))
    expect(await screen.findByRole('heading', { name: 'Java Developer 14', level: 1 })).toBeInTheDocument()
  })

  it('keyboard on the grid: j/k focus cards, s shortlists, e marks eligible, d dismisses; typing is ignored', async () => {
    const user = userEvent.setup()
    renderBoard()
    await gridLoaded()

    await user.keyboard('j')
    expect(screen.getByRole('link', { name: 'Java Developer 1' })).toHaveFocus()
    await user.keyboard('j')
    expect(screen.getByRole('link', { name: 'Java Developer 2' })).toHaveFocus()
    await user.keyboard('k')
    expect(screen.getByRole('link', { name: 'Java Developer 1' })).toHaveFocus()

    await user.keyboard('s')
    await waitFor(() => expect(api.jobs[0].status).toBe('SHORTLISTED'))
    await user.keyboard('e')
    await waitFor(() => expect(api.jobs[0].eligibilityDecision).toBe('ELIGIBLE'))
    await user.keyboard('d')
    await waitFor(() => expect(api.jobs[0].status).toBe('DISMISSED'))
    await waitFor(() => expect(screen.getByRole('link', { name: 'Java Developer 2' })).toHaveFocus())

    const search = screen.getByRole('searchbox', { name: 'Search title or company' })
    await user.keyboard('/')
    expect(search).toHaveFocus()
    await user.keyboard('dj')
    expect(search).toHaveValue('dj')
    await user.keyboard('{Escape}')
    expect(search).not.toHaveFocus()
    expect(api.jobs[1].status).toBe('NEW')
  })
})

describe('BoardPage — split view (desktop)', () => {
  let api: JobsApi

  beforeEach(() => {
    window.localStorage.clear()
    api = mockJobsApi(
      jobs(40, (id) =>
        id === 3 ? { score: null, scoreBand: 'NONE', summary: null, checks: null, analysisStage: 'NOT_ANALYZED' } : {},
      ),
    )
    vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('opened from grid page 3, the list holds pages 1–3 with the job in view', async () => {
    const scrollIntoView = vi.spyOn(Element.prototype, 'scrollIntoView')
    renderBoard('/jobs/26?page=3')

    expect(await screen.findByRole('heading', { name: 'Java Developer 26', level: 1 })).toBeInTheDocument()
    await waitFor(() => expect(cards()).toHaveLength(36))
    expect(screen.getByText('36 of 40')).toBeInTheDocument()
    const selected = screen.getByRole('link', { name: 'Java Developer 26' })
    expect(selected).toHaveAttribute('aria-current', 'true')
    await waitFor(() => expect(scrollIntoView.mock.contexts).toContain(selected.closest('li')))
    expect(screen.getByRole('link', { name: 'All jobs' })).toHaveAttribute('href', '/?page=3')
    expect(llmRequests(api)).toEqual([])
  })

  it('"Load more" appends the next jobs without moving the list scroll', async () => {
    const user = userEvent.setup()
    renderBoard('/jobs/2')
    await waitFor(() => expect(cards()).toHaveLength(12))
    const scroll = screen.getByTestId('job-list-scroll')
    scroll.scrollTop = 300
    const first = cards()[0]

    await user.click(screen.getByRole('button', { name: 'Load 12 more · 12 of 40' }))

    await waitFor(() => expect(cards()).toHaveLength(24))
    expect(scroll.scrollTop).toBe(300)
    expect(cards()[0]).toBe(first)
    expect(api.requests).toContain('GET /api/jobs?sort=score&page=1&size=12')
  })

  it('selecting another job resets the detail scroll and keeps the list mounted', async () => {
    const user = userEvent.setup()
    renderBoard('/jobs/1')
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })
    const detailScroll = screen.getByTestId('job-detail-scroll')
    const listScroll = screen.getByTestId('job-list-scroll')
    detailScroll.scrollTop = 500
    listScroll.scrollTop = 120

    await user.click(screen.getByRole('link', { name: 'Java Developer 4' }))

    expect(await screen.findByRole('heading', { name: 'Java Developer 4', level: 1 })).toBeInTheDocument()
    expect(detailScroll.scrollTop).toBe(0)
    expect(screen.getByTestId('job-list-scroll')).toBe(listScroll)
    expect(listScroll.scrollTop).toBe(120)
  })

  it('keyboard: j/k move (the URL page follows), d dismisses and selects the next, Esc returns to the grid page of the job', async () => {
    const user = userEvent.setup()
    renderBoard('/jobs/12')
    await screen.findByRole('heading', { name: 'Java Developer 12', level: 1 })
    await waitFor(() => expect(cards()).toHaveLength(12))
    await user.click(screen.getByRole('button', { name: /Load 12 more/ }))
    await waitFor(() => expect(cards()).toHaveLength(24))

    await user.keyboard('j')
    expect(await screen.findByRole('heading', { name: 'Java Developer 13', level: 1 })).toBeInTheDocument()
    expect(location.current).toBe('/jobs/13?page=2')

    await user.keyboard('k')
    expect(await screen.findByRole('heading', { name: 'Java Developer 12', level: 1 })).toBeInTheDocument()

    await user.keyboard('o')
    expect(window.open).toHaveBeenCalledWith('https://himalayas.app/jobs/12', '_blank', 'noopener,noreferrer')

    await user.keyboard('d')
    await waitFor(() => expect(api.jobs[11].status).toBe('DISMISSED'))
    expect(await screen.findByRole('heading', { name: 'Java Developer 13', level: 1 })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Java Developer 12' })).not.toBeInTheDocument()

    // Job 13 moved up to index 11 (page 1) when 12 left the list.
    await user.keyboard('{Escape}')
    await waitFor(() => expect(location.current).toBe('/'))
    await gridLoaded()
  })

  it('e / n set the decision (pressing again clears) with Undo; keys are ignored while typing', async () => {
    const user = userEvent.setup()
    renderBoard('/jobs/1')
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })
    const decision = screen.getByRole('group', { name: 'Your decision' })

    await user.keyboard('e')
    await waitFor(() => expect(api.jobs[0].eligibilityDecision).toBe('ELIGIBLE'))
    expect(within(decision).getByRole('button', { name: /^Eligible/ })).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByText('Marked eligible')).toBeInTheDocument()

    await user.keyboard('e')
    await waitFor(() => expect(api.jobs[0].eligibilityDecision).toBeNull())
    expect(await screen.findByText('Decision cleared')).toBeInTheDocument()

    await user.keyboard('n')
    await waitFor(() => expect(api.jobs[0].eligibilityDecision).toBe('INELIGIBLE'))

    await user.click(screen.getByRole('searchbox', { name: 'Search title or company' }))
    await user.keyboard('en')
    expect(api.jobs[0].eligibilityDecision).toBe('INELIGIBLE')
    expect(api.requests.filter((r) => r.includes('/eligibility'))).toHaveLength(3)
  })

  it('marking Not eligible keeps the card, Undo restores; after a refetch the job leaves the default view', async () => {
    const user = userEvent.setup()
    const { unmount } = renderBoard('/jobs/2')
    await screen.findByRole('heading', { name: 'Java Developer 2', level: 1 })

    await user.click(screen.getByRole('button', { name: /^Not eligible/ }))

    await waitFor(() => expect(api.jobs[1].eligibilityDecision).toBe('INELIGIBLE'))
    const listCard = within(screen.getByRole('list', { name: 'Jobs' })).getByRole('article', {
      name: 'Java Developer 2',
    })
    expect(within(listCard).getByText('Not eligible')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Score' })).not.toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: 'Undo' }))
    await waitFor(() => expect(api.jobs[1].eligibilityDecision).toBeNull())

    await user.click(screen.getByRole('button', { name: /^Not eligible/ }))
    await waitFor(() => expect(api.jobs[1].eligibilityDecision).toBe('INELIGIBLE'))
    unmount()

    renderBoard('/')
    await gridLoaded()
    expect(screen.queryByRole('article', { name: 'Java Developer 2' })).not.toBeInTheDocument()
  })

  it('a refused decision is rolled back and shows the server message', async () => {
    const user = userEvent.setup()
    renderBoard('/jobs/1')
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })
    api.failDecision = { status: 409, code: 'CONFLICT', detail: 'Decision refused' }

    await user.click(screen.getByRole('button', { name: /^Eligible/ }))

    expect(await screen.findByText('Could not change the decision: Decision refused')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Undecided/ })).toHaveAttribute('aria-pressed', 'true')
  })

  it('Analyze sends exactly one request and updates the list card', async () => {
    const user = userEvent.setup()
    renderBoard('/jobs/3')
    await screen.findByRole('heading', { name: 'Java Developer 3', level: 1 })
    expect(screen.getByText('Not analyzed yet')).toBeInTheDocument()

    await user.click(screen.getAllByRole('button', { name: 'Analyze' })[0])

    await waitFor(() => expect(screen.getByRole('button', { name: 'Score' })).toBeEnabled())
    expect(llmRequests(api)).toEqual(['POST /api/jobs/3/analyze'])
    const listCard = within(screen.getByRole('list', { name: 'Jobs' })).getByRole('article', {
      name: 'Java Developer 3',
    })
    expect(within(listCard).getByText('Extracted')).toBeInTheDocument()
  })

  it('the detail has exactly one primary button', async () => {
    renderBoard('/jobs/1')
    await screen.findByRole('heading', { name: 'Java Developer 1', level: 1 })

    expect(document.querySelectorAll('[data-variant="primary"]')).toHaveLength(1)
  })
})
