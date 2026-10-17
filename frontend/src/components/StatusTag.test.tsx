import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { TrackingStatus } from '@/lib/labels'
import { StatusTag } from './StatusTag'

describe('StatusTag', () => {
  it.each([
    ['NEW', 'New', 'bg-line-faint'],
    ['SHORTLISTED', 'Shortlisted', 'bg-accent-tint'],
    ['APPLIED', 'Applied', 'bg-accent-tint'],
    ['INTERVIEWING', 'Interviewing', 'bg-accent'],
    ['OFFER', 'Offer', 'bg-accent'],
    ['REJECTED', 'Rejected', 'bg-neutral-fill'],
    ['WITHDRAWN', 'Withdrawn', 'bg-neutral-fill'],
  ] as [TrackingStatus, string, string][])('%s shows "%s"', (status, label, className) => {
    render(<StatusTag status={status} />)

    expect(screen.getByText(label)).toHaveClass(className)
  })

  it('is hidden for NEW in rows', () => {
    const { container } = render(<StatusTag status="NEW" hideNew />)

    expect(container).toBeEmptyDOMElement()
  })

  it('is shown for other statuses in rows', () => {
    render(<StatusTag status="SHORTLISTED" hideNew />)

    expect(screen.getByText('Shortlisted')).toBeInTheDocument()
  })
})
