// Server state via TanStack Query (ADR-0012). Query keys live here so invalidation stays consistent.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, apiGet } from './client.ts'
import type { Me } from './types.ts'

export const keys = {
  me: ['me'] as const,
  photos: ['photos'] as const,
  photo: (id: string) => ['photos', id] as const,
  imports: ['imports'] as const,
  importBatch: (id: string) => ['imports', id] as const,
  users: ['admin', 'users'] as const,
}

/** The signed-in user, or null when signed out (a 401 is an answer here, not an error). */
export async function fetchMe(): Promise<Me | null> {
  try {
    return await apiGet<Me>('/api/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

export function useMe() {
  return useQuery({ queryKey: keys.me, queryFn: fetchMe, staleTime: 5 * 60_000 })
}

export function useSetMe() {
  const client = useQueryClient()
  return (me: Me | null) => client.setQueryData(keys.me, me)
}
