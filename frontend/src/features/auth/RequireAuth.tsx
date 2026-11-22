import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useMe } from '../../api/queries.ts'
import type { Role } from '../../api/types.ts'
import { ErrorMessage, Loading } from '../../ui/ErrorMessage.tsx'

/**
 * Guards signed-in routes: signed out -> /login (and back afterwards); a temporary password must be changed
 * before anything else (ADR-0005); `role` limits a route to admins.
 */
export function RequireAuth({ children, role }: { children: ReactNode; role?: Role }) {
  const me = useMe()
  const location = useLocation()
  if (me.isPending) return <Loading />
  if (me.isError) return <ErrorMessage error={me.error} onRetry={() => void me.refetch()} />
  if (!me.data) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  if (me.data.mustChangePassword && location.pathname !== '/account') {
    return <Navigate to="/account" replace />
  }
  if (role && me.data.role !== role) return <Navigate to="/" replace />
  return children
}
