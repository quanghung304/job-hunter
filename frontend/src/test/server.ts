import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** MSW server for Vitest (Node); started in setup.ts. */
export const server = setupServer(...handlers)
