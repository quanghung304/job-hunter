import { http, HttpResponse } from 'msw'
import type { components } from '@/api/schema'

type Meta = components['schemas']['MetaResponse']
type Status = components['schemas']['StatusResponse']

/** A realistic /api/meta response (shape from the generated types). */
export const metaFixture: Meta = {
  profileId: 'hugh',
  summaryLanguage: 'vi',
  sources: [
    { code: 'arbeitnow', name: 'Arbeitnow' },
    { code: 'himalayas', name: 'Himalayas' },
  ],
  statuses: ['NEW', 'SHORTLISTED', 'DISMISSED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN'],
  employmentTypes: ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'FREELANCE', 'INTERNSHIP'],
  eligibilities: ['ELIGIBLE', 'UNCERTAIN', 'INELIGIBLE'],
}

export const statusFixture: Status = {
  lastRun: { startedAt: '2026-10-17T05:00:00Z', newJobs: 42, scored: 9, status: 'SUCCESS' },
  pipelineRunning: false,
  llmAlerts: [],
}

const emptyPage = { items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 }

/** Default handlers for every test; override per test with `server.use(...)`. */
export const handlers = [
  http.get('/api/meta', () => HttpResponse.json(metaFixture)),
  http.get('/api/status', () => HttpResponse.json(statusFixture)),
  http.get('/api/jobs', () => HttpResponse.json(emptyPage)),
  http.get('/api/jobs/:id', ({ params }) =>
    HttpResponse.json(
      { status: 404, code: 'JOB_NOT_FOUND', detail: `Job ${String(params.id)} not found` },
      { status: 404 },
    ),
  ),
]
