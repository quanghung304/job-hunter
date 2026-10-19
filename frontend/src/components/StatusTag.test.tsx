import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AnalysisStage, Decision, TrackingStatus } from '@/lib/labels'
import { AnalysisTag } from './AnalysisTag'
import { ChecksTag } from './ChecksTag'
import { DecisionTag } from './DecisionTag'
import { StatusTag } from './StatusTag'

/** The tag element of a word, and whether it carries an icon (color is never the only signal). */
function tagOf(word: string) {
  const tag = screen.getByText(word)
  return { tag, icon: tag.querySelector('svg') }
}

describe('StatusTag', () => {
  it.each([
    ['NEW', 'New', 'bg-line-faint'],
    ['SHORTLISTED', 'Shortlisted', 'bg-st-shortlisted-bg'],
    ['APPLIED', 'Applied', 'bg-st-applied-bg'],
    ['INTERVIEWING', 'Interviewing', 'bg-st-interviewing-bg'],
    ['OFFER', 'Offer', 'bg-st-offer-bg'],
    ['REJECTED', 'Rejected', 'bg-strong'],
    ['WITHDRAWN', 'Withdrawn', 'bg-st-withdrawn-bg'],
    ['DISMISSED', 'Dismissed', 'bg-st-withdrawn-bg'],
  ] as [TrackingStatus, string, string][])(
    '%s shows "%s" with its token color and an icon',
    (status, label, className) => {
      render(<StatusTag status={status} />)

      const { tag, icon } = tagOf(label)
      expect(tag).toHaveClass(className)
      expect(icon).not.toBeNull()
    },
  )

  it('is hidden for NEW in rows', () => {
    const { container } = render(<StatusTag status="NEW" hideNew />)

    expect(container).toBeEmptyDOMElement()
  })

  it('is shown for other statuses in rows', () => {
    render(<StatusTag status="SHORTLISTED" hideNew />)

    expect(screen.getByText('Shortlisted')).toBeInTheDocument()
  })
})

describe('DecisionTag', () => {
  it.each([
    ['ELIGIBLE', 'Eligible', 'bg-eligible-bg'],
    ['INELIGIBLE', 'Not eligible', 'bg-ineligible-bg'],
  ] as [Decision, string, string][])('%s shows "%s" with its token color and an icon', (decision, label, className) => {
    render(<DecisionTag decision={decision} />)

    const { tag, icon } = tagOf(label)
    expect(tag).toHaveClass(className)
    expect(icon).not.toBeNull()
  })
})

describe('AnalysisTag', () => {
  it.each([
    ['ANALYZED', 'Extracted', 'bg-extracted-bg'],
    ['FAILED', 'Analysis failed', 'bg-strong'],
  ] as [AnalysisStage, string, string][])(
    '%s shows "%s" with its token color and an icon',
    (stage, label, className) => {
      render(<AnalysisTag stage={stage} />)

      const { tag, icon } = tagOf(label)
      expect(tag).toHaveClass(className)
      expect(icon).not.toBeNull()
    },
  )

  it('shows "Not analyzed" only where asked (detail header) and nothing for scored jobs', () => {
    const { container, rerender } = render(<AnalysisTag stage="NOT_ANALYZED" />)
    expect(container).toBeEmptyDOMElement()

    rerender(<AnalysisTag stage="NOT_ANALYZED" showNotAnalyzed />)
    expect(tagOf('Not analyzed').icon).not.toBeNull()

    rerender(<AnalysisTag stage="SCORED" showNotAnalyzed />)
    expect(container).toBeEmptyDOMElement()
  })
})

describe('ChecksTag', () => {
  it('counts unclear and failing checks, with the attention color and an icon', () => {
    render(<ChecksTag checks={{ unknown: 1, fail: 1 }} />)

    const { tag, icon } = tagOf('Check 2 criteria')
    expect(tag).toHaveClass('bg-attention-bg')
    expect(icon).not.toBeNull()
  })

  it('is hidden when every check is OK or there are none', () => {
    const { container, rerender } = render(<ChecksTag checks={{ unknown: 0, fail: 0 }} />)
    expect(container).toBeEmptyDOMElement()

    rerender(<ChecksTag checks={null} />)
    expect(container).toBeEmptyDOMElement()
  })
})
