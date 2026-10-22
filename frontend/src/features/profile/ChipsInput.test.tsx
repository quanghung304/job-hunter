import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { ChipsInput } from './ChipsInput'

function Harness({ initial = [], normalize }: { initial?: string[]; normalize?: (value: string) => string }) {
  const [values, setValues] = useState(initial)
  return (
    <>
      <label htmlFor="chips">Skills</label>
      <ChipsInput id="chips" values={values} onChange={setValues} normalize={normalize} />
      <output data-testid="values">{values.join('|')}</output>
    </>
  )
}

const values = () => screen.getByTestId('values').textContent

describe('ChipsInput', () => {
  it('Enter adds the typed value; empty and duplicate values are ignored', async () => {
    const user = userEvent.setup()
    render(<Harness initial={['Java']} />)
    const input = screen.getByLabelText('Skills')

    await user.type(input, 'Kotlin{Enter}')
    await user.type(input, '   {Enter}')
    await user.type(input, 'Java{Enter}')

    expect(values()).toBe('Java|Kotlin')
    expect(input).toHaveValue('')
  })

  it('Backspace on an empty input removes the last chip, not while typing', async () => {
    const user = userEvent.setup()
    render(<Harness initial={['Java', 'Kotlin']} />)
    const input = screen.getByLabelText('Skills')

    await user.type(input, 'Go{Backspace}')
    expect(values()).toBe('Java|Kotlin')

    await user.clear(input)
    await user.type(input, '{Backspace}')
    expect(values()).toBe('Java')
  })

  it('each chip has a remove button named after it', async () => {
    const user = userEvent.setup()
    render(<Harness initial={['Java', 'Kotlin', 'SQL']} />)

    await user.click(screen.getByRole('button', { name: 'Remove Kotlin' }))

    expect(values()).toBe('Java|SQL')
  })

  it('leaving the field keeps what was typed, normalized', async () => {
    const user = userEvent.setup()
    render(<Harness normalize={(value) => value.toUpperCase()} />)

    await user.type(screen.getByLabelText('Skills'), 'eu')
    await user.tab()

    expect(values()).toBe('EU')
  })
})
