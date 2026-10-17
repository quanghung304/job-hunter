import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ScoreBadge } from './ScoreBadge'

describe('ScoreBadge', () => {
  it.each([92, 76, 55, 12])('always renders the number (%s)', (score) => {
    render(<ScoreBadge score={score} />)

    expect(screen.getByText(String(score))).toBeInTheDocument()
  })

  it('renders a dash and an accessible label when unscored', () => {
    render(<ScoreBadge score={null} />)

    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Not scored' })).toBeInTheDocument()
  })

  it('shows the band word only on lg', () => {
    const { rerender } = render(<ScoreBadge score={88} />)
    expect(screen.queryByText('EXCELLENT')).not.toBeInTheDocument()

    rerender(<ScoreBadge score={88} size="lg" />)
    expect(screen.getByText('88')).toBeInTheDocument()
    expect(screen.getByText('EXCELLENT')).toBeInTheDocument()
  })

  it.each([
    [90, 'bg-accent'],
    [75, 'bg-accent-tint'],
    [60, 'bg-maybe-bg'],
    [30, 'bg-transparent'],
  ])('uses the band style for %s', (score, className) => {
    render(<ScoreBadge score={score} />)

    expect(screen.getByRole('img')).toHaveClass(className)
  })

  it('describes the score for screen readers', () => {
    render(<ScoreBadge score={72} />)

    expect(screen.getByRole('img', { name: 'Score 72 of 100, good' })).toBeInTheDocument()
  })
})
