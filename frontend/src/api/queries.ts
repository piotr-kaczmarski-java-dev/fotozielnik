// Server state via TanStack Query (ADR-0012). Query keys live here so invalidation stays consistent.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet } from './client.ts'
import type { Me, SessionResponse } from './types.ts'

export const keys = {
  me: ['me'] as const,
  photos: ['photos'] as const,
  photo: (id: string) => ['photos', id] as const,
  imports: ['imports'] as const,
  importBatch: (id: string) => ['imports', id] as const,
  users: ['admin', 'users'] as const,
}

/** The signed-in user, or null when signed out (asked via /api/auth/session, which never answers 401). */
export async function fetchMe(): Promise<Me | null> {
  return (await apiGet<SessionResponse>('/api/auth/session')).user ?? null
}

export function useMe() {
  return useQuery({ queryKey: keys.me, queryFn: fetchMe, staleTime: 5 * 60_000 })
}

export function useSetMe() {
  const client = useQueryClient()
  return (me: Me | null) => client.setQueryData(keys.me, me)
}
