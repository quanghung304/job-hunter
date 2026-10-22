import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ProfileData } from '@/api/queries'
import { profileFixture } from '@/test/profileFixtures'
import { ProfileForm } from './ProfileForm'
import { clientProblems, fieldKey, useProfileForm } from './useProfileForm'

function Harness({ loaded = profileFixture() }: { loaded?: ProfileData }) {
  const form = useProfileForm(loaded)
  return (
    <>
      <p data-testid="state">
        {form.dirty ? 'dirty' : 'clean'} {form.valid ? 'valid' : 'invalid'}
      </p>
      <button type="button" onClick={form.discard}>
        Discard
      </button>
      <button type="button" onClick={() => form.setServerProblems([{ path: 'candidate.city', message: 'too far' }])}>
        Server says
      </button>
      <pre data-testid="draft">{JSON.stringify(form.draft)}</pre>
      <ProfileForm form={form} />
    </>
  )
}

const state = () => screen.getByTestId('state').textContent
const draft = () => JSON.parse(screen.getByTestId('draft').textContent ?? '{}') as ProfileData

describe('ProfileForm', () => {
  it('shows every section with the YAML path of each field', () => {
    render(<Harness />)

    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'Candidate',
      'Skills and domains',
      'Preferences',
      'Prefilter',
      'Notifications',
    ])
    expect(screen.getByLabelText(/Headline/)).toHaveValue('Backend developer, Java and Spring Boot')
    expect(screen.getByText('candidate.headline')).toHaveClass('num')
    expect(screen.getByRole('button', { name: 'Contract' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Full-time' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByText('vi · NATIVE')).toBeInTheDocument()
    expect(state()).toBe('clean valid')
  })

  it('an edit makes the draft dirty; Discard restores the loaded profile', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByLabelText(/Max hours per week/), '30')
    await user.click(screen.getByRole('button', { name: 'Full-time' }))
    await user.type(screen.getByLabelText(/Core skills/), 'Kafka{Enter}')

    expect(state()).toBe('dirty valid')
    expect(draft().preferences.maxHoursPerWeek).toBe(30)
    expect(draft().preferences.employmentTypes).toEqual(['FULL_TIME', 'PART_TIME', 'CONTRACT', 'FREELANCE'])
    expect(draft().candidate.skills.core).toEqual(['Java', 'Spring Boot', 'Kafka'])

    await user.click(screen.getByRole('button', { name: 'Discard' }))

    expect(state()).toBe('clean valid')
    expect(screen.getByLabelText(/Max hours per week/)).toHaveValue(null)
    expect(screen.getByRole('button', { name: 'Full-time' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('client checks mark required fields and ranges next to the field', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.clear(screen.getByLabelText(/Headline/))
    await user.clear(screen.getByLabelText(/Digest minimum score/))
    await user.type(screen.getByLabelText(/Digest minimum score/), '120')
    await user.click(screen.getByRole('button', { name: 'Junior' }))
    await user.click(screen.getByRole('button', { name: 'Mid' }))
    await user.click(screen.getByRole('button', { name: 'Senior' }))

    expect(state()).toBe('dirty invalid')
    const headline = screen.getByLabelText(/Headline/)
    expect(headline).toHaveAttribute('aria-invalid', 'true')
    expect(headline).toHaveAccessibleDescription('is required')
    expect(screen.getByLabelText(/Digest minimum score/)).toHaveAccessibleDescription('must be between 0 and 100')
    expect(screen.getByText('pick at least one')).toBeInTheDocument()
  })

  it('a server problem shows at its field until that field is edited', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Server says' }))
    expect(screen.getByText('too far')).toBeInTheDocument()

    await user.type(screen.getByLabelText(/^City/), 'x')

    expect(screen.queryByText('too far')).not.toBeInTheDocument()
  })

  it('languages are added with a level and removed by their chip', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByLabelText(/^Languages/), 'IT')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Level' }), 'B1')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await user.click(
      within(screen.getByRole('list', { name: 'Your languages' })).getByRole('button', { name: 'Remove vi' }),
    )

    expect(draft().candidate.languages).toEqual([
      { code: 'en', level: 'C1' },
      { code: 'it', level: 'B1' },
    ])
  })
})

describe('clientProblems', () => {
  it('the example profile has none', () => {
    expect(clientProblems(profileFixture())).toEqual([])
  })

  it('lists required fields, ranges and region tokens by path', () => {
    const p = profileFixture()
    p.candidate.residenceCountry = 'Italy'
    p.candidate.yearsExperience = Number.NaN
    p.preferences.acceptRegions = ['EU', 'Mars']
    p.preferences.minSalary = { amount: Number.NaN, currency: 'eu', period: 'YEAR' }
    p.prefilter.titleInclude = []

    expect(clientProblems(p).map((problem) => problem.path)).toEqual([
      'candidate.yearsExperience',
      'candidate.residenceCountry',
      'preferences.acceptRegions[1]',
      'preferences.minSalary.amount',
      'preferences.minSalary.currency',
      'prefilter.titleInclude',
    ])
  })

  it('field keys drop list indexes', () => {
    expect(fieldKey('preferences.acceptRegions[1]')).toBe('preferences.acceptRegions')
    expect(fieldKey('candidate.languages[0].code')).toBe('candidate.languages.code')
  })
})
