import { expect, test } from '@playwright/test'
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createUser, jpeg, signIn, watchConsole } from './support.ts'

/** Łąka/{IMG_1.jpg, IMG_2.JPG, wiosna/IMG_3.jpeg, notatki.txt} */
function makeFolder(): string {
  const root = mkdtempSync(join(tmpdir(), 'fz-e2e-'))
  const folder = join(root, 'Łąka')
  mkdirSync(join(folder, 'wiosna'), { recursive: true })
  writeFileSync(join(folder, 'IMG_1.jpg'), jpeg(1))
  writeFileSync(join(folder, 'IMG_2.JPG'), jpeg(2))
  writeFileSync(join(folder, 'wiosna', 'IMG_3.jpeg'), jpeg(3))
  writeFileSync(join(folder, 'notatki.txt'), 'nie zdjęcie')
  return folder
}

// UC-01: FR-004 (folder, only JPEGs), FR-005/FR-006 (second import uploads nothing), FR-008 (progress), FR-009 (grid)
test('import a folder, see the photos, import it again without uploading anything', async ({ page }) => {
  const errors = watchConsole(page)
  const user = await createUser({ ready: true })
  const folder = makeFolder()
  await signIn(page, user.email, user.password)
  await expect(page.getByText('Nie masz jeszcze zdjęć.')).toBeVisible()

  await page.getByRole('link', { name: 'Dodaj zdjęcia' }).first().click()
  await page.getByTestId('folder-input').setInputFiles(folder)
  await expect(page.getByTestId('found')).toHaveText('Znaleziono 3 zdjęć JPEG w folderze „Łąka”.')
  await expect(page.getByTestId('skipped')).toContainText('1 plik nie jest zdjęciem JPEG')

  await page.getByRole('button', { name: 'Rozpocznij dodawanie' }).click()
  const progress = page.getByTestId('import-progress')
  await expect(progress.getByRole('heading', { name: 'Gotowe!' })).toBeVisible()
  await expect(progress).toContainText('Przesłano 3 z 3 nowych zdjęć')
  await expect(page.getByTestId('processing-done')).toBeVisible({ timeout: 30_000 })

  await page.getByRole('link', { name: 'Przejdź do biblioteki' }).click()
  const tiles = page.getByTestId('photo-tile')
  await expect(tiles).toHaveCount(3)
  await expect(tiles.locator('img')).toHaveCount(3) // thumbnails are ready
  await tiles.first().click()
  await expect(page.getByText('Łąka/')).toBeVisible() // original path on the photo page
  await expect(page.locator('figure.preview img')).toBeVisible()

  // choosing the same folder again: everything is already in the library
  await page.getByRole('link', { name: 'Dodaj zdjęcia' }).click()
  await page.getByTestId('folder-input').setInputFiles(folder)
  await page.getByRole('button', { name: 'Rozpocznij dodawanie' }).click()
  await expect(progress.getByRole('heading', { name: 'Gotowe!' })).toBeVisible()
  await expect(progress).toContainText('Pominięto 3')
  await expect(progress).not.toContainText('Przesłano')

  expect(errors).toEqual([])
})
