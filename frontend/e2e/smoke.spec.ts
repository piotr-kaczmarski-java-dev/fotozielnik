import { expect, test } from '@playwright/test'

test('home page shows the backend is up, without console errors', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
  page.on('pageerror', (e) => errors.push(e.message))

  await page.goto('/')
  await expect(page.getByTestId('backend-info')).toContainText('is up')
  expect(errors).toEqual([])
})
