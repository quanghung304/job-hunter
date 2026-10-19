import {
  type InfiniteData,
  keepPreviousData,
  type QueryClient,
  type QueryKey,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useEffect } from 'react'
import type { Decision, TrackingStatus } from '@/lib/labels'
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

/** One grid page, and each "Load more" step of the job list panel (api-spec "Pagination"). */
export const PAGE_SIZE = 12

/** Query keys are arrays starting with the resource (conventions.md). */
export const queryKeys = {
  meta: ['meta'] as const,
  status: ['status'] as const,
  jobsAll: ['jobs'] as const,
  /** One grid page; `page` is 1-based like the URL. */
  jobsGrid: (query: JobsQuery, page: number) => ['jobs', query, 'grid', page] as const,
  /** The appendable job list of the split view. */
  jobsList: (query: JobsQuery) => ['jobs', query, 'list'] as const,
  job: (id: number) => ['job', id] as const,
}

function isGridKey(key: QueryKey): boolean {
  return key[0] === 'jobs' && key[2] === 'grid'
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

function fetchJobs(query: JobsQuery, page: number): Promise<JobPage> {
  return unwrap(api.GET('/api/jobs', { params: { query: { ...query, page, size: PAGE_SIZE } } }))
}

/** The board grid: one page of 12; `page` is 1-based. The previous page stays shown while the next loads. */
export function useJobs(query: JobsQuery, page: number) {
  return useQuery({
    queryKey: queryKeys.jobsGrid(query, page),
    queryFn: () => fetchJobs(query, page - 1),
    placeholderData: keepPreviousData,
  })
}

/**
 * The job list panel: pages of 12 that "Load more" appends. Seeded with pages 1…`seedPages`
 * (the grid page the job was opened from), also when a shorter list is still cached.
 */
export function useInfiniteJobs(query: JobsQuery, seedPages = 1) {
  const result = useInfiniteQuery({
    queryKey: queryKeys.jobsList(query),
    queryFn: ({ pageParam }) => fetchJobs(query, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last: JobPage) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
  const { data, hasNextPage, isFetching, fetchNextPage } = result
  const loaded = data?.pages.length ?? 0
  useEffect(() => {
    if (loaded > 0 && loaded < seedPages && hasNextPage && !isFetching) void fetchNextPage()
  }, [loaded, seedPages, hasNextPage, isFetching, fetchNextPage])
  return result
}

export function useJob(id: number | null) {
  return useQuery({
    queryKey: queryKeys.job(id ?? -1),
    queryFn: () => unwrap(api.GET('/api/jobs/{id}', { params: { path: { id: id as number } } })),
    enabled: id !== null,
  })
}

/** A cached board query: one grid page or the appendable list. */
type JobsData = JobPage | InfiniteData<JobPage, number>

function pagesOf(data: JobsData): JobPage[] {
  return 'pages' in data ? data.pages : [data]
}

function isDefaultQuery(query: JobsQuery): boolean {
  return Object.entries(query).every(([key, value]) => key === 'sort' || value === undefined)
}

/**
 * Whether a job with this status stays in a list loaded with this query (default view: status ≠
 * DISMISSED). A decision change never removes a card at once (frontend.md "Behavior › Board").
 */
export function statusVisibleIn(query: JobsQuery, status: TrackingStatus): boolean {
  if (isDefaultQuery(query)) return status !== 'DISMISSED'
  return query.status === undefined || query.status.includes(status)
}

/** Applies `patch` to the job in every cached board query; `remove` drops it from those it no longer belongs to. */
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
    const pages = pagesOf(data).map((page) => {
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
    const counted = removed
      ? pages.map((page) => ({ ...page, totalItems: Math.max(0, page.totalItems - removed) }))
      : pages
    const next: JobsData = 'pages' in data ? { ...data, pages: counted } : (counted[0] as JobPage)
    client.setQueryData<JobsData>(key, next)
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
 * caller shows the toast). Grid pages refetch afterwards so a dismissed card's place refills; the
 * list panel is only marked stale, so its scroll position and the keyboard flow stay put.
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
        const present = data ? pagesOf(data).some((page) => page.items.some((job) => job.id === id)) : false
        if (!present && !isGridKey(key) && statusVisibleIn(key[1] as JobsQuery, summary.status)) {
          void client.invalidateQueries({ queryKey: key, exact: true })
        }
      }
      void client.invalidateQueries({ queryKey: queryKeys.job(id) })
      void client.invalidateQueries({ predicate: (query) => isGridKey(query.queryKey) })
      void client.invalidateQueries({ queryKey: queryKeys.jobsAll, refetchType: 'none' })
    },
  })
}

export interface DecisionChange {
  id: number
  to: Decision | null
}

/**
 * The owner's eligibility decision (ADR-0011), optimistic on the list and detail caches; rolled
 * back on error (the caller shows the toast). A job marked Not eligible keeps its card until the
 * next refetch, so nothing is removed here and lists are only marked stale.
 */
export function useSetDecision() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, to }: DecisionChange) =>
      unwrap(api.PUT('/api/jobs/{id}/eligibility', { params: { path: { id } }, body: { decision: to } })),
    onMutate: async ({ id, to }) => {
      await client.cancelQueries({ queryKey: queryKeys.jobsAll })
      await client.cancelQueries({ queryKey: queryKeys.job(id) })
      const lists = client.getQueriesData<JobsData>({ queryKey: queryKeys.jobsAll })
      const detail = client.getQueryData<JobDetail>(queryKeys.job(id))
      patchLists(client, id, (job) => ({ ...job, eligibilityDecision: to }))
      if (detail) {
        client.setQueryData<JobDetail>(queryKeys.job(id), {
          ...detail,
          eligibilityDecision: to,
          eligibilityDecidedAt: to === null ? null : new Date().toISOString(),
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

/**
 * The owner's Analyze or Score on one job (ADR-0010): one request, the answer updates list and
 * detail. The mutation key lets every button of the job show the running request.
 */
export function useAnalysisAction(action: 'analyze' | 'score', id: number) {
  const client = useQueryClient()
  return useMutation({
    mutationKey: [action, id],
    mutationFn: () =>
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

/** Position of a job in the cached grid page it was opened from (mobile "Job N of M"); undefined when not cached. */
export function cachedPosition(
  client: QueryClient,
  query: JobsQuery,
  page: number,
  id: number,
): { position: number; total: number } | undefined {
  const data = client.getQueryData<JobPage>(queryKeys.jobsGrid(query, page))
  const index = data?.items.findIndex((job) => job.id === id) ?? -1
  if (!data || index < 0) return undefined
  return { position: (page - 1) * PAGE_SIZE + index + 1, total: data.totalItems }
}
