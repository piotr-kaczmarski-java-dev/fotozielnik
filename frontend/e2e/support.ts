// Helpers for e2e tests: create users through the admin API so every test starts from a fresh account.
import { type APIRequestContext, expect, type Page, request } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { E2E_ADMIN, E2E_BACKEND } from '../playwright.config.ts'

/** A signed-in API session (cookies + CSRF header) - like a second browser. */
export async function apiSession(email: string, password: string): Promise<APIRequestContext & { csrf: () => Promise<Record<string, string>> }> {
  const ctx = await request.newContext({ baseURL: E2E_BACKEND })
  const csrf = async () => {
    let token = (await ctx.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value
    if (!token) {
      await ctx.get('/api/auth/csrf')
      token = (await ctx.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value
    }
    return { 'X-XSRF-TOKEN': token ?? '' }
  }
  const login = await ctx.post('/api/auth/login', { data: { email, password }, headers: await csrf() })
  expect(login.status(), await login.text()).toBe(200)
  return Object.assign(ctx, { csrf })
}

export interface TestUser {
  email: string
  password: string
  displayName: string
}

/** A new user (USER by default) with a temporary password; `ready: true` also sets a permanent one (skips the forced change). */
export async function createUser({ ready = false, role = 'USER' as 'USER' | 'ADMIN' } = {}): Promise<TestUser> {
  const admin = await apiSession(E2E_ADMIN.email, E2E_ADMIN.password)
  const user = { email: `e2e-${randomUUID()}@fotozielnik.test`, password: 'tymczasowe-1', displayName: 'Anna Testowa' }
  const created = await admin.post('/api/admin/users', {
    data: { email: user.email, displayName: user.displayName, role, temporaryPassword: user.password },
    headers: await admin.csrf(),
  })
  expect(created.status(), await created.text()).toBe(201)
  await admin.dispose()
  if (ready) {
    const session = await apiSession(user.email, user.password)
    const changed = await session.put('/api/me/password', {
      data: { currentPassword: user.password, newPassword: 'moje-haslo-123' },
      headers: await session.csrf(),
    })
    expect(changed.status()).toBe(204)
    await session.dispose()
    user.password = 'moje-haslo-123'
  }
  return user
}

export async function signIn(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Adres e-mail').fill(email)
  await page.getByLabel('Hasło').fill(password)
  await page.getByRole('button', { name: 'Zaloguj się' }).click()
}

/** Fails the test on console errors and uncaught exceptions. */
export function watchConsole(page: Page): string[] {
  const errors: string[] = []
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
  page.on('pageerror', (e) => errors.push(e.message))
  return errors
}
