import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dailyUsage } from '@/test/adminFixtures'
import { LlmUsageCard } from './LlmUsageCard'

const calls = [3, 0, 5, 8, 2, 0, 0, 4, 6, 9, 1, 0, 7, 14]

describe('LlmUsageCard', () => {
  it('shows the month-to-date cost against the budget, calls and tokens today', () => {
    render(
      <LlmUsageCard usage={{ monthToDateUsd: 1.25, monthlyBudgetUsd: 5, callsToday: 14, daily: dailyUsage(calls) }} />,
    )

    expect(screen.getByText('$1.25')).toBeInTheDocument()
    expect(screen.getByText('$5.00')).toBeInTheDocument()
    const meter = screen.getByRole('meter', { name: 'Monthly budget used' })
    expect(meter).toHaveAttribute('aria-valuenow', '1.25')
    expect(meter.firstElementChild).toHaveStyle({ width: '25%' })
    expect(screen.getByText('14 calls today · 32k tokens')).toBeInTheDocument()
  })

  it('draws 14 bars, today in the accent and scaled to the biggest day; calls while nothing cost money', () => {
    render(
      <LlmUsageCard usage={{ monthToDateUsd: 0, monthlyBudgetUsd: 5, callsToday: 14, daily: dailyUsage(calls) }} />,
    )

    const bars = within(screen.getByRole('list', { name: 'Daily LLM calls' })).getAllByRole('listitem')
    expect(bars).toHaveLength(14)
    expect(bars[13]).toHaveClass('bg-accent')
    expect(bars[13]).toHaveStyle({ height: '100%' })
    expect(bars[0]).toHaveClass('bg-accent-bar')
    expect(bars[3]).toHaveStyle({ height: `${(8 / 14) * 100}%` })
    expect(bars[1]).toHaveStyle({ height: '2%' })
    expect(bars[13]).toHaveAccessibleName(/14 calls, \$0\.00/)
    expect(screen.getByText('6 Oct')).toBeInTheDocument()
    expect(screen.getByText('19 Oct')).toBeInTheDocument()
  })

  it('bars show cost once any day cost money; the meter warns from 80% of the budget', () => {
    const costs = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.5, 2]
    render(
      <LlmUsageCard
        usage={{ monthToDateUsd: 4.2, monthlyBudgetUsd: 5, callsToday: 14, daily: dailyUsage(calls, costs) }}
      />,
    )

    const bars = within(screen.getByRole('list', { name: 'Daily LLM cost' })).getAllByRole('listitem')
    expect(bars[12]).toHaveStyle({ height: '25%' })
    expect(screen.getByRole('meter').firstElementChild).toHaveClass('bg-attention-fg')
  })
})
