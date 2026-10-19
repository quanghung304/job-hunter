import { act, renderHook } from '@testing-library/react'
import { createElement, type ReactNode } from 'react'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import {
  activeFilterCount,
  DEFAULT_FILTERS,
  parseFilters,
  toApiQuery,
  toSearchParams,
  useBoardFilters,
} from './useBoardFilters'

describe('parseFilters / toSearchParams', () => {
  it('no params is the default view', () => {
    expect(parseFilters(new URLSearchParams())).toEqual(DEFAULT_FILTERS)
    expect(toSearchParams(DEFAULT_FILTERS).toString()).toBe('')
  })

  it('round-trips every filter', () => {
    const filters = {
      ...DEFAULT_FILTERS,
      status: ['NEW', 'APPLIED'] as const,
      decision: ['UNDECIDED'] as const,
      checks: ['HAS_FAIL', 'HAS_UNKNOWN'] as const,
      analysisStage: ['ANALYZED'] as const,
      source: ['himalayas', 'jobicy'],
      employmentType: ['CONTRACT'] as const,
      minScore: 60,
      maxScore: 90,
      postedWithinDays: 7,
      q: 'java dev',
      includeExpired: true,
      sort: 'posted' as const,
    }
    const params = toSearchParams({
      ...filters,
      status: [...filters.status],
      decision: [...filters.decision],
      checks: [...filters.checks],
      analysisStage: [...filters.analysisStage],
      employmentType: [...filters.employmentType],
    })

    expect(params.getAll('status')).toEqual(['NEW', 'APPLIED'])
    expect(parseFilters(params)).toEqual({
      ...filters,
      status: ['NEW', 'APPLIED'],
      decision: ['UNDECIDED'],
      checks: ['HAS_FAIL', 'HAS_UNKNOWN'],
      analysisStage: ['ANALYZED'],
      employmentType: ['CONTRACT'],
    })
  })

  it('drops unknown and invalid values', () => {
    const filters = parseFilters(
      new URLSearchParams('status=LOST&status=NEW,NEW&minScore=101&maxScore=abc&postedWithinDays=0&sort=title'),
    )

    expect(filters.status).toEqual(['NEW'])
    expect(filters.minScore).toBeNull()
    expect(filters.maxScore).toBeNull()
    expect(filters.postedWithinDays).toBeNull()
    expect(filters.sort).toBe('score')
  })

  it('counts active filters without the sort', () => {
    expect(activeFilterCount({ ...DEFAULT_FILTERS, sort: 'posted' })).toBe(0)
    expect(activeFilterCount({ ...DEFAULT_FILTERS, status: ['NEW', 'APPLIED'], minScore: 50, q: 'x' })).toBe(4)
  })

  it('builds the API query with omitted values for "no filter"', () => {
    expect(toApiQuery(DEFAULT_FILTERS)).toEqual({
      status: undefined,
      decision: undefined,
      checks: undefined,
      analysisStage: undefined,
      source: undefined,
      employmentType: undefined,
      minScore: undefined,
      maxScore: undefined,
      postedWithinDays: undefined,
      q: undefined,
      includeExpired: undefined,
      sort: 'score',
    })
    expect(toApiQuery({ ...DEFAULT_FILTERS, status: ['DISMISSED'], q: ' rust ' })).toMatchObject({
      status: ['DISMISSED'],
      q: 'rust',
    })
  })
})

describe('useBoardFilters', () => {
  function setup(route: string) {
    const location = { current: '' }
    function Spy() {
      const loc = useLocation()
      location.current = loc.pathname + loc.search
      return null
    }
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(MemoryRouter, { initialEntries: [route] }, children, createElement(Spy))
    const hook = renderHook(() => useBoardFilters(), { wrapper })
    return { hook, location }
  }

  it('reads the URL and writes updates back to it (bookmarkable)', () => {
    const { hook, location } = setup('/jobs/7?status=APPLIED&sort=posted')

    expect(hook.result.current.filters.status).toEqual(['APPLIED'])
    expect(hook.result.current.activeCount).toBe(1)

    act(() => hook.result.current.update({ minScore: 70 }))

    expect(location.current).toBe('/jobs/7?status=APPLIED&minScore=70&sort=posted')
    expect(hook.result.current.filters.minScore).toBe(70)
  })

  it('reset returns to the default view and keeps the sort', () => {
    const { hook, location } = setup('/?status=APPLIED&q=java&sort=posted')

    act(() => hook.result.current.reset())

    expect(location.current).toBe('/?sort=posted')
    expect(hook.result.current.activeCount).toBe(0)
  })
})
