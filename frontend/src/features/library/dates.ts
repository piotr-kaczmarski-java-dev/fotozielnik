import type { PhotoSummary } from '../../api/types.ts'

/**
 * The camera's wall-clock time ("2024-05-12T10:31:07", no zone) as a Date in the browser's zone with the same
 * fields - so a photo taken at 10:31 shows 10:31 wherever the viewer is.
 */
export function wallClock(local: string): Date {
  const [date = '', time = '00:00:00'] = local.split('T')
  const [y, mo, d] = date.split('-').map(Number)
  const [h, mi, s] = time.split(':').map((v) => Math.floor(Number(v)))
  return new Date(y ?? 1970, (mo ?? 1) - 1, d ?? 1, h ?? 0, mi ?? 0, s ?? 0)
}

/** When the photo was taken if known, else when the file was saved/uploaded (the grid's sort time). */
export function photoDate(p: { takenAtLocal?: string | null; sortTime: string }): { date: Date; known: boolean } {
  if (p.takenAtLocal != null) return { date: wallClock(p.takenAtLocal), known: true }
  return { date: new Date(p.sortTime), known: false }
}

const monthFormat = new Intl.DateTimeFormat('pl-PL', { month: 'long', year: 'numeric' })
const dayFormat = new Intl.DateTimeFormat('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' })
const dateTimeFormat = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'long', timeStyle: 'short' })

export const formatMonth = (d: Date) => {
  const s = monthFormat.format(d)
  return s.charAt(0).toUpperCase() + s.slice(1)
}
export const formatDay = (d: Date) => dayFormat.format(d)
export const formatDateTime = (d: Date) => dateTimeFormat.format(d)

export interface MonthGroup {
  key: string
  label: string
  photos: PhotoSummary[]
}

/** Consecutive photos (already newest first) grouped under month headings. */
export function groupByMonth(photos: PhotoSummary[]): MonthGroup[] {
  const groups: MonthGroup[] = []
  for (const p of photos) {
    const { date } = photoDate(p)
    const key = `${date.getFullYear()}-${date.getMonth()}`
    const last = groups.at(-1)
    if (last && last.key === key) last.photos.push(p)
    else groups.push({ key, label: formatMonth(date), photos: [p] })
  }
  return groups
}

const bytes = (unit: 'kilobyte' | 'megabyte', digits: number) =>
  new Intl.NumberFormat('pl-PL', { style: 'unit', unit, maximumFractionDigits: digits })

/** "850 kB", "12,4 MB" (Polish decimal comma). */
export function formatBytes(n: number): string {
  return n < 1_000_000 ? bytes('kilobyte', 0).format(n / 1000) : bytes('megabyte', 1).format(n / 1_000_000)
}
