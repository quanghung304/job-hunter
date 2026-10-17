import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/client'
import { ErrorState } from './ErrorState'

describe('ErrorState', () => {
  it('shows the Problem Details detail with status and code', () => {
    render(
      <ErrorState
        title="Could not change status"
        error={
          new ApiError({
            status: 409,
            code: 'ILLEGAL_STATUS_TRANSITION',
            detail: 'Cannot change status from OFFER to NEW',
          })
        }
      />,
    )

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText('Could not change status')).toBeInTheDocument()
    expect(screen.getByText('Cannot change status from OFFER to NEW')).toBeInTheDocument()
    expect(screen.getByText('409 · ILLEGAL_STATUS_TRANSITION')).toHaveClass('num')
  })

  it('has a dedicated message when the backend is not reachable', () => {
    render(<ErrorState error={ApiError.network(new TypeError('Failed to fetch'))} />)

    expect(screen.getByText('Backend not reachable')).toBeInTheDocument()
    expect(screen.getByText('Is the app running?')).toBeInTheDocument()
    expect(screen.queryByText(/·/)).not.toBeInTheDocument()
  })

  it('falls back to the message of a plain error', () => {
    render(<ErrorState error={new Error('boom')} />)

    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
    expect(screen.getByText('boom')).toBeInTheDocument()
  })

  it('renders an optional action', () => {
    render(<ErrorState error={new Error('boom')} action={<button>Retry</button>} />)

    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
