import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// Pages load several queries; the 1 s default is too short when the whole suite runs in parallel.
configure({ asyncUtilTimeout: 5000 })

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

// jsdom lacks the pointer-capture and scroll APIs Radix primitives call.
for (const name of ['setPointerCapture', 'releasePointerCapture'] as const) {
  if (!Element.prototype[name]) Element.prototype[name] = () => {}
}
if (!Element.prototype.hasPointerCapture) Element.prototype.hasPointerCapture = () => false
if (!Element.prototype.scrollIntoView) Element.prototype.scrollIntoView = () => {}
