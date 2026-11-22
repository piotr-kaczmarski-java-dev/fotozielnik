import { expect, test } from '@playwright/test'
import { createUser, signIn, uploadPhotos, watchConsole } from './support.ts'

// FR-022: delete to the trash (from the grid and the photo page), undo, restore, empty for good
test('trash: delete, undo, restore, empty', async ({ page }) => {
  const errors = watchConsole(page)
  const user = await createUser({ ready: true })
  await uploadPhotos(user, 3)
  await signIn(page, user.email, user.password)
  const tiles = page.getByTestId('photo-grid').getByTestId('photo-tile')
  await expect(tiles).toHaveCount(3)

  // select two photos and move them to the trash, then undo
  await page.getByRole('button', { name: 'Zaznacz' }).click()
  await tiles.nth(0).click()
  await tiles.nth(1).click()
  await expect(tiles.nth(0)).toHaveAttribute('aria-pressed', 'true')
  const bar = page.getByRole('toolbar')
  await expect(bar).toContainText('Zaznaczono 2 zdjęcia')
  await bar.getByRole('button', { name: 'Przenieś do kosza' }).click()
  await expect(tiles).toHaveCount(1)
  await expect(page.getByRole('status')).toContainText('Przeniesiono 2 zdjęcia do kosza.')
  await page.getByRole('button', { name: 'Cofnij' }).click()
  await expect(tiles).toHaveCount(3)

  // again, then restore one of them from the trash page
  await page.getByRole('button', { name: 'Zaznacz' }).click()
  await tiles.nth(0).click()
  await tiles.nth(1).click()
  await page.getByRole('toolbar').getByRole('button', { name: 'Przenieś do kosza' }).click()
  await page.getByRole('link', { name: 'Kosz (2)' }).click()
  const trashTiles = page.getByTestId('trash-grid').getByTestId('photo-tile')
  await expect(trashTiles).toHaveCount(2)
  await expect(page.getByTestId('trash-grid')).toContainText('jeszcze 30 dni')
  await trashTiles.first().click()
  await page.getByRole('button', { name: 'Przywróć zaznaczone' }).click()
  await expect(page.getByRole('status')).toContainText('Przywrócono 1 zdjęcie do biblioteki.')
  await expect(trashTiles).toHaveCount(1)

  // delete from the photo page: back in the library with an undo offer
  await page.getByRole('link', { name: /Wróć do biblioteki/ }).click()
  await expect(tiles).toHaveCount(2)
  await tiles.first().click()
  await page.getByRole('button', { name: 'Przenieś do kosza' }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('status')).toContainText('Przeniesiono 1 zdjęcie do kosza.')
  await expect(tiles).toHaveCount(1)

  // empty the trash for good (confirmation dialog)
  await page.getByRole('link', { name: 'Kosz (2)' }).click()
  page.once('dialog', (d) => void d.accept())
  await page.getByRole('button', { name: 'Opróżnij kosz' }).click()
  await expect(page.getByRole('status')).toContainText('Kosz został opróżniony.')
  await expect(page.getByText('Kosz jest pusty.')).toBeVisible()
  await page.getByRole('link', { name: /Wróć do biblioteki/ }).click()
  await expect(tiles).toHaveCount(1)
  await expect(page.getByRole('link', { name: /Kosz \(/ })).toHaveCount(0)

  expect(errors).toEqual([])
})
