import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { llmAdminFixture, mockLlmApi } from '@/test/llmFixtures'
import { renderWithProviders } from '@/test/render'
import { LlmModelsCard } from './LlmModelsCard'

function renderCard() {
  return renderWithProviders(
    <>
      <LlmModelsCard />
      <Toaster />
    </>,
  )
}

const block = (name: string) => screen.getByRole('region', { name })
const row = (label: string) => screen.getByText(label, { selector: 'td' }).closest('tr') as HTMLElement

describe('LlmModelsCard', () => {
  it('shows each step with its model, status, usage meter, toggles and fallbacks', async () => {
    mockLlmApi()
    renderCard()

    const extraction = await screen.findByRole('region', { name: 'Extraction' })
    expect(within(extraction).getByText('GPT-OSS 120B (Groq)')).toBeInTheDocument()
    expect(within(extraction).getByText(/^Exhausted until/)).toBeInTheDocument()
    const meter = within(extraction).getByRole('meter', { name: 'Extraction requests today' })
    expect(meter).toHaveAttribute('aria-valuenow', '1000')
    expect(meter.firstElementChild).toHaveClass('bg-attention-fg')
    expect(within(extraction).getByText(/1000 \/ 1000 requests today · resets/)).toBeInTheDocument()
    expect(within(extraction).getByRole('switch', { name: /Auto-switch to fallback/ })).toHaveAttribute(
      'aria-checked',
      'false',
    )
    expect(within(block('Scoring')).getByRole('switch', { name: /Auto-switch to fallback/ })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    expect(within(extraction).getByText('1. Qwen 3.8 27B (Groq)')).toBeInTheDocument()
    expect(within(extraction).getByText('2. Claude Haiku 5.5')).toBeInTheDocument()
  })

  it('lists the models with status, usage, limits and Test, and the switch history', async () => {
    mockLlmApi()
    renderCard()
    await screen.findByRole('region', { name: 'Extraction' })

    expect(within(row('Qwen 3.8 27B (Groq)')).getByText('Available')).toBeInTheDocument()
    expect(within(row('Qwen 3.8 27B (Groq)')).getByText('30/min · 1000/day')).toBeInTheDocument()
    expect(within(row('Qwen 3.8 27B (Groq)')).getByText(/120 calls · 271k tokens/)).toBeInTheDocument()
    expect(screen.getByText('Add the openrouter API key to .env')).toBeInTheDocument()
    expect(screen.queryByText('Fake (tests / UI work)', { selector: 'td' })).not.toBeInTheDocument()
    expect(screen.getByText('Scoring: Qwen 3.8 27B (Groq) → GPT-OSS 120B (Groq)')).toBeInTheDocument()
    expect(screen.getAllByText('Not evaluated yet').length).toBeGreaterThan(0)
  })

  it('a toggle saves the setting of that step', async () => {
    const user = userEvent.setup()
    const api = mockLlmApi()
    renderCard()

    await user.click(
      within(await screen.findByRole('region', { name: 'Extraction' })).getByRole('switch', {
        name: /Auto-switch to fallback/,
      }),
    )

    await waitFor(() =>
      expect(api.puts).toEqual([{ purpose: 'EXTRACT', body: { modelId: 'groq-gpt-oss-120b', autoFallback: true } }]),
    )
    expect(await screen.findByText('Extraction settings saved')).toBeInTheDocument()
  })

  it('turning on paid fallback asks for the paid confirmation the server requires', async () => {
    const user = userEvent.setup()
    const api = mockLlmApi()
    api.failNext = {
      status: 409,
      unlessConfirmed: true,
      body: {
        code: 'PAID_CONFIRMATION_REQUIRED',
        detail: 'confirm',
        modelLabel: 'Claude Haiku 5.5',
        tier: 'PAID',
        estimatedCostPerCallUsd: 0.0012,
      },
    }
    renderCard()

    await user.click(
      within(await screen.findByRole('region', { name: 'Extraction' })).getByRole('switch', {
        name: /Allow paid fallback/,
      }),
    )

    const dialog = await screen.findByRole('dialog', { name: 'Allow paid fallback for Extraction?' })
    expect(dialog).toHaveTextContent('Claude Haiku 5.5 is a paid model.')
    expect(dialog).toHaveTextContent('Each call costs about $0.0012')
    await user.click(within(dialog).getByRole('button', { name: 'Confirm paid calls' }))

    await waitFor(() => expect(api.puts).toHaveLength(2))
    expect(api.puts[1].body).toEqual({ modelId: 'groq-gpt-oss-120b', allowPaid: true, confirmPaid: true })
  })

  it('with paid calls off, paid fallback and Test of a PAID model are disabled', async () => {
    mockLlmApi(llmAdminFixture({ paidCallsAllowed: false }))
    renderCard()

    const extraction = await screen.findByRole('region', { name: 'Extraction' })
    expect(within(extraction).getByRole('switch', { name: /Allow paid fallback/ })).toBeDisabled()
    expect(within(extraction).getByText('Paid calls are off (LLM_ALLOW_PAID_CALLS=false)')).toBeInTheDocument()
    expect(within(row('Claude Haiku 5.5')).getByRole('button', { name: 'Test' })).toBeDisabled()
  })

  it('Test calls the model once and reports the result', async () => {
    const user = userEvent.setup()
    const api = mockLlmApi()
    renderCard()
    await screen.findByRole('region', { name: 'Extraction' })

    await user.click(within(row('Qwen 3.8 27B (Groq)')).getByRole('button', { name: 'Test' }))

    expect(await screen.findByText('Qwen 3.8 27B (Groq) works · 412 ms')).toBeInTheDocument()
    expect(api.tests).toEqual([{ modelId: 'groq-qwen3-27b', body: null }])
  })

  it('Test of a PAID model runs only after the confirmation', async () => {
    const user = userEvent.setup()
    const api = mockLlmApi()
    api.failNext = {
      status: 409,
      unlessConfirmed: true,
      body: { code: 'PAID_CONFIRMATION_REQUIRED', modelLabel: 'Claude Haiku 5.5', estimatedCostPerCallUsd: null },
    }
    renderCard()
    await screen.findByRole('region', { name: 'Extraction' })

    await user.click(within(row('Claude Haiku 5.5')).getByRole('button', { name: 'Test' }))
    const dialog = await screen.findByRole('dialog', { name: 'Test Claude Haiku 5.5?' })
    expect(dialog).toHaveTextContent('an unknown amount (no cost history yet)')
    await user.click(within(dialog).getByRole('button', { name: 'Confirm paid calls' }))

    expect(await screen.findByText('Claude Haiku 5.5 works · 412 ms')).toBeInTheDocument()
    expect(api.tests).toEqual([
      { modelId: 'claude-haiku', body: null },
      { modelId: 'claude-haiku', body: { confirmPaid: true } },
    ])
  })
})
