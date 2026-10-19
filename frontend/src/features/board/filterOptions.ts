import { useMeta } from '@/api/queries'
import {
  ANALYSIS_STAGE_LABELS,
  type AnalysisStage,
  ELIGIBILITY_LABELS,
  type Eligibility,
  EMPLOYMENT_LABELS,
  type EmploymentType,
  STATUS_LABELS,
  type TrackingStatus,
} from '@/lib/labels'
import { ALL_STATUSES } from '@/lib/statusMachine'
import type { BoardFilters } from './useBoardFilters'

export interface Option<T extends string> {
  value: T
  label: string
}

export const POSTED_OPTIONS: Option<string>[] = [
  { value: '', label: 'Any time' },
  { value: '1', label: 'Last 24 hours' },
  { value: '3', label: 'Last 3 days' },
  { value: '7', label: 'Last 7 days' },
  { value: '14', label: 'Last 14 days' },
  { value: '30', label: 'Last 30 days' },
]

/** Option lists of the multi-value filters; sources and types come from `GET /api/meta`. */
export function useFilterOptions() {
  const meta = useMeta()
  const statuses = (meta.data?.statuses.length ? meta.data.statuses : ALL_STATUSES) as TrackingStatus[]
  const types = (
    meta.data?.employmentTypes.length ? meta.data.employmentTypes : Object.keys(EMPLOYMENT_LABELS)
  ) as EmploymentType[]
  return {
    status: statuses.map((value) => ({ value, label: STATUS_LABELS[value] })),
    eligibility: (['ELIGIBLE', 'UNCERTAIN', 'INELIGIBLE'] as Eligibility[]).map((value) => ({
      value,
      label: ELIGIBILITY_LABELS[value],
    })),
    analysisStage: (Object.keys(ANALYSIS_STAGE_LABELS) as AnalysisStage[]).map((value) => ({
      value,
      label: ANALYSIS_STAGE_LABELS[value],
    })),
    source: (meta.data?.sources ?? []).map((source) => ({ value: source.code, label: source.name })),
    employmentType: types.map((value) => ({ value, label: EMPLOYMENT_LABELS[value] })),
  }
}

export type ListFilter = 'status' | 'eligibility' | 'analysisStage' | 'source' | 'employmentType'

export const LIST_FILTER_LABELS: Record<ListFilter, string> = {
  status: 'Status',
  eligibility: 'Eligibility',
  analysisStage: 'Analysis',
  source: 'Source',
  employmentType: 'Type',
}

/** "Any", the single label, or "2 selected". */
export function listValueText(selected: readonly string[], options: readonly Option<string>[]): string {
  if (selected.length === 0) return 'Any'
  if (selected.length === 1) return options.find((o) => o.value === selected[0])?.label ?? selected[0]
  return `${selected.length} selected`
}

export function scoreValueText(filters: BoardFilters): string {
  const { minScore, maxScore } = filters
  if (minScore === null && maxScore === null) return 'Any'
  if (maxScore === null) return `≥ ${minScore}`
  if (minScore === null) return `≤ ${maxScore}`
  return `${minScore}–${maxScore}`
}

export function postedValueText(filters: BoardFilters): string {
  return (
    POSTED_OPTIONS.find((o) => o.value === String(filters.postedWithinDays ?? ''))?.label ??
    `${filters.postedWithinDays}d`
  )
}
