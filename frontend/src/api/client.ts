import createClient from 'openapi-fetch'
import type { paths } from './schema'

/**
 * The only HTTP client; components never call fetch (frontend.md "Rules"). Same origin as the
 * page (Vite proxies /api in dev; the backend serves the SPA otherwise). An absolute origin
 * instead of "/" because Node's fetch (tests) rejects relative URLs.
 */
export const api = createClient<paths>({
  baseUrl: typeof window === 'undefined' ? '' : window.location.origin,
  // Resolve fetch per call (not at import), so test interceptors (MSW) installed later apply.
  fetch: (request) => globalThis.fetch(request),
})

/** RFC 9457 Problem Details as sent by the backend (api-spec.md "Conventions"). */
export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  code?: string
}

/** A failed API call: a Problem Details response, or no response at all (backend down). */
export class ApiError extends Error {
  readonly status: number | null
  readonly code: string | null
  readonly detail: string | null
  readonly network: boolean

  constructor(options: {
    status: number | null
    code?: string | null
    detail?: string | null
    network?: boolean
    title?: string
  }) {
    super(options.title ?? options.detail ?? 'Request failed')
    this.name = 'ApiError'
    this.status = options.status
    this.code = options.code ?? null
    this.detail = options.detail ?? null
    this.network = options.network ?? false
  }

  static network(cause: unknown): ApiError {
    const error = new ApiError({ status: null, network: true, title: 'Backend not reachable' })
    error.cause = cause
    return error
  }

  static fromProblem(status: number, body: unknown): ApiError {
    const problem: ProblemDetail = isProblem(body) ? body : {}
    return new ApiError({ status, code: problem.code, detail: problem.detail, title: problem.title })
  }
}

function isProblem(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null
}

/**
 * Unwraps an openapi-fetch result: returns data, or throws ApiError (TanStack Query
 * surfaces it as the query error).
 */
export async function unwrap<T>(request: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  let result: Awaited<typeof request>
  try {
    result = await request
  } catch (cause) {
    throw ApiError.network(cause)
  }
  if (result.error !== undefined || !result.response.ok) {
    throw ApiError.fromProblem(result.response.status, result.error)
  }
  return result.data as T
}
