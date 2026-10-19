import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import type { JobsQuery } from '@/api/queries'
import type { AnalysisStage, ChecksFilter, DecisionFilter, EmploymentType, TrackingStatus } from '@/lib/labels'

export type Sort = 'score' | 'posted'

/** Board filters (api-spec `GET /api/jobs`); they live in the URL so views can be bookmarked. */
export interface BoardFilters {
  status: TrackingStatus[]
  decision: DecisionFilter[]
  checks: ChecksFilter[]
  analysisStage: AnalysisStage[]
  source: string[]
  employmentType: EmploymentType[]
  minScore: number | null
  maxScore: number | null
  postedWithinDays: number | null
  q: string
  includeExpired: boolean
  sort: Sort
}

export const DEFAULT_FILTERS: BoardFilters = {
  status: [],
  decision: [],
  checks: [],
  analysisStage: [],
  source: [],
  employmentType: [],
  minScore: null,
  maxScore: null,
  postedWithinDays: null,
  q: '',
  includeExpired: false,
  sort: 'score',
}

const STATUSES = new Set([
  'NEW',
  'SHORTLISTED',
  'DISMISSED',
  'APPLIED',
  'INTERVIEWING',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
])
const DECISIONS = new Set(['ELIGIBLE', 'INELIGIBLE', 'UNDECIDED'])
const CHECKS = new Set(['ALL_OK', 'HAS_UNKNOWN', 'HAS_FAIL', 'NOT_CHECKED'])
const STAGES = new Set(['NOT_ANALYZED', 'ANALYZED', 'SCORED', 'FAILED'])
const TYPES = new Set(['FULL_TIME', 'PART_TIME', 'CONTRACT', 'FREELANCE', 'INTERNSHIP'])

function list<T extends string>(params: URLSearchParams, name: string, allowed?: Set<string>): T[] {
  const values = params.getAll(name).flatMap((value) => value.split(','))
  return [...new Set(values.filter((value) => value !== '' && (!allowed || allowed.has(value))))] as T[]
}

function int(params: URLSearchParams, name: string, min: number, max: number): number | null {
  const raw = params.get(name)
  if (raw === null || raw.trim() === '') return null
  const value = Number(raw)
  return Number.isInteger(value) && value >= min && value <= max ? value : null
}

/** Reads filters from URL search params, dropping unknown or invalid values. */
export function parseFilters(params: URLSearchParams): BoardFilters {
  return {
    status: list<TrackingStatus>(params, 'status', STATUSES),
    decision: list<DecisionFilter>(params, 'decision', DECISIONS),
    checks: list<ChecksFilter>(params, 'checks', CHECKS),
    analysisStage: list<AnalysisStage>(params, 'analysisStage', STAGES),
    source: list<string>(params, 'source'),
    employmentType: list<EmploymentType>(params, 'employmentType', TYPES),
    minScore: int(params, 'minScore', 0, 100),
    maxScore: int(params, 'maxScore', 0, 100),
    postedWithinDays: int(params, 'postedWithinDays', 1, 3650),
    q: params.get('q')?.trim() ?? '',
    includeExpired: params.get('includeExpired') === 'true',
    sort: params.get('sort') === 'posted' ? 'posted' : 'score',
  }
}

/** Writes filters as search params; defaults are left out so "/" is the default view. */
export function toSearchParams(filters: BoardFilters): URLSearchParams {
  const params = new URLSearchParams()
  const lists = ['status', 'decision', 'checks', 'analysisStage', 'source', 'employmentType'] as const
  for (const name of lists) {
    for (const value of filters[name]) params.append(name, value)
  }
  if (filters.minScore !== null) params.set('minScore', String(filters.minScore))
  if (filters.maxScore !== null) params.set('maxScore', String(filters.maxScore))
  if (filters.postedWithinDays !== null) params.set('postedWithinDays', String(filters.postedWithinDays))
  if (filters.q.trim() !== '') params.set('q', filters.q.trim())
  if (filters.includeExpired) params.set('includeExpired', 'true')
  if (filters.sort !== 'score') params.set('sort', filters.sort)
  return params
}

/** Number of active filters (sort is not a filter). */
export function activeFilterCount(filters: BoardFilters): number {
  return (
    filters.status.length +
    filters.decision.length +
    filters.checks.length +
    filters.analysisStage.length +
    filters.source.length +
    filters.employmentType.length +
    (filters.minScore !== null || filters.maxScore !== null ? 1 : 0) +
    (filters.postedWithinDays !== null ? 1 : 0) +
    (filters.q.trim() !== '' ? 1 : 0) +
    (filters.includeExpired ? 1 : 0)
  )
}

/** The query of `GET /api/jobs` without paging; omitted values mean "no filter". */
export function toApiQuery(filters: BoardFilters): JobsQuery {
  return {
    status: filters.status.length ? filters.status : undefined,
    decision: filters.decision.length ? filters.decision : undefined,
    checks: filters.checks.length ? filters.checks : undefined,
    analysisStage: filters.analysisStage.length ? filters.analysisStage : undefined,
    source: filters.source.length ? filters.source : undefined,
    employmentType: filters.employmentType.length ? filters.employmentType : undefined,
    minScore: filters.minScore ?? undefined,
    maxScore: filters.maxScore ?? undefined,
    postedWithinDays: filters.postedWithinDays ?? undefined,
    q: filters.q.trim() || undefined,
    includeExpired: filters.includeExpired || undefined,
    sort: filters.sort,
  }
}

/** The 1-based grid page from the URL (`page`); 1 when missing or invalid. */
export function parsePage(params: URLSearchParams): number {
  return int(params, 'page', 1, 100_000) ?? 1
}

/** Search params of a view: filters plus the page (left out on page 1). */
export function viewSearch(filters: BoardFilters, page: number): string {
  const params = toSearchParams(filters)
  if (page > 1) params.set('page', String(page))
  return params.toString()
}

/**
 * Board filters and grid page bound to the URL (bookmarkable). Updates replace the history entry
 * (no back-button spam); any filter change goes back to page 1. Page changes are history entries.
 */
export function useBoardFilters() {
  const [params, setParams] = useSearchParams()
  const filters = useMemo(() => parseFilters(params), [params])
  const page = parsePage(params)

  const update = useCallback(
    (patch: Partial<BoardFilters>) => {
      setParams((current) => toSearchParams({ ...parseFilters(current), ...patch }), { replace: true })
    },
    [setParams],
  )
  const reset = useCallback(() => {
    setParams((current) => toSearchParams({ ...DEFAULT_FILTERS, sort: parseFilters(current).sort }), { replace: true })
  }, [setParams])
  const setPage = useCallback(
    (next: number, replace = false) => {
      // A new history entry unless replacing: Back returns to the previous page.
      setParams((current) => new URLSearchParams(viewSearch(parseFilters(current), next)), { replace })
    },
    [setParams],
  )

  return {
    filters,
    update,
    reset,
    page,
    setPage,
    activeCount: activeFilterCount(filters),
    search: params.toString(),
    /** Search params with another page, for links that keep the filters. */
    searchWithPage: (next: number) => viewSearch(filters, next),
  }
}
