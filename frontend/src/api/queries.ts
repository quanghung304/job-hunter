import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from './client'
import type { components } from './schema'

export type Meta = components['schemas']['MetaResponse']

/** Query keys are arrays starting with the resource (conventions.md). */
export const queryKeys = {
  meta: ['meta'] as const,
}

/** Filter values and the current profile (GET /api/meta). Rarely changes. */
export function useMeta() {
  return useQuery({
    queryKey: queryKeys.meta,
    queryFn: () => unwrap(api.GET('/api/meta')),
    staleTime: 5 * 60_000,
  })
}
