import { expect, test } from '@playwright/test'
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createUser, signIn, watchConsole } from './support.ts'

// A real 32x24 JPEG. Bytes after the end-of-image marker are ignored by decoders but change the hash,
// so each file is a distinct photo.
const TINY_JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAYACADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDn4IunFacEXSooIunFacEXSvFqTPOjEkgi6VpwRdKjgi6VpQRdOK46kzojE5WCLpWnBF0oorSoyIo0oIulacEXTiiiuOo2bxR//9k=',
  'base64',
)
const jpeg = (n: number) => Buffer.concat([TINY_JPEG, Buffer.from(`photo-${n}`)])

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

  expect(errors.filter((e) => !e.includes('401'))).toEqual([])
})
