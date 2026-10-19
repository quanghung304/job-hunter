import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Decision } from '@/lib/labels'
import { EligibilityDecision } from './EligibilityDecision'

function pressed() {
  return screen
    .getAllByRole('button')
    .filter((button) => button.getAttribute('aria-pressed') === 'true')
    .map((button) => button.textContent?.replace(/[en]$/, ''))
}

describe('EligibilityDecision', () => {
  it.each([
    ['ELIGIBLE', 'Eligible', 'bg-eligible-bg'],
    ['INELIGIBLE', 'Not eligible', 'bg-ineligible-bg'],
    [null, 'Undecided', 'bg-neutral-fill'],
  ] as [Decision | null, string, string][])('%s presses "%s" only, in its colors', (value, label, className) => {
    render(<EligibilityDecision value={value} onChange={() => {}} />)

    expect(screen.getByRole('group', { name: 'Your decision' })).toBeInTheDocument()
    expect(pressed()).toEqual([label])
    expect(screen.getByRole('button', { name: new RegExp(`^${label}`) })).toHaveClass(className)
  })

  it('pressing another value reports it; Undecided clears', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<EligibilityDecision value="ELIGIBLE" onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: /^Not eligible/ }))
    await user.click(screen.getByRole('button', { name: /^Undecided/ }))

    expect(onChange.mock.calls).toEqual([['INELIGIBLE'], [null]])
  })

  it('pressing the current value changes nothing', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<EligibilityDecision value="INELIGIBLE" onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: /^Not eligible/ }))

    expect(onChange).not.toHaveBeenCalled()
  })

  it('names the keyboard shortcuts', () => {
    render(<EligibilityDecision value={null} onChange={() => {}} />)

    expect(screen.getByRole('button', { name: /^Eligible/ })).toHaveAttribute('title', 'Eligible (e)')
    expect(screen.getByRole('button', { name: /^Not eligible/ })).toHaveAttribute('title', 'Not eligible (n)')
  })
})
