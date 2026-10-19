import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { TrackingStatus } from '@/lib/labels'
import { allowedTargets } from '@/lib/statusMachine'
import { StatusActions } from './StatusActions'

function renderActions(status: TrackingStatus, allowed = allowedTargets(status)) {
  const onChange = vi.fn()
  render(<StatusActions status={status} allowed={allowed} onChange={onChange} />)
  return onChange
}

describe('StatusActions', () => {
  it('shows the next step as the one primary button, then secondary, then a quiet Dismiss', () => {
    renderActions('NEW')

    const buttons = screen.getAllByRole('button')
    expect(buttons.map((b) => b.textContent)).toEqual(['Shortlists', 'Mark applieda', 'Dismissd'])
    expect(buttons.map((b) => b.dataset.variant)).toEqual(['primary', 'secondary', 'quiet'])
    expect(document.querySelectorAll('[data-variant="primary"]')).toHaveLength(1)
  })

  it.each([
    ['SHORTLISTED', ['Mark applied', 'Unshortlist', 'Dismiss']],
    ['DISMISSED', ['Restore']],
    ['APPLIED', ['Interviewing', 'Rejected', 'Withdraw']],
    ['INTERVIEWING', ['Offer', 'Rejected', 'Withdraw']],
  ] as [TrackingStatus, string[]][])('%s offers %s', (status, labels) => {
    renderActions(status)

    const names = screen.getAllByRole('button').map((b) => b.childNodes[0]?.textContent)
    expect(names).toEqual(labels)
  })

  it('follows the server list, not its own idea of the machine', () => {
    renderActions('NEW', ['DISMISSED'])

    expect(screen.getAllByRole('button')).toHaveLength(1)
    expect(document.querySelector('[data-variant="primary"]')).toBeNull()
  })

  it('a terminal status has no buttons', () => {
    renderActions('OFFER')

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByText('Final status — notes stay editable.')).toBeInTheDocument()
  })

  it('reports the chosen status', async () => {
    const onChange = renderActions('SHORTLISTED')

    await userEvent.click(screen.getByRole('button', { name: /Mark applied/ }))

    expect(onChange).toHaveBeenCalledWith('APPLIED')
  })
})
