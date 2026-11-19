import { expect, test } from '@playwright/test'
import { createUser, signIn } from './support.ts'

// NFR-008: dark by default; the choice on "Moje konto" is remembered in the browser
test('dark by default, light after choosing it - also after a reload', async ({ page }) => {
  await page.goto('/login')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(18, 24, 19)')

  const user = await createUser({ ready: true })
  await signIn(page, user.email, user.password)
  await page.getByRole('link', { name: 'Moje konto' }).click()
  await page.getByRole('radio', { name: 'Jasny' }).check()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')

  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await expect(page.getByRole('radio', { name: 'Jasny' })).toBeChecked()
  await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(246, 248, 244)')
})
