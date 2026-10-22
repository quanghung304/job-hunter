import { http, HttpResponse } from 'msw'
import type { LlmAdmin, LlmAlert, LlmModel, Status } from '@/api/queries'
import { statusFixture } from './handlers'
import { server } from './server'

export function model(overrides: Partial<LlmModel> & Pick<LlmModel, 'modelId' | 'label'>): LlmModel {
  return {
    provider: 'groq',
    providerModel: `openai/${overrides.modelId}`,
    tier: 'FREE',
    status: 'AVAILABLE',
    until: null,
    detail: null,
    limits: { rpm: 30, rpd: 1000, tokensPerMinute: null, tokensPerDay: null, requestsPerMonth: null },
    usageToday: { calls: 120, inputTokens: 240_000, outputTokens: 31_000, costUsd: null },
    keyConfigured: true,
    assignedTo: [],
    lastEval: null,
    ...overrides,
  }
}

/** EXTRACT and SCORE on GPT-OSS 120B, which is EXHAUSTED; Qwen works; Nemotron has no key; Claude is PAID. */
export function llmAdminFixture(overrides: Partial<LlmAdmin> = {}): LlmAdmin {
  return {
    assignments: [
      {
        purpose: 'EXTRACT',
        modelId: 'groq-gpt-oss-120b',
        fallbacks: ['groq-qwen3-27b', 'claude-haiku'],
        autoFallback: false,
        allowPaid: false,
        updatedAt: '2026-10-22T06:00:00Z',
        updatedBy: 'config',
      },
      {
        purpose: 'SCORE',
        modelId: 'groq-gpt-oss-120b',
        fallbacks: ['groq-qwen3-27b'],
        autoFallback: true,
        allowPaid: false,
        updatedAt: '2026-10-22T06:00:00Z',
        updatedBy: 'config',
      },
    ],
    models: [
      model({
        modelId: 'groq-gpt-oss-120b',
        label: 'GPT-OSS 120B (Groq)',
        status: 'EXHAUSTED',
        until: '2026-10-23T00:00:00Z',
        detail: 'Daily request limit 1000/1000',
        usageToday: { calls: 1000, inputTokens: 2_400_000, outputTokens: 310_000, costUsd: null },
        assignedTo: ['EXTRACT', 'SCORE'],
      }),
      model({ modelId: 'groq-qwen3-27b', label: 'Qwen 3.8 27B (Groq)' }),
      model({
        modelId: 'openrouter-nemotron-super',
        label: 'Nemotron 3 Super 120B (OpenRouter, free)',
        provider: 'openrouter',
        status: 'ERROR',
        detail: 'missing key: environment variable for provider openrouter is empty',
        keyConfigured: false,
        limits: { rpm: 20, rpd: 50, tokensPerMinute: null, tokensPerDay: null, requestsPerMonth: null },
      }),
      model({ modelId: 'groq-gpt-oss-20b', label: 'GPT-OSS 20B (Groq)', status: 'ERROR', detail: 'HTTP 404 model' }),
      model({
        modelId: 'claude-haiku',
        label: 'Claude Haiku 5.5',
        provider: 'anthropic',
        tier: 'PAID',
        limits: { rpm: null, rpd: null, tokensPerMinute: null, tokensPerDay: null, requestsPerMonth: null },
        usageToday: { calls: 0, inputTokens: 0, outputTokens: 0, costUsd: null },
      }),
      model({ modelId: 'fake', label: 'Fake (tests / UI work)', provider: 'fake', tier: 'LOCAL' }),
    ],
    history: [
      {
        purpose: 'SCORE',
        fromModelId: 'groq-qwen3-27b',
        toModelId: 'groq-gpt-oss-120b',
        reason: 'USER',
        at: '2026-10-21T09:00:00Z',
      },
    ],
    paidCallsAllowed: true,
    ...overrides,
  }
}

export function exhaustedAlert(overrides: Partial<LlmAlert> = {}): LlmAlert {
  return {
    purpose: 'EXTRACT',
    modelId: 'groq-gpt-oss-120b',
    modelLabel: 'GPT-OSS 120B (Groq)',
    status: 'EXHAUSTED',
    detail: 'Daily request limit 1000/1000',
    until: '2026-10-23T00:00:00Z',
    paused: true,
    switchedFromModelId: null,
    suggestions: [{ modelId: 'groq-qwen3-27b', label: 'Qwen 3.8 27B (Groq)', tier: 'FREE', status: 'AVAILABLE' }],
    ...overrides,
  }
}

export interface LlmApi {
  data: LlmAdmin
  alerts: LlmAlert[]
  puts: { purpose: string; body: Record<string, unknown> }[]
  tests: { modelId: string; body: unknown }[]
  /** Problem Details for the next PUT or test call (e.g. PAID_CONFIRMATION_REQUIRED). */
  failNext?: { status: number; body: Record<string, unknown>; unlessConfirmed?: boolean }
}

/**
 * MSW handlers for /api/admin/llm/** and /api/status: a successful switch updates the assignment
 * and drops that purpose's alerts (as the backend does on the next poll).
 */
export function mockLlmApi(data: LlmAdmin = llmAdminFixture(), alerts: LlmAlert[] = []): LlmApi {
  const api: LlmApi = { data, alerts, puts: [], tests: [] }
  const fail = (body: Record<string, unknown> | null) => {
    const f = api.failNext
    if (!f || (f.unlessConfirmed && body?.confirmPaid === true)) return null
    api.failNext = undefined
    return HttpResponse.json({ status: f.status, ...f.body }, { status: f.status })
  }
  server.use(
    http.get('/api/admin/llm', () => HttpResponse.json(api.data)),
    http.get('/api/status', () => HttpResponse.json({ ...statusFixture, llmAlerts: api.alerts } satisfies Status)),
    http.put('/api/admin/llm/assignments/:purpose', async ({ params, request }) => {
      const body = (await request.json()) as Record<string, unknown>
      const purpose = String(params.purpose)
      api.puts.push({ purpose, body })
      const failed = fail(body)
      if (failed) return failed
      const assignment = api.data.assignments.find((a) => a.purpose === purpose)
      if (!assignment) return HttpResponse.json({ status: 400, code: 'BAD_REQUEST' }, { status: 400 })
      Object.assign(assignment, {
        modelId: body.modelId,
        ...(body.autoFallback !== undefined ? { autoFallback: body.autoFallback } : {}),
        ...(body.allowPaid !== undefined ? { allowPaid: body.allowPaid } : {}),
        updatedBy: 'user',
      })
      api.alerts = api.alerts.filter((alert) => alert.purpose !== purpose)
      const tier = api.data.models.find((m) => m.modelId === body.modelId)?.tier
      return HttpResponse.json({
        assignment,
        warning: tier === 'PAID' ? 'PAID model: counts against the monthly budget' : null,
      })
    }),
    http.post('/api/admin/llm/models/:modelId/test', async ({ params, request }) => {
      const text = await request.text()
      const body = text ? (JSON.parse(text) as Record<string, unknown>) : null
      api.tests.push({ modelId: String(params.modelId), body })
      const failed = fail(body)
      if (failed) return failed
      return HttpResponse.json({ ok: true, latencyMs: 412, status: 'AVAILABLE', detail: null })
    }),
  )
  return api
}
