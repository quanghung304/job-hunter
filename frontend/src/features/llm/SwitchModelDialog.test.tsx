import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { llmAdminFixture, mockLlmApi } from '@/test/llmFixtures'
import { renderWithProviders } from '@/test/render'
import { SwitchModelDialog } from './SwitchModelDialog'

function renderDialog(purpose: 'EXTRACT' | 'SCORE' = 'EXTRACT') {
  return renderWithProviders(
    <>
      <SwitchModelDialog purpose={purpose} open onOpenChange={() => {}} />
      <Toaster />
    </>,
  )
}

const radio = (name: RegExp) => screen.getByRole('radio', { name })

describe('SwitchModelDialog', () => {
  it('lists models with tier, status and usage; unusable ones are disabled with the reason', async () => {
    mockLlmApi()
    renderDialog()

    const dialog = await screen.findByRole('dialog', { name: 'Model for Extraction' })
    await within(dialog).findByRole('radio', { name: /Qwen 3.8 27B/ })
    expect(radio(/GPT-OSS 120B/)).toBeChecked()
    expect(within(dialog).getByText('current')).toBeInTheDocument()
    expect(radio(/Qwen 3.8 27B/)).toBeEnabled()
    expect(radio(/Qwen 3.8 27B/)).toHaveAccessibleDescription(/120 \/ 1000 today/)
    expect(radio(/Nemotron/)).toBeDisabled()
    expect(radio(/Nemotron/)).toHaveAccessibleDescription(/No key: add the openrouter API key to \.env/)
    expect(radio(/GPT-OSS 20B/)).toBeDisabled()
    expect(radio(/GPT-OSS 20B/)).toHaveAccessibleDescription(/Error: HTTP 404 model/)
    expect(within(dialog).queryByRole('radio', { name: /Fake/ })).not.toBeInTheDocument()
    expect(within(dialog).getAllByText('PAID')).toHaveLength(1)
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled()
  })

  it('a PAID model shows the cost warning and needs the confirmation before Save', async () => {
    const user = userEvent.setup()
    const api = mockLlmApi()
    renderDialog('SCORE')
    const dialog = await screen.findByRole('dialog', { name: 'Model for Scoring' })

    await user.click(await within(dialog).findByRole('radio', { name: /Claude Haiku/ }))

    expect(within(dialog).getByText(/Paid model — every Score costs money/)).toBeInTheDocument()
    const save = within(dialog).getByRole('button', { name: 'Save' })
    expect(save).toBeDisabled()
    await user.click(within(dialog).getByRole('checkbox', { name: 'I confirm paid calls for Scoring' }))
    await user.click(save)

    expect(await screen.findByText('Scoring now uses Claude Haiku 5.5')).toBeInTheDocument()
    expect(api.puts).toEqual([{ purpose: 'SCORE', body: { modelId: 'claude-haiku', confirmPaid: true } }])
  })

  it('with paid calls off a PAID model cannot be selected and the dialog says how to enable them', async () => {
    mockLlmApi(llmAdminFixture({ paidCallsAllowed: false }))
    renderDialog()
    await screen.findByRole('dialog')

    const claude = await screen.findByRole('radio', { name: /Claude Haiku/ })
    expect(claude).toBeDisabled()
    expect(claude).toHaveAccessibleDescription(/set LLM_ALLOW_PAID_CALLS=true in \.env and restart/)
  })

  it('a refused switch (409) shows the server message and keeps the dialog open', async () => {
    const user = userEvent.setup()
    const api = mockLlmApi()
    api.failNext = {
      status: 409,
      body: { code: 'MODEL_UNUSABLE', detail: 'The Qwen 3.8 27B (Groq) model cannot be used: HTTP 401' },
    }
    renderDialog()
    const dialog = await screen.findByRole('dialog')

    await user.click(await within(dialog).findByRole('radio', { name: /Qwen 3.8 27B/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(
      await within(dialog).findByText('The Qwen 3.8 27B (Groq) model cannot be used: HTTP 401'),
    ).toBeInTheDocument()
    expect(within(dialog).getByText('409 · MODEL_UNUSABLE')).toBeInTheDocument()
  })
})
