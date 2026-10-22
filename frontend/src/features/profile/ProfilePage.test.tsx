import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, Link, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { Toaster } from '@/components/ui/sonner'
import { HASH_1, HASH_2, mockProfileApi } from '@/test/profileFixtures'
import { testQueryClient } from '@/test/render'
import { ProfilePage } from './ProfilePage'

/** The page in a data router (needed by the leave guard) with a link away to the board. */
function renderProfile() {
  const router = createMemoryRouter(
    [
      {
        path: '/profile',
        element: (
          <>
            <Link to="/">Board</Link>
            <ProfilePage />
            <Toaster />
          </>
        ),
      },
      { path: '/', element: <p>Board page</p> },
    ],
    { initialEntries: ['/profile'] },
  )
  render(
    <QueryClientProvider client={testQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

const save = () => screen.getByRole('button', { name: 'Save changes' })

describe('ProfilePage', () => {
  it('loads the profile: header with id, hash and file, nothing to save yet', async () => {
    mockProfileApi()
    renderProfile()

    expect(await screen.findByRole('heading', { name: /Your profile/, level: 1 })).toHaveTextContent('hugh')
    expect(screen.getByText(/hash 3f9c1a · config\/profile\.yaml/)).toBeInTheDocument()
    expect(screen.getByText('No changes')).toBeInTheDocument()
    expect(save()).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Discard' })).toBeDisabled()
    expect(screen.getByText(/Saving re-runs the prefilter/)).toBeInTheDocument()
    expect(document.querySelectorAll('[data-variant="primary"]')).toHaveLength(1)
  })

  it('Save sends one PUT with the loaded hash, shows the counts and the new hash', async () => {
    const user = userEvent.setup()
    const api = mockProfileApi()
    renderProfile()
    await user.type(await screen.findByLabelText(/Max hours per week/), '30')
    expect(screen.getByText('Unsaved changes')).toBeInTheDocument()

    await user.click(save())

    expect(await screen.findByText('Profile saved · eligibility re-checked for 30 jobs')).toBeInTheDocument()
    expect(api.puts).toHaveLength(1)
    expect(api.puts[0].baseHash).toBe(HASH_1)
    expect(api.puts[0].profile.preferences.maxHoursPerWeek).toBe(30)
    expect(api.puts[0].profile.candidate.headline).toBe('Backend developer, Java and Spring Boot')
    expect(await screen.findByText(new RegExp(`hash ${HASH_2.slice(0, 6)}`))).toBeInTheDocument()
    expect(screen.getByText('No changes')).toBeInTheDocument()
    expect(screen.getByLabelText(/Max hours per week/)).toHaveValue(30)
  })

  it('400 VALIDATION_FAILED shows each problem next to its field and in the banner', async () => {
    const user = userEvent.setup()
    const api = mockProfileApi()
    api.failNext = {
      status: 400,
      body: {
        code: 'VALIDATION_FAILED',
        detail: 'The profile has 2 problems',
        errors: [
          { path: 'candidate.timezone', message: 'Invalid ID for region-based ZoneId' },
          { path: 'prefilter.titleInclude[0]', message: 'invalid regex: Unclosed group' },
        ],
      },
    }
    renderProfile()
    await user.type(await screen.findByLabelText(/Max hours per week/), '30')

    await user.click(save())

    const banner = await screen.findByRole('alert')
    expect(within(banner).getByText('Fix 2 fields to save')).toBeInTheDocument()
    expect(within(banner).getByText('prefilter.titleInclude[0]: invalid regex: Unclosed group')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Time zone/)).toHaveAccessibleDescription('Invalid ID for region-based ZoneId')
    expect(screen.getByText('invalid regex: Unclosed group', { selector: 'p' })).toBeInTheDocument()
    expect(screen.getByText('Unsaved changes')).toBeInTheDocument()
  })

  it('409 PROFILE_CHANGED shows the conflict with a Reload button', async () => {
    const user = userEvent.setup()
    const api = mockProfileApi()
    api.failNext = {
      status: 409,
      body: { code: 'PROFILE_CHANGED', detail: 'The profile was saved since you loaded it; reload the page' },
    }
    renderProfile()
    await user.type(await screen.findByLabelText(/Max hours per week/), '30')

    await user.click(save())

    expect(await screen.findByText('The profile changed since you loaded it')).toBeInTheDocument()
    expect(screen.getByText('409 · PROFILE_CHANGED')).toBeInTheDocument()
    api.current = { ...api.current, hash: HASH_2 }

    await user.click(screen.getByRole('button', { name: 'Reload' }))

    await waitFor(() => expect(screen.queryByText('The profile changed since you loaded it')).not.toBeInTheDocument())
    expect(await screen.findByText(new RegExp(`hash ${HASH_2.slice(0, 6)}`))).toBeInTheDocument()
    expect(screen.getByLabelText(/Max hours per week/)).toHaveValue(null)
  })

  it('leaving with unsaved changes asks first; Stay keeps the draft, Leave goes', async () => {
    const user = userEvent.setup()
    mockProfileApi()
    const router = renderProfile()
    await user.type(await screen.findByLabelText(/Max hours per week/), '30')

    await user.click(screen.getByRole('link', { name: 'Board' }))

    const dialog = await screen.findByRole('dialog', { name: 'Leave without saving?' })
    await user.click(within(dialog).getByRole('button', { name: 'Stay' }))
    expect(router.state.location.pathname).toBe('/profile')
    expect(screen.getByLabelText(/Max hours per week/)).toHaveValue(30)

    await user.click(screen.getByRole('link', { name: 'Board' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Leave' }))

    expect(await screen.findByText('Board page')).toBeInTheDocument()
  })

  it('leaving without changes does not ask', async () => {
    const user = userEvent.setup()
    mockProfileApi()
    renderProfile()
    await screen.findByText('No changes')

    await user.click(screen.getByRole('link', { name: 'Board' }))

    expect(await screen.findByText('Board page')).toBeInTheDocument()
  })
})
