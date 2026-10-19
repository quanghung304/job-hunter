import {
  type InfiniteData,
  type QueryClient,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type { TrackingStatus } from '@/lib/labels'
import { allowedTargets } from '@/lib/statusMachine'
import { api, unwrap } from './client'
import type { components, paths } from './schema'

export type Meta = components['schemas']['MetaResponse']
export type JobSummary = components['schemas']['JobSummaryResponse']
export type JobDetail = components['schemas']['JobDetailResponse']
export type JobPage = components['schemas']['JobPageResponse']
export type Status = components['schemas']['StatusResponse']
export type LlmAlert = components['schemas']['LlmAlertResponse']

/** Filters and sort of `GET /api/jobs` without paging; omitted values mean "no filter". */
export type JobsQuery = Omit<NonNullable<paths['/api/jobs']['get']['parameters']['query']>, 'page' | 'size'>

export const PAGE_SIZE = 25

/** Query keys are arrays starting with the resource (conventions.md). */
export const queryKeys = {
  meta: ['meta'] as const,
  status: ['status'] as const,
  jobsAll: ['jobs'] as const,
  jobs: (query: JobsQuery) => ['jobs', query] as const,
  job: (id: number) => ['job', id] as const,
}

/** Filter values and the current profile (GET /api/meta). Rarely changes. */
export function useMeta() {
  return useQuery({
    queryKey: queryKeys.meta,
    queryFn: () => unwrap(api.GET('/api/meta')),
    staleTime: 5 * 60_000,
  })
}

/** Header status line and LLM alerts; polled every 60 s, every 10 s while an alert is shown. */
export function useStatus() {
  return useQuery({
    queryKey: queryKeys.status,
    queryFn: () => unwrap(api.GET('/api/status')),
    refetchInterval: (query) => ((query.state.data?.llmAlerts.length ?? 0) > 0 ? 10_000 : 60_000),
  })
}

/** The paused LLM alert of a purpose, if any (Analyze/Score buttons are disabled then). */
export function usePausedPurpose(purpose: 'EXTRACT' | 'SCORE'): LlmAlert | undefined {
  const status = useStatus()
  return status.data?.llmAlerts.find((alert) => alert.purpose === purpose && alert.paused)
}

/** The board: pages of 25 with "Load more". */
export function useJobs(query: JobsQuery) {
  return useInfiniteQuery({
    queryKey: queryKeys.jobs(query),
    queryFn: ({ pageParam }) =>
      unwrap(api.GET('/api/jobs', { params: { query: { ...query, page: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: 0,
    getNextPageParam: (last: JobPage) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

export function useJob(id: number | null) {
  return useQuery({
    queryKey: queryKeys.job(id ?? -1),
    queryFn: () => unwrap(api.GET('/api/jobs/{id}', { params: { path: { id: id as number } } })),
    enabled: id !== null,
  })
}

type JobsData = InfiniteData<JobPage, number>

function isDefaultQuery(query: JobsQuery): boolean {
  return Object.entries(query).every(([key, value]) => key === 'sort' || value === undefined)
}

/** Whether a job with this status stays in a list loaded with this query (default view: NEW, SHORTLISTED). */
export function statusVisibleIn(query: JobsQuery, status: TrackingStatus): boolean {
  if (isDefaultQuery(query)) return status === 'NEW' || status === 'SHORTLISTED'
  return query.status === undefined || query.status.includes(status)
}

/** Applies `patch` to the job in every cached board list; `remove` drops it from lists it no longer belongs to. */
function patchLists(
  client: QueryClient,
  id: number,
  patch: (job: JobSummary) => JobSummary,
  remove?: (query: JobsQuery, job: JobSummary) => boolean,
) {
  for (const [key, data] of client.getQueriesData<JobsData>({ queryKey: queryKeys.jobsAll })) {
    if (!data) continue
    const query = key[1] as JobsQuery
    let removed = 0
    const pages = data.pages.map((page) => {
      if (!page.items.some((job) => job.id === id)) return page
      const items: JobSummary[] = []
      for (const job of page.items) {
        if (job.id !== id) {
          items.push(job)
          continue
        }
        const next = patch(job)
        if (remove?.(query, next)) {
          removed++
        } else {
          items.push(next)
        }
      }
      return { ...page, items }
    })
    client.setQueryData<JobsData>(key, {
      ...data,
      pages: removed ? pages.map((page) => ({ ...page, totalItems: Math.max(0, page.totalItems - removed) })) : pages,
    })
  }
}

/** The summary part of a detail, for list caches. */
function summaryOf(detail: JobDetail): Partial<JobSummary> {
  return {
    score: detail.score,
    scoreBand: detail.scoreBand,
    summary: detail.summary,
    eligibilityDecision: detail.eligibilityDecision,
    checks: detail.checks,
    remoteType: detail.remoteType,
    allowedRegions: detail.allowedRegions,
    employmentTypes: detail.employmentTypes,
    status: detail.status,
    jobStatus: detail.jobStatus,
    analysisStage: detail.analysisStage,
  }
}

export interface StatusChange {
  id: number
  from: TrackingStatus
  to: TrackingStatus
  note?: string
}

/**
 * Tracking status change, optimistic on the list and detail caches; rolled back on error (the
 * caller shows the toast). Lists are marked stale but not refetched, so the keyboard flow stays put.
 */
export function useChangeStatus() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, to, note }: StatusChange) =>
      unwrap(api.POST('/api/jobs/{id}/status', { params: { path: { id } }, body: { to, note: note ?? null } })),
    onMutate: async ({ id, to }) => {
      await client.cancelQueries({ queryKey: queryKeys.jobsAll })
      await client.cancelQueries({ queryKey: queryKeys.job(id) })
      const lists = client.getQueriesData<JobsData>({ queryKey: queryKeys.jobsAll })
      const detail = client.getQueryData<JobDetail>(queryKeys.job(id))
      patchLists(
        client,
        id,
        (job) => ({ ...job, status: to }),
        (query, job) => !statusVisibleIn(query, job.status),
      )
      if (detail) {
        client.setQueryData<JobDetail>(queryKeys.job(id), {
          ...detail,
          status: to,
          allowedTransitions: [...allowedTargets(to)],
        })
      }
      return { lists, detail }
    },
    onError: (_error, { id }, context) => {
      context?.lists.forEach(([key, data]) => client.setQueryData(key, data))
      if (context?.detail) client.setQueryData(queryKeys.job(id), context.detail)
    },
    onSuccess: (summary, { id }) => {
      patchLists(client, id, (job) => ({ ...job, ...summary }))
      // A job back in a list it had left (Undo of a dismiss): reload that list to show it again.
      for (const [key, data] of client.getQueriesData<JobsData>({ queryKey: queryKeys.jobsAll })) {
        const present = data?.pages.some((page) => page.items.some((job) => job.id === id)) ?? false
        if (!present && statusVisibleIn(key[1] as JobsQuery, summary.status)) {
          void client.invalidateQueries({ queryKey: key, exact: true })
        }
      }
      void client.invalidateQueries({ queryKey: queryKeys.job(id) })
      void client.invalidateQueries({ queryKey: queryKeys.jobsAll, refetchType: 'none' })
    },
  })
}

export function useUpdateNotes(id: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (notes: string) =>
      unwrap(api.PUT('/api/jobs/{id}/notes', { params: { path: { id } }, body: { notes } })),
    onSuccess: (_data, notes) => {
      const detail = client.getQueryData<JobDetail>(queryKeys.job(id))
      if (detail) client.setQueryData<JobDetail>(queryKeys.job(id), { ...detail, notes: notes === '' ? null : notes })
    },
  })
}

/** The owner's Analyze or Score on one job (ADR-0010): one request, the answer updates list and detail. */
export function useAnalysisAction(action: 'analyze' | 'score') {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) =>
      unwrap(
        action === 'analyze'
          ? api.POST('/api/jobs/{id}/analyze', { params: { path: { id } } })
          : api.POST('/api/jobs/{id}/score', { params: { path: { id } } }),
      ),
    onSuccess: (detail) => {
      client.setQueryData(queryKeys.job(detail.id), detail)
      patchLists(client, detail.id, (job) => ({ ...job, ...summaryOf(detail) }))
      void client.invalidateQueries({ queryKey: queryKeys.jobsAll, refetchType: 'none' })
    },
  })
}

/** Jobs of a cached board list (mobile "Job N of M"); undefined when the list is not cached. */
export function cachedJobIds(client: QueryClient, query: JobsQuery): { ids: number[]; total: number } | undefined {
  const data = client.getQueryData<JobsData>(queryKeys.jobs(query))
  if (!data) return undefined
  return {
    ids: data.pages.flatMap((page) => page.items.map((job) => job.id)),
    total: data.pages[data.pages.length - 1]?.totalItems ?? 0,
  }
}
