import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { AppShell } from '@/App'
import { server } from '@/test/server'
import { renderWithProviders } from '@/test/render'

describe('App routes', () => {
  it.each([
    ['/', 'The job board arrives with T-020.'],
    ['/jobs/1', 'The job detail arrives with T-020.'],
    ['/admin', 'Pipeline, sources and LLM usage arrive with T-021.'],
  ])('renders %s', async (route, text) => {
    renderWithProviders(<AppShell />, { route })

    expect(await screen.findByText(text)).toBeInTheDocument()
  })

  it('renders the 404 page for an unknown path', async () => {
    renderWithProviders(<AppShell />, { route: '/nope' })

    expect(await screen.findByText('Page not found', { selector: 'p' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the board' })).toHaveAttribute('href', '/')
  })

  it('shows the profile id from /api/meta in the header', async () => {
    renderWithProviders(<AppShell />)

    expect(await screen.findByText('hugh')).toBeInTheDocument()
  })

  it('marks the active nav link', async () => {
    renderWithProviders(<AppShell />, { route: '/admin' })

    expect(await screen.findByRole('link', { name: 'Admin' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Board' })).not.toHaveAttribute('aria-current')
  })

  it('shows "offline" in the profile pill when the backend is down', async () => {
    server.use(http.get('/api/meta', () => HttpResponse.error()))

    renderWithProviders(<AppShell />)

    expect(await screen.findByText('offline')).toBeInTheDocument()
  })
})
