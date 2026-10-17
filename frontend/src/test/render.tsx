import type { ReactElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'

/** A fresh client per test: no retries, no shared cache. */
export function testQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
}

export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const client = testQueryClient()
  return {
    client,
    ...render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </QueryClientProvider>,
    ),
  }
}
