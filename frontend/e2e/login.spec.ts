import { expect, test } from '@playwright/test'
import { createUser, signIn } from './support.ts'

// FR-001 (sign in/out, Polish error), FR-002 (temporary password must be changed at first sign-in)
test('first sign-in with a temporary password, then sign out and in with the new one', async ({ page }) => {
  const user = await createUser()

  await signIn(page, user.email, 'zle-haslo-123')
  await expect(page.getByRole('alert')).toHaveText('Nieprawidłowy adres e-mail lub hasło.')

  await signIn(page, user.email, user.password)
  await expect(page).toHaveURL(/\/account$/)
  await expect(page.getByText('To jest hasło tymczasowe')).toBeVisible()
  await page.getByLabel('Obecne hasło').fill(user.password)
  await page.getByLabel('Nowe hasło', { exact: true }).fill('nowe-haslo-456')
  await page.getByLabel('Powtórz nowe hasło').fill('nowe-haslo-456')
  await page.getByRole('button', { name: 'Zmień hasło' }).click()

  await expect(page.getByText('Nie masz jeszcze zdjęć.')).toBeVisible()
  await page.getByRole('button', { name: 'Wyloguj' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await signIn(page, user.email, 'nowe-haslo-456')
  await expect(page.getByRole('heading', { name: 'Biblioteka' })).toBeVisible()
})
