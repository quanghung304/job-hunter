import type { ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/client'
import { useMeta } from '@/api/queries'
import { metaFixture } from '@/test/handlers'
import { testQueryClient } from '@/test/render'
import { server } from '@/test/server'

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={testQueryClient()}>{children}</QueryClientProvider>
}

describe('useMeta', () => {
  it('returns the meta response', async () => {
    const { result } = renderHook(() => useMeta(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(metaFixture)
  })

  it('turns Problem Details into an ApiError', async () => {
    server.use(
      http.get('/api/meta', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Internal Server Error',
            status: 500,
            detail: 'Unexpected server error',
            code: 'INTERNAL_ERROR',
          },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    const { result } = renderHook(() => useMeta(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
    const error = result.current.error as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(500)
    expect(error.code).toBe('INTERNAL_ERROR')
    expect(error.detail).toBe('Unexpected server error')
    expect(error.network).toBe(false)
  })

  it('reports a network failure', async () => {
    server.use(http.get('/api/meta', () => HttpResponse.error()))

    const { result } = renderHook(() => useMeta(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as ApiError).network).toBe(true)
  })
})
