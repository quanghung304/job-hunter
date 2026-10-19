import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { JobSummary } from '@/api/queries'
import { summary } from '@/test/jobFixtures'
import { JobCard } from './JobCard'

function renderCard(job: JobSummary, handlers: { onShortlistToggle?: () => void; onDismiss?: () => void } = {}) {
  render(
    <MemoryRouter>
      <ul>
        <JobCard
          job={job}
          search="status=NEW&page=3"
          onShortlistToggle={handlers.onShortlistToggle ?? (() => {})}
          onDismiss={handlers.onDismiss ?? (() => {})}
        />
      </ul>
    </MemoryRouter>,
  )
  return screen.getByRole('article', { name: job.title })
}

describe('JobCard', () => {
  it('shows score, title, company with sources, chips and the posted time', () => {
    const card = renderCard(summary(1, { score: 91, scoreBand: 'EXCELLENT' }))

    expect(within(card).getByRole('img', { name: /Score 91 of 100/ })).toBeInTheDocument()
    expect(within(card).getByRole('heading', { level: 3, name: 'Java Developer 1' })).toBeInTheDocument()
    expect(within(card).getByText('Acme 1 · via Himalayas')).toBeInTheDocument()
    expect(within(card).getByText('Contract')).toBeInTheDocument()
    expect(within(card).getByText('EU')).toBeInTheDocument()
    expect(within(card).getByText('5h')).toBeInTheDocument()
  })

  it('the title is the one link: it opens the job and keeps the search params incl. page', () => {
    const card = renderCard(summary(7))

    const links = within(card).getAllByRole('link')
    expect(links).toHaveLength(1)
    expect(links[0]).toHaveAttribute('href', '/jobs/7?status=NEW&page=3')
  })

  it('shows status (not New), analysis and decision tags; never analysis buttons', () => {
    const card = renderCard(
      summary(2, {
        status: 'SHORTLISTED',
        score: null,
        scoreBand: 'NONE',
        analysisStage: 'ANALYZED',
        eligibilityDecision: 'INELIGIBLE',
      }),
    )

    expect(within(card).getByText('Shortlisted')).toBeInTheDocument()
    expect(within(card).getByText('Extracted')).toBeInTheDocument()
    expect(within(card).getByText('Not eligible')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: /Analyze|Score/ })).not.toBeInTheDocument()
  })

  it('shows "Check N criteria" while undecided, and no status tag for New', () => {
    const card = renderCard(summary(3, { checks: { ok: 8, unknown: 1, fail: 1 } }))

    expect(within(card).getByText('Check 2 criteria')).toBeInTheDocument()
    expect(within(card).queryByText('New')).not.toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: /Analyze|Score/ })).not.toBeInTheDocument()
  })

  it('star and dismiss call their handlers', async () => {
    const user = userEvent.setup()
    const onShortlistToggle = vi.fn()
    const onDismiss = vi.fn()
    const card = renderCard(summary(4), { onShortlistToggle, onDismiss })

    await user.click(within(card).getByRole('button', { name: 'Shortlist' }))
    await user.click(within(card).getByRole('button', { name: 'Dismiss' }))

    expect(onShortlistToggle).toHaveBeenCalledOnce()
    expect(onDismiss).toHaveBeenCalledOnce()
  })

  it('a shortlisted job has a pressed, filled star', () => {
    const card = renderCard(summary(5, { status: 'SHORTLISTED' }))

    expect(within(card).getByRole('button', { name: 'Remove from shortlist' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('hover lift, shadow and transitions apply only without reduced motion', () => {
    const card = renderCard(summary(6))
    const classes = card.className.split(/\s+/)

    const motion = classes.filter((c) => /translate|shadow-card-hover|transition|duration/.test(c))
    expect(motion.length).toBeGreaterThan(0)
    expect(motion.every((c) => c.startsWith('motion-safe:'))).toBe(true)
  })
})
