/** RFC 9457 problem detail, as returned by every Spring API error (see backend ApiExceptionHandler). */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  /** Stable machine-readable error key (e.g. `auth.bad-credentials`) for translated messages. */
  code?: string
}

export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`)
    this.problem = problem
  }

  get status(): number {
    return this.problem.status
  }

  get code(): string | undefined {
    return this.problem.code
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) return decodeURIComponent(trimmed.slice(prefix.length))
  }
  return undefined
}

/**
 * The CSRF token the backend expects on unsafe requests (ADR-0005: cookie session + double-submit token).
 * Normally any earlier response has set the cookie; if not, ask for it once.
 */
async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) return existing
  await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  return readCookie(CSRF_COOKIE)
}

async function toApiError(res: Response): Promise<ApiError> {
  const isProblem = res.headers.get('content-type')?.includes('json')
  const problem: ProblemDetail = isProblem
    ? ((await res.json()) as ProblemDetail)
    : { status: res.status, title: res.statusText }
  return new ApiError(problem)
}

/** Fetches JSON from the backend (same origin; Vite proxies /api in dev). Throws ApiError on non-2xx. */
export async function apiGet<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
  })
  if (!res.ok) throw await toApiError(res)
  return (await res.json()) as T
}

type UnsafeMethod = 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/**
 * Sends an unsafe request with the CSRF header. A plain object body is sent as JSON; a Blob/File/ArrayBuffer
 * is sent raw (uploads). Resolves to the parsed JSON body, or undefined for 204 No Content.
 */
export async function apiSend<T = undefined>(
  method: UnsafeMethod,
  path: string,
  body?: unknown,
  headers?: Record<string, string>,
): Promise<T> {
  const raw = body instanceof Blob || body instanceof ArrayBuffer
  const token = await csrfToken()
  const res = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...(body !== undefined && !raw ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { [CSRF_HEADER]: token } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : raw ? (body as BodyInit) : JSON.stringify(body),
  })
  if (!res.ok) throw await toApiError(res)
  if (res.status === 204 || res.headers.get('content-length') === '0') return undefined as T
  return (await res.json()) as T
}
