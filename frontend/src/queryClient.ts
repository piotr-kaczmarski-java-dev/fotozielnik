import { QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client.ts'
import { keys } from './api/queries.ts'

export function createQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    // A 401 anywhere means the session ended (expired, password reset by the admin): show the login page.
    queryCache: new QueryCache({
      onError: (error) => {
        if (error instanceof ApiError && error.status === 401) client.setQueryData(keys.me, null)
      },
    }),
    defaultOptions: {
      queries: {
        retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
        refetchOnWindowFocus: false,
      },
    },
  })
  return client
}
