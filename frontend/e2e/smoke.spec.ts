import { expect, test } from '@playwright/test'
import { watchConsole } from './support.ts'

test('the app loads and shows the sign-in page, without console errors', async ({ page, request }) => {
  const errors = watchConsole(page)
  const info = await request.get('/api/public/info')
  expect(info.ok()).toBe(true)

  await page.goto('/')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Logowanie' })).toBeVisible()
  expect(errors).toEqual([])
})
