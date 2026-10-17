import { http, HttpResponse } from 'msw'
import type { components } from '@/api/schema'

type Meta = components['schemas']['MetaResponse']

/** A realistic /api/meta response (shape from the generated types). */
export const metaFixture: Meta = {
  profileId: 'hugh',
  summaryLanguage: 'vi',
  sources: [
    { code: 'arbeitnow', name: 'Arbeitnow' },
    { code: 'himalayas', name: 'Himalayas' },
  ],
  statuses: [],
  employmentTypes: [],
  eligibilities: [],
}

/** Default handlers for every test; override per test with `server.use(...)`. */
export const handlers = [http.get('/api/meta', () => HttpResponse.json(metaFixture))]
