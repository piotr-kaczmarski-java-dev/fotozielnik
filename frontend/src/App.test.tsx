import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import './i18n/index.ts'
import { createQueryClient } from './queryClient.ts'
import { routes } from './routes.tsx'

type Handler = (init?: RequestInit) => Response
const json = (body: unknown, status = 200) =>
  Response.json(body, { status, headers: { 'content-type': status >= 400 ? 'application/problem+json' : 'application/json' } })

function serve(handlers: Record<string, Handler>) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const key = `${init?.method ?? 'GET'} ${url.split('?')[0]}`
      const handler = handlers[key]
      if (!handler) throw new Error(`unexpected request ${key}`)
      return handler(init)
    }),
  )
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={createQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

const me = { id: 'u1', email: 'jan@x.pl', displayName: 'Jan', locale: 'pl', role: 'USER', mustChangePassword: false }

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('sends signed-out users to the login page and shows a Polish error for a wrong password', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    serve({
      'GET /api/me': () => json({ status: 401 }, 401),
      'POST /api/auth/login': () =>
        json({ status: 401, code: 'auth.bad-credentials', detail: 'Wrong email or password.' }, 401),
    })
    const router = renderAt('/')

    expect(await screen.findByRole('heading', { name: 'Logowanie' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/login')

    fireEvent.change(screen.getByLabelText('Adres e-mail'), { target: { value: 'jan@x.pl' } })
    fireEvent.change(screen.getByLabelText('Hasło'), { target: { value: 'zle-haslo' } })
    fireEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }))

    expect((await screen.findByRole('alert')).textContent).toContain('Nieprawidłowy adres e-mail lub hasło.')
  })

  it('forces a temporary password to be changed before anything else', async () => {
    serve({ 'GET /api/me': () => json({ ...me, mustChangePassword: true }) })
    const router = renderAt('/import')

    expect(await screen.findByText(/To jest hasło tymczasowe/)).toBeTruthy()
    expect(router.state.location.pathname).toBe('/account')
    expect(screen.queryByRole('navigation')).toBeNull()
  })

  it('shows an empty library with one clear next step', async () => {
    serve({
      'GET /api/me': () => json(me),
      'GET /api/photos': () => json({ items: [], nextCursor: null }),
    })
    renderAt('/')

    expect(await screen.findByText('Nie masz jeszcze zdjęć.')).toBeTruthy()
    expect(screen.getAllByRole('link', { name: 'Dodaj zdjęcia' }).length).toBeGreaterThan(0)
    expect(screen.queryByRole('link', { name: 'Użytkownicy' })).toBeNull() // not an admin
  })

  it('keeps non-admins out of the admin page', async () => {
    serve({
      'GET /api/me': () => json(me),
      'GET /api/photos': () => json({ items: [], nextCursor: null }),
    })
    const router = renderAt('/admin/users')
    await screen.findByText('Nie masz jeszcze zdjęć.')
    expect(router.state.location.pathname).toBe('/')
  })
})
