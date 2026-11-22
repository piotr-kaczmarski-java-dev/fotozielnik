// Helpers for e2e tests: create users through the admin API so every test starts from a fresh account.
import { type APIRequestContext, expect, type Page, request } from '@playwright/test'
import { createHash, randomUUID } from 'node:crypto'
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

// A real 32x24 JPEG. Bytes after the end-of-image marker are ignored by decoders but change the hash,
// so each file is a distinct photo.
export const TINY_JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAYACADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDn4IunFacEXSooIunFacEXSvFqTPOjEkgi6VpwRdKjgi6VpQRdOK46kzojE5WCLpWnBF0oorSoyIo0oIulacEXTiiiuOo2bxR//9k=',
  'base64',
)
export const jpeg = (n: number) => Buffer.concat([TINY_JPEG, Buffer.from(`photo-${n}`)])

/** Uploads n distinct photos through the API (faster than the import page) and returns their ids. */
export async function uploadPhotos(user: TestUser, n: number, seed = Date.now()): Promise<string[]> {
  const session = await apiSession(user.email, user.password)
  const batch = await session.post('/api/imports', {
    data: { label: 'e2e', filesFound: n, filesSkipped: 0 },
    headers: await session.csrf(),
  })
  const batchId = (await batch.json()).id as string
  const ids: string[] = []
  for (let i = 0; i < n; i++) {
    const bytes = jpeg(seed + i)
    const sha = createHash('sha256').update(bytes).digest('hex')
    const r = await session.put(`/api/imports/${batchId}/files/${sha}`, {
      data: bytes,
      headers: { ...(await session.csrf()), 'Content-Type': 'application/octet-stream', 'X-File-Path': `e2e/IMG_${i}.jpg` },
    })
    expect(r.status(), await r.text()).toBe(201)
    ids.push((await r.json()).photoId as string)
  }
  await session.dispose()
  return ids
}
