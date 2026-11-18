/** RFC 9457 problem detail, as returned by every Spring API error (see backend ApiExceptionHandler). */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
}

export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`)
    this.problem = problem
  }
}

/** Fetches JSON from the backend (same origin; Vite proxies /api in dev). Throws ApiError on non-2xx. */
export async function apiGet<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, { ...init, headers: { Accept: 'application/json', ...init?.headers } })
  if (!res.ok) {
    const isProblem = res.headers.get('content-type')?.includes('json')
    const problem: ProblemDetail = isProblem ? await res.json() : { status: res.status, title: res.statusText }
    throw new ApiError(problem)
  }
  return (await res.json()) as T
}
