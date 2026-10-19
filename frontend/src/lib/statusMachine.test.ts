import { describe, expect, it } from 'vitest'
import type { TrackingStatus } from './labels'
import { ALL_STATUSES, allowedTargets, canTransition, isTerminal, primaryTarget } from './statusMachine'

/** data-model.md §2: every allowed pair; everything else is illegal. */
const MATRIX: Record<TrackingStatus, TrackingStatus[]> = {
  NEW: ['SHORTLISTED', 'DISMISSED', 'APPLIED'],
  SHORTLISTED: ['DISMISSED', 'APPLIED', 'NEW'],
  DISMISSED: ['NEW'],
  APPLIED: ['INTERVIEWING', 'REJECTED', 'WITHDRAWN'],
  INTERVIEWING: ['OFFER', 'REJECTED', 'WITHDRAWN'],
  OFFER: [],
  REJECTED: [],
  WITHDRAWN: [],
}

describe('statusMachine', () => {
  const pairs = ALL_STATUSES.flatMap((from) => ALL_STATUSES.map((to) => [from, to] as const))

  it('covers all 64 pairs', () => {
    expect(pairs).toHaveLength(64)
  })

  it.each(pairs)('%s → %s matches data-model §2', (from, to) => {
    expect(canTransition(from, to)).toBe(MATRIX[from].includes(to))
  })

  it('lists the allowed targets and terminal statuses', () => {
    for (const status of ALL_STATUSES) {
      expect([...allowedTargets(status)].sort()).toEqual([...MATRIX[status]].sort())
      expect(isTerminal(status)).toBe(MATRIX[status].length === 0)
    }
  })

  it.each([
    ['NEW', 'SHORTLISTED'],
    ['SHORTLISTED', 'APPLIED'],
    ['DISMISSED', 'NEW'],
    ['APPLIED', 'INTERVIEWING'],
    ['INTERVIEWING', 'OFFER'],
    ['OFFER', null],
    ['REJECTED', null],
  ] as [TrackingStatus, TrackingStatus | null][])('the next step from %s is %s', (from, next) => {
    expect(primaryTarget(from, allowedTargets(from))).toBe(next)
  })

  it('has no primary step when the server does not allow it', () => {
    expect(primaryTarget('NEW', ['DISMISSED'])).toBeNull()
  })
})
