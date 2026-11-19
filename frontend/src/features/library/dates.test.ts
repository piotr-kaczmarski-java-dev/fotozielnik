import { describe, expect, it } from 'vitest'
import type { PhotoSummary } from '../../api/types.ts'
import { formatBytes, groupByMonth, photoDate, wallClock } from './dates.ts'

const photo = (id: string, takenAtLocal: string | null, sortTime = '2024-01-01T00:00:00Z') =>
  ({ id, takenAtLocal, sortTime, processingState: 'DONE', thumbnailReady: true }) as PhotoSummary

describe('dates', () => {
  it('keeps the camera wall-clock time regardless of the viewer zone', () => {
    const d = wallClock('2024-05-12T10:31:07')
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()]).toEqual([2024, 4, 12, 10, 31])
  })

  it('falls back to the sort time when the capture time is unknown', () => {
    expect(photoDate(photo('a', null, '2023-03-03T12:00:00Z')).known).toBe(false)
    expect(photoDate(photo('a', '2023-03-03T12:00:00')).known).toBe(true)
  })

  it('groups consecutive photos by month with Polish labels', () => {
    const groups = groupByMonth([
      photo('1', '2024-05-12T10:00:00'),
      photo('2', '2024-05-01T08:00:00'),
      photo('3', '2024-04-30T23:00:00'),
    ])
    expect(groups.map((g) => [g.label, g.photos.length])).toEqual([
      ['Maj 2024', 2],
      ['Kwiecień 2024', 1],
    ])
  })

  it('formats file sizes the Polish way', () => {
    expect(formatBytes(850_000).replace(/\s/g, ' ')).toBe('850 kB')
    expect(formatBytes(12_400_000).replace(/\s/g, ' ')).toBe('12,4 MB')
  })
})
