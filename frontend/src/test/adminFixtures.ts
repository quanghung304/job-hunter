import { http, HttpResponse } from 'msw'
import type { AdminOverview, Company, JobSummary, ModelChoice } from '@/api/queries'
import { server } from './server'

const DAY = 86_400_000

/** 14 local days, oldest first; costs only when `costs` is given. */
export function dailyUsage(calls: number[], costs?: number[]): AdminOverview['llmUsage']['daily'] {
  const today = Date.parse('2026-10-19T00:00:00Z')
  return calls.map((count, i) => ({
    date: new Date(today - (calls.length - 1 - i) * DAY).toISOString().slice(0, 10),
    calls: count,
    inputTokens: count * 2000,
    outputTokens: count * 300,
    costUsd: costs?.[i] ?? 0,
  }))
}

export const FREE_MODEL: ModelChoice = {
  modelId: 'groq-gpt-oss-120b',
  label: 'GPT-OSS 120B (Groq)',
  tier: 'FREE',
  paidCallsAllowed: false,
}

export const PAID_MODEL: ModelChoice = {
  modelId: 'anthropic-sonnet',
  label: 'Claude Sonnet (Anthropic)',
  tier: 'PAID',
  paidCallsAllowed: true,
}

export function overviewFixture(overrides: Partial<AdminOverview> = {}): AdminOverview {
  return {
    sources: [
      {
        code: 'himalayas',
        name: 'Himalayas',
        enabled: true,
        lastRun: {
          status: 'SUCCESS',
          startedAt: '2026-10-19T05:00:00Z',
          fetched: 120,
          newRaw: 30,
          newJobs: 12,
          error: null,
        },
        lastSuccessAt: '2026-10-19T05:00:00Z',
      },
      {
        code: 'jobicy',
        name: 'Jobicy',
        enabled: true,
        lastRun: {
          status: 'FAILED',
          startedAt: '2026-10-19T05:00:00Z',
          fetched: 0,
          newRaw: 0,
          newJobs: 0,
          error: 'HTTP 503 Service Unavailable from https://jobicy.com/api/v2/remote-jobs after 3 attempts',
        },
        lastSuccessAt: '2026-10-18T05:00:00Z',
      },
      { code: 'arbeitnow', name: 'Arbeitnow', enabled: false, lastRun: null, lastSuccessAt: null },
    ],
    pipelineReports: [
      {
        runNumber: 7,
        trigger: 'SCHEDULED',
        status: 'PARTIAL',
        startedAt: '2026-10-19T05:00:00Z',
        finishedAt: '2026-10-19T05:01:05Z',
        durationMs: 65_000,
        sources: [],
        newJobs: 12,
        reassessed: 0,
        prefilterEvaluated: 30,
        prefilterPassed: 18,
        prefilterRejected: 12,
        notAnalyzed: 40,
        expired: 2,
        llmCalls: 0,
        error: null,
      },
    ],
    llmUsage: {
      monthToDateUsd: 1.25,
      monthlyBudgetUsd: 5,
      callsToday: 14,
      daily: dailyUsage([3, 0, 5, 8, 2, 0, 0, 4, 6, 9, 1, 0, 7, 14]),
    },
    failures: {
      extractions: [{ jobId: 41, title: 'Kotlin Engineer', attempts: 2, lastError: 'INVALID_OUTPUT: not JSON' }],
      scorings: [],
    },
    pipelineRunning: false,
    pipelineProgress: null,
    schedule: { nextRunAt: '2026-10-19T17:00:00Z', nextDigestAt: '2026-10-20T06:30:00Z', zone: 'Europe/Rome' },
    kpis: {
      activeJobs: 1384,
      newToday: 42,
      prefilterRejectedToday: 31,
      scoredToday: 9,
      toReview: 37,
      sourceCount: 9,
      companyBoardCount: 6,
    },
    llmAlerts: [],
    ...overrides,
  }
}

export const companiesFixture: Company[] = [
  {
    id: 1,
    name: 'Acme',
    ats: 'GREENHOUSE',
    slug: 'acme',
    enabled: true,
    lastCheckStatus: 'OK',
    lastCheckedAt: '2026-10-19T05:00:00Z',
    activeJobs: 4,
  },
  {
    id: 2,
    name: 'Globex',
    ats: 'LEVER',
    slug: 'globex',
    enabled: true,
    lastCheckStatus: 'NOT_FOUND',
    lastCheckedAt: null,
    activeJobs: 0,
  },
]

export interface AdminApi {
  requests: string[]
  /** Overview responses served in order; the last one repeats. */
  overviews: AdminOverview[]
  /** Answers of POST /api/admin/pipeline/run: 'accept' or 'conflict'. */
  run: 'accept' | 'conflict'
  rescoreModel: ModelChoice
  bulkCandidates: JobSummary[]
}

/** MSW handlers for /api/admin/** and the bulk candidates of GET /api/jobs. */
export function mockAdminApi(overviews: AdminOverview[] = [overviewFixture()]): AdminApi {
  const api: AdminApi = { requests: [], overviews, run: 'accept', rescoreModel: FREE_MODEL, bulkCandidates: [] }
  let served = 0
  server.use(
    http.get('/api/admin/overview', () => {
      api.requests.push('GET /api/admin/overview')
      const overview = api.overviews[Math.min(served, api.overviews.length - 1)]
      served++
      return HttpResponse.json(overview)
    }),
    http.get('/api/admin/companies', () => HttpResponse.json(companiesFixture)),
    http.post('/api/admin/pipeline/run', () => {
      api.requests.push('POST /api/admin/pipeline/run')
      if (api.run === 'conflict') {
        return HttpResponse.json(
          { status: 409, code: 'PIPELINE_ALREADY_RUNNING', detail: 'A pipeline run is in progress' },
          { status: 409 },
        )
      }
      return HttpResponse.json({ accepted: true }, { status: 202 })
    }),
    http.post('/api/admin/rescore', async ({ request }) => {
      const body = (await request.json()) as { dryRun: boolean }
      api.requests.push(`POST /api/admin/rescore dryRun=${body.dryRun}`)
      if (!body.dryRun) return HttpResponse.json({ accepted: true }, { status: 202 })
      return HttpResponse.json({
        jobCount: 57,
        estimatedCalls: 57,
        estimatedCostUsd: api.rescoreModel.tier === 'PAID' ? 0.09 : 0,
        model: api.rescoreModel,
      })
    }),
    http.post('/api/admin/jobs/:action', async ({ params, request }) => {
      const body = (await request.json()) as { jobIds: number[]; dryRun: boolean }
      const action = String(params.action)
      api.requests.push(`POST /api/admin/jobs/${action} ${body.jobIds.length} dryRun=${body.dryRun}`)
      if (!body.dryRun) return HttpResponse.json({ accepted: true }, { status: 202 })
      const n = body.jobIds.length
      return HttpResponse.json(
        action === 'analyze'
          ? {
              selected: n,
              willAnalyze: n - 1,
              skipped: { alreadyAnalyzed: 1, expired: 0, prefilterRejected: 0, failedPermanently: 0, notFound: 0 },
              estimatedCalls: n - 1,
              estimatedCostUsd: 0,
              model: FREE_MODEL,
            }
          : {
              selected: n,
              willScore: n,
              skipped: {
                notAnalyzed: 0,
                markedIneligible: 0,
                alreadyScored: 0,
                expired: 0,
                failedPermanently: 0,
                notFound: 0,
              },
              estimatedCalls: n,
              estimatedCostUsd: 0,
              model: FREE_MODEL,
            },
      )
    }),
    http.post('/api/admin/jobs/:id/retry', async ({ params, request }) => {
      const body = (await request.json()) as { purpose: string }
      api.requests.push(`POST /api/admin/jobs/${String(params.id)}/retry ${body.purpose}`)
      return HttpResponse.json({ accepted: true }, { status: 202 })
    }),
    http.post('/api/admin/eval/export', () =>
      HttpResponse.json({ status: 501, code: 'NOT_IMPLEMENTED', detail: 'Comes with T-016' }, { status: 501 }),
    ),
    http.get('/api/jobs', ({ request }) => {
      const url = new URL(request.url)
      const page = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 12)
      const stages = url.searchParams.getAll('analysisStage')
      const items = api.bulkCandidates.filter((job) => stages.length === 0 || stages.includes(job.analysisStage))
      return HttpResponse.json({
        items: items.slice(page * size, (page + 1) * size),
        page,
        size,
        totalItems: items.length,
        totalPages: Math.ceil(items.length / size),
      })
    }),
  )
  return api
}
