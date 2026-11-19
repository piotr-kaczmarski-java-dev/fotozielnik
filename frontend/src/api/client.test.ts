import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiGet, apiSend } from './client.ts'

function clearCookies() {
  for (const c of document.cookie.split(';')) {
    const name = c.split('=')[0]?.trim()
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
}

describe('api client', () => {
  beforeEach(clearCookies)
  afterEach(() => vi.unstubAllGlobals())

  it('sends the CSRF cookie value as header on unsafe requests', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
    const fetchMock = vi.fn(async () => Response.json({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await apiSend('POST', '/api/auth/login', { email: 'a@b.pl', password: 'x' })

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    const headers = init.headers as Record<string, string>
    expect(headers['X-XSRF-TOKEN']).toBe('abc123')
    expect(headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"email":"a@b.pl","password":"x"}')
  })

  it('fetches the CSRF cookie first when it is missing', async () => {
    const fetchMock = vi.fn(async (url: string, _init?: RequestInit) => {
      if (url === '/api/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=fresh; path=/'
        return new Response(null, { status: 204 })
      }
      return new Response(null, { status: 204 })
    })
    vi.stubGlobal('fetch', fetchMock)

    await apiSend('POST', '/api/auth/logout')

    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/auth/csrf', '/api/auth/logout'])
    const init = fetchMock.mock.calls[1]?.[1] as RequestInit
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('fresh')
  })

  it('sends blobs raw, without a JSON content type', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    const fetchMock = vi.fn(
      async (_url: string, _init?: RequestInit) => Response.json({ photoId: '1', created: true }, { status: 201 }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const file = new Blob([new Uint8Array([0xff, 0xd8, 0xff])])

    await apiSend('PUT', '/api/imports/1/files/abc', file, { 'Content-Type': 'application/octet-stream' })

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(init.body).toBe(file)
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/octet-stream')
  })

  it('turns a ProblemDetail into an ApiError with its code', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        Response.json(
          { status: 401, detail: 'Wrong email or password.', code: 'auth.bad-credentials' },
          { status: 401, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    )
    const error = await apiGet('/api/me').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect((error as ApiError).code).toBe('auth.bad-credentials')
  })
})
