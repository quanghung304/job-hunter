import { http, HttpResponse } from 'msw'
import type { JobDetail, JobSummary } from '@/api/queries'
import type { TrackingStatus } from '@/lib/labels'
import { allowedTargets } from '@/lib/statusMachine'
import { server } from './server'

const NOW = Date.now()
const hoursAgo = (hours: number) => new Date(NOW - hours * 3_600_000).toISOString()

export function summary(id: number, overrides: Partial<JobSummary> = {}): JobSummary {
  return {
    id,
    title: `Java Developer ${id}`,
    company: `Acme ${id}`,
    locationText: 'Remote (EU)',
    score: 80,
    scoreBand: 'GOOD',
    summary: `Good match ${id}`,
    eligibilityDecision: null,
    checks: { ok: 10, unknown: 0, fail: 0 },
    remoteType: 'REMOTE_RESTRICTED',
    allowedRegions: ['EU'],
    employmentTypes: ['CONTRACT'],
    postedAt: hoursAgo(id * 5),
    firstSeenAt: hoursAgo(id * 5),
    sources: [{ code: 'himalayas', name: 'Himalayas' }],
    status: 'NEW',
    jobStatus: 'ACTIVE',
    analysisStage: 'SCORED',
    ...overrides,
  }
}

const CHECK_CODES = [
  'WORK_MODE',
  'REGION',
  'WORK_AUTH',
  'TIMEZONE',
  'EMPLOYMENT_TYPE',
  'HOURS',
  'SENIORITY',
  'LANGUAGE',
  'SCAM',
  'EXTRACTION_CONFIDENCE',
] as const

/** Ten checks: TIMEZONE is UNKNOWN when `unknown` > 0, REGION fails when `fail` > 0. */
function checksFor(counts: { unknown: number; fail: number }): JobDetail['eligibilityChecks'] {
  return CHECK_CODES.map((code) => ({
    code,
    critical: code !== 'TIMEZONE' && code !== 'HOURS' && code !== 'SENIORITY',
    result:
      code === 'TIMEZONE' && counts.unknown > 0 ? 'UNKNOWN' : code === 'REGION' && counts.fail > 0 ? 'FAIL' : 'OK',
    value: code === 'TIMEZONE' && counts.unknown > 0 ? 'Not stated' : 'EU',
    reason: null,
  }))
}

export function detail(job: JobSummary, overrides: Partial<JobDetail> = {}): JobDetail {
  const scored = job.score !== null
  return {
    ...job,
    description: `Build things at ${job.company}.\nJava, Spring Boot and SQL.`,
    applyUrl: `https://apply.example/${job.id}`,
    salary: { min: 60000, max: 80000, currency: 'EUR', period: 'YEAR' },
    tags: ['java'],
    extraction:
      job.analysisStage === 'NOT_ANALYZED'
        ? null
        : {
            remoteType: 'REMOTE_RESTRICTED',
            allowedRegions: ['EU'],
            employmentTypes: ['CONTRACT'],
            requiredSkills: ['Java', 'Spring'],
            roleSummary: 'Backend role.',
            isAgencyOrReposting: false,
            isScamSignal: false,
            confidence: 'HIGH',
          },
    eligibilityChecks: job.checks === null ? [] : checksFor(job.checks),
    eligibilityDecidedAt: null,
    prefilterReasons: [],
    scoreBreakdown: scored ? { skills: 32, experience: 16, arrangement: 18, domain: 8, other: 6 } : null,
    strengths: scored ? ['Java 17 and Spring'] : [],
    gaps: scored ? ['Kafka'] : [],
    redFlags: [],
    applyAdvice: scored ? 'Nhấn mạnh kinh nghiệm Spring.' : null,
    postings: [
      {
        sourceCode: 'himalayas',
        sourceName: 'Himalayas',
        url: `https://himalayas.app/jobs/${job.id}`,
        firstSeenAt: job.firstSeenAt,
        lastSeenAt: job.firstSeenAt,
      },
    ],
    notes: null,
    history: [],
    allowedTransitions: [...allowedTargets(job.status)],
    ...overrides,
  }
}

export interface JobsApi {
  jobs: JobSummary[]
  /** Requests seen, e.g. "GET /api/jobs?status=NEW", "POST /api/jobs/1/analyze". */
  requests: string[]
  /** Answer the next status change with this Problem Details (status, code, detail). */
  failStatusChange?: { status: number; code: string; detail: string }
  notes: Record<number, string>
}

function visible(job: JobSummary, url: URL): boolean {
  const statuses = url.searchParams.getAll('status')
  const filtered = [...url.searchParams.keys()].some((key) => !['sort', 'page', 'size'].includes(key))
  if (!filtered) return job.status === 'NEW' || job.status === 'SHORTLISTED'
  return statuses.length === 0 || statuses.includes(job.status)
}

/** In-memory jobs behind MSW handlers for /api/jobs/**; returns the state to inspect. */
export function mockJobsApi(jobs: JobSummary[], details: Record<number, Partial<JobDetail>> = {}): JobsApi {
  const api: JobsApi = { jobs: jobs.map((job) => ({ ...job })), requests: [], notes: {} }
  const find = (id: number) => api.jobs.find((job) => job.id === id)
  const detailOf = (job: JobSummary): JobDetail =>
    detail(job, {
      ...details[job.id],
      notes: api.notes[job.id] ?? details[job.id]?.notes ?? null,
    })

  server.use(
    http.get('/api/jobs', ({ request }) => {
      const url = new URL(request.url)
      api.requests.push(`GET /api/jobs?${url.searchParams.toString()}`)
      const page = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 25)
      const items = api.jobs.filter((job) => visible(job, url))
      return HttpResponse.json({
        items: items.slice(page * size, (page + 1) * size),
        page,
        size,
        totalItems: items.length,
        totalPages: Math.ceil(items.length / size),
      })
    }),
    http.get('/api/jobs/:id', ({ params }) => {
      const job = find(Number(params.id))
      api.requests.push(`GET /api/jobs/${String(params.id)}`)
      if (!job) {
        return HttpResponse.json(
          { status: 404, code: 'JOB_NOT_FOUND', detail: `Job ${String(params.id)} not found` },
          { status: 404 },
        )
      }
      return HttpResponse.json(detailOf(job))
    }),
    http.post('/api/jobs/:id/status', async ({ params, request }) => {
      const body = (await request.json()) as { to: TrackingStatus }
      api.requests.push(`POST /api/jobs/${String(params.id)}/status ${body.to}`)
      const job = find(Number(params.id))
      if (api.failStatusChange) {
        const { status, code, detail: text } = api.failStatusChange
        api.failStatusChange = undefined
        return HttpResponse.json({ status, code, detail: text }, { status })
      }
      if (!job) return HttpResponse.json({ status: 404, code: 'JOB_NOT_FOUND' }, { status: 404 })
      job.status = body.to
      return HttpResponse.json(job)
    }),
    http.put('/api/jobs/:id/notes', async ({ params, request }) => {
      const body = (await request.json()) as { notes: string }
      api.requests.push(`PUT /api/jobs/${String(params.id)}/notes`)
      api.notes[Number(params.id)] = body.notes
      return new HttpResponse(null, { status: 204 })
    }),
    http.post('/api/jobs/:id/:action', ({ params }) => {
      const action = String(params.action)
      api.requests.push(`POST /api/jobs/${String(params.id)}/${action}`)
      const job = find(Number(params.id))
      if (!job) return HttpResponse.json({ status: 404, code: 'JOB_NOT_FOUND' }, { status: 404 })
      if (action === 'analyze') {
        Object.assign(job, { analysisStage: 'ANALYZED', checks: { ok: 10, unknown: 0, fail: 0 } })
      } else if (action === 'score') {
        Object.assign(job, { analysisStage: 'SCORED', score: 77, scoreBand: 'GOOD', summary: 'Scored now' })
      }
      return HttpResponse.json(detailOf(job))
    }),
  )
  return api
}

/** Requests that would make an LLM call (ADR-0010: only on an owner action). */
export function llmRequests(api: JobsApi): string[] {
  return api.requests.filter((request) => /\/(analyze|score)$/.test(request))
}
