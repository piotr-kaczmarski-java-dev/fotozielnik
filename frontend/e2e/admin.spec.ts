import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { createUser, signIn } from './support.ts'

// UC-05 / FR-002: an admin creates an account and hands over the temporary password; NFR-006: deletion
test('admin creates a user who can sign in, then deletes the account', async ({ page, browser }) => {
  const admin = await createUser({ ready: true, role: 'ADMIN' })
  await signIn(page, admin.email, admin.password)
  await page.getByRole('link', { name: 'Użytkownicy' }).click()

  const email = `nowa-${randomUUID()}@fotozielnik.test`
  await page.getByText('Dodaj użytkownika').first().click()
  await page.getByLabel('Imię i nazwisko').fill('Zofia Nowak')
  await page.getByLabel('Adres e-mail').fill(email)
  const temporary = await page.getByLabel('Hasło tymczasowe').inputValue()
  expect(temporary.length).toBeGreaterThanOrEqual(8)
  await page.getByRole('button', { name: 'Dodaj użytkownika' }).click()
  await expect(page.getByRole('status')).toContainText(temporary) // shown to hand over in person
  const row = page.getByRole('row', { name: /Zofia Nowak/ })
  await expect(row).toContainText('czeka na zmianę hasła')

  // the new user can sign in with it (and is asked to change it)
  const other = await browser.newPage()
  await signIn(other, email, temporary)
  await expect(other.getByText('To jest hasło tymczasowe')).toBeVisible()

  // answers for the browser dialogs, in order: a wrong email, the "did not match" alert, the right email
  const answers = ['ktos-inny@x.pl', '', email]
  page.on('dialog', (d) => void d.accept(answers.shift()))

  await row.getByRole('button', { name: 'Usuń' }).click()
  await expect(row).toBeVisible()
  await expect.poll(() => answers.length).toBe(1)

  await row.getByRole('button', { name: 'Usuń' }).click()
  await expect(page.getByRole('status')).toContainText(`Usunięto konto ${email}`)
  await expect(page.getByRole('row', { name: /Zofia Nowak/ })).toHaveCount(0)

  // the deleted user's session is gone
  await other.reload()
  await expect(other).toHaveURL(/\/login$/)
})
