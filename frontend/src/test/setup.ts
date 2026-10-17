import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// openapi-fetch builds absolute URLs from the page origin.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
})
afterAll(() => server.close())

// jsdom has no matchMedia; default to the desktop layout (≥ 1024px).
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: query.includes('min-width: 1024px'),
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}
