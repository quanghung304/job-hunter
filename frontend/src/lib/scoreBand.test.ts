import { describe, expect, it } from 'vitest'
import { scoreBand } from './scoreBand'

describe('scoreBand', () => {
  it.each([
    [100, 'EXCELLENT'],
    [85, 'EXCELLENT'],
    [84, 'GOOD'],
    [70, 'GOOD'],
    [69, 'MAYBE'],
    [50, 'MAYBE'],
    [49, 'WEAK'],
    [0, 'WEAK'],
    [null, 'NONE'],
    [undefined, 'NONE'],
    [Number.NaN, 'NONE'],
  ])('%s → %s', (score, band) => {
    expect(scoreBand(score)).toBe(band)
  })
})
