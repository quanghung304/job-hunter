import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './Pagination'
import { pageItems } from './pageItems'

describe('pageItems', () => {
  it('lists every page up to 7', () => {
    expect(pageItems(1, 3)).toEqual([1, 2, 3])
    expect(pageItems(4, 7)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('keeps first, last and the neighbours of the current page with gaps', () => {
    expect(pageItems(1, 20)).toEqual([1, 2, 3, 4, 'gap', 20])
    expect(pageItems(10, 20)).toEqual([1, 'gap', 9, 10, 11, 'gap', 20])
    expect(pageItems(20, 20)).toEqual([1, 'gap', 17, 18, 19, 20])
  })
})

describe('Pagination', () => {
  it('shows the range, marks the current page and changes pages', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Pagination page={2} pageSize={12} totalItems={30} onChange={onChange} />)

    expect(screen.getByText('Showing 13–24 of 30 jobs')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Page 1' })).not.toHaveAttribute('aria-current')

    await user.click(screen.getByRole('button', { name: 'Page 3' }))
    await user.click(screen.getByRole('button', { name: 'Previous page' }))
    await user.click(screen.getByRole('button', { name: 'Page 2' }))

    expect(onChange.mock.calls).toEqual([[3], [1]])
  })

  it('disables previous on the first page and next on the last', () => {
    const { rerender } = render(<Pagination page={1} pageSize={12} totalItems={30} onChange={() => {}} />)
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled()

    rerender(<Pagination page={3} pageSize={12} totalItems={30} onChange={() => {}} />)
    expect(screen.getByText('Showing 25–30 of 30 jobs')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
  })

  it('a single page has both ends disabled', () => {
    render(<Pagination page={1} pageSize={12} totalItems={1} onChange={() => {}} />)

    expect(screen.getByText('Showing 1–1 of 1 job')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
  })
})
