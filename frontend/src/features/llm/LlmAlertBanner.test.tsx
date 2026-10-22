import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { clockTime } from '@/lib/format'
import { exhaustedAlert, mockLlmApi } from '@/test/llmFixtures'
import { renderWithProviders } from '@/test/render'
import { LlmAlertBanner } from './LlmAlertBanner'

function renderBanner() {
  return renderWithProviders(
    <>
      <LlmAlertBanner />
      <Toaster />
    </>,
  )
}

describe('LlmAlertBanner', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  it('shows nothing without alerts', async () => {
    const api = mockLlmApi(undefined, [])
    renderBanner()

    await waitFor(() => expect(api.alerts).toEqual([]))
    expect(screen.queryByRole('region', { name: 'LLM alerts' })).not.toBeInTheDocument()
  })

  it('a paused step is an attention row with the reset time, Switch model and Details', async () => {
    mockLlmApi(undefined, [exhaustedAlert()])
    renderBanner()

    const row = await screen.findByRole('alert')
    expect(row).toHaveClass('bg-attention-bg')
    expect(row).toHaveTextContent('Extraction paused')
    expect(row).toHaveTextContent('GPT-OSS 120B (Groq): Daily request limit 1000/1000 · Analyze is off')
    expect(row).toHaveTextContent(`resets ${clockTime('2026-10-23T00:00:00Z')}`)
    expect(within(row).getByRole('button', { name: 'Switch model' })).toBeInTheDocument()
    expect(within(row).getByRole('link', { name: 'Details' })).toHaveAttribute('href', '/admin#llm')
  })

  it('near the limit is a quiet row with the percentage', async () => {
    mockLlmApi(undefined, [
      exhaustedAlert({
        purpose: 'SCORE',
        status: 'NEAR_LIMIT',
        paused: false,
        detail: 'Daily request limit 812/1000 (81%)',
      }),
    ])
    renderBanner()

    const row = await screen.findByRole('status')
    expect(row).toHaveClass('bg-surface-muted')
    expect(row).toHaveTextContent("Scoring model at 81% of today's limit")
  })

  it('after an auto-switch it offers to switch back', async () => {
    mockLlmApi(undefined, [
      exhaustedAlert({
        modelId: 'groq-qwen3-27b',
        modelLabel: 'Qwen 3.8 27B (Groq)',
        status: 'AVAILABLE',
        paused: false,
        until: null,
        detail: 'Switched automatically',
        switchedFromModelId: 'groq-gpt-oss-120b',
        suggestions: [
          { modelId: 'groq-gpt-oss-120b', label: 'GPT-OSS 120B (Groq)', tier: 'FREE', status: 'AVAILABLE' },
        ],
      }),
    ])
    renderBanner()

    const row = await screen.findByRole('status')
    expect(row).toHaveTextContent(
      'Extraction uses Qwen 3.8 27B (Groq) after an auto-switch · GPT-OSS 120B (Groq) works again',
    )
    expect(within(row).getByRole('button', { name: 'Switch back' })).toBeInTheDocument()
  })

  it('dismiss hides this alert for the session; the same step with a new reset time shows again', async () => {
    const user = userEvent.setup()
    const api = mockLlmApi(undefined, [exhaustedAlert()])
    const { unmount } = renderBanner()

    await user.click(await screen.findByRole('button', { name: /^Dismiss: Extraction paused/ }))

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    unmount()
    renderBanner()
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())

    api.alerts = [exhaustedAlert({ until: '2026-10-24T00:00:00Z' })]
    unmount()
    renderBanner()
    expect(await screen.findByRole('alert')).toHaveTextContent('Extraction paused')
  })

  it('switching from the banner saves the assignment and the row goes away on the next status poll', async () => {
    const user = userEvent.setup()
    const api = mockLlmApi(undefined, [exhaustedAlert()])
    renderBanner()

    await user.click(await screen.findByRole('button', { name: 'Switch model' }))
    const dialog = await screen.findByRole('dialog', { name: 'Model for Extraction' })
    await user.click(await within(dialog).findByRole('radio', { name: /Qwen 3.8 27B/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Extraction now uses Qwen 3.8 27B (Groq)')).toBeInTheDocument()
    expect(api.puts).toEqual([{ purpose: 'EXTRACT', body: { modelId: 'groq-qwen3-27b' } }])
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })
})
