import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { JobSummary } from '@/api/queries'
import { summary } from '@/test/jobFixtures'
import { JobListPanel } from './JobListPanel'

const ALL = Array.from({ length: 30 }, (_, i) => summary(i + 1))

/** The panel with "Load more" appending the next 12 of 30 jobs, like useInfiniteJobs. */
function Harness({ selectedId = 1, initial = 12 }: { selectedId?: number; initial?: number }) {
  const [jobs, setJobs] = useState<JobSummary[]>(ALL.slice(0, initial))
  return (
    <JobListPanel
      jobs={jobs}
      total={ALL.length}
      selectedId={selectedId}
      cardSearch={(index) => `page=${Math.floor(index / 12) + 1}`}
      backSearch="page=1"
      query=""
      onQueryChange={() => {}}
      isPending={false}
      error={null}
      hasMore={jobs.length < ALL.length}
      loadingMore={false}
      onLoadMore={() => setJobs(ALL.slice(0, jobs.length + 12))}
      leavingId={null}
      onShortlistToggle={() => {}}
      pageSize={12}
    />
  )
}

function renderPanel(props: { selectedId?: number; initial?: number } = {}) {
  return render(
    <MemoryRouter>
      <Harness {...props} />
    </MemoryRouter>,
  )
}

function cards() {
  return within(screen.getByRole('list', { name: 'Jobs' })).getAllByRole('article')
}

describe('JobListPanel', () => {
  it('"Load more" appends the next 12 without changing the scroll position or remounting cards', async () => {
    const user = userEvent.setup()
    renderPanel()
    const scroll = screen.getByTestId('job-list-scroll')
    const firstCard = cards()[0]
    expect(screen.getByText('12 of 30')).toBeInTheDocument()
    scroll.scrollTop = 480

    await user.click(screen.getByRole('button', { name: 'Load 12 more · 12 of 30' }))

    expect(cards()).toHaveLength(24)
    expect(scroll.scrollTop).toBe(480)
    expect(cards()[0]).toBe(firstCard)

    await user.click(screen.getByRole('button', { name: 'Load 6 more · 24 of 30' }))

    expect(cards()).toHaveLength(30)
    expect(scroll.scrollTop).toBe(480)
    expect(screen.getByText('All 30 jobs loaded')).toBeInTheDocument()
  })

  it('marks the selected card and scrolls it into view once, not again on "Load more"', async () => {
    const user = userEvent.setup()
    const scrollIntoView = vi.spyOn(Element.prototype, 'scrollIntoView')
    renderPanel({ selectedId: 20, initial: 24 })

    const selected = screen.getByRole('link', { name: 'Java Developer 20' })
    expect(selected).toHaveAttribute('aria-current', 'true')
    expect(scrollIntoView).toHaveBeenCalledTimes(1)
    expect(scrollIntoView.mock.contexts[0]).toBe(selected.closest('li'))
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' })

    await user.click(screen.getByRole('button', { name: /Load 6 more/ }))

    expect(scrollIntoView).toHaveBeenCalledTimes(1)
    scrollIntoView.mockRestore()
  })

  it('card links keep the page of each card; "All jobs" goes back to the grid', () => {
    renderPanel({ initial: 24 })

    expect(screen.getByRole('link', { name: 'Java Developer 13' })).toHaveAttribute('href', '/jobs/13?page=2')
    expect(screen.getByRole('link', { name: 'All jobs' })).toHaveAttribute('href', '/?page=1')
    expect(screen.getByText(/e eligible · n not eligible · Esc back/)).toBeInTheDocument()
  })
})
