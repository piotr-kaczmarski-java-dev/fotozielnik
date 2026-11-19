import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { PhotoSummary } from '../../api/types.ts'
import { formatDay, photoDate } from './dates.ts'

/**
 * One grid cell. Normally a link to the photo; in selection mode a toggle button (aria-pressed) with a big
 * check mark, so older users see clearly what is selected.
 */
export function PhotoTile({
  photo,
  selecting = false,
  selected = false,
  onToggle,
  badge,
}: {
  photo: PhotoSummary
  selecting?: boolean
  selected?: boolean
  onToggle?: (id: string) => void
  badge?: ReactNode
}) {
  const { t } = useTranslation()
  const { date, known } = photoDate(photo)
  const label = known
    ? t('library.photoAlt', { date: formatDay(date) })
    : t('library.photoAltNoDate', { name: formatDay(date) })
  const content = (
    <>
      {photo.thumbnailReady ? (
        <img src={`/api/photos/${photo.id}/thumb`} alt="" loading="lazy" decoding="async" />
      ) : (
        <span className={`tile-placeholder ${photo.processingState === 'FAILED' ? 'failed' : ''}`}>
          {photo.processingState === 'FAILED' ? t('library.failed') : t('library.processing')}
        </span>
      )}
      {selecting && (
        <span className="tile-check" aria-hidden="true">
          {selected ? '✓' : ''}
        </span>
      )}
      {badge && <span className="tile-badge">{badge}</span>}
    </>
  )
  if (selecting) {
    return (
      <button
        type="button"
        className={`tile selectable ${selected ? 'selected' : ''}`}
        aria-label={label}
        aria-pressed={selected}
        data-testid="photo-tile"
        onClick={() => onToggle?.(photo.id)}
      >
        {content}
      </button>
    )
  }
  return (
    <Link to={`/photos/${photo.id}`} className="tile" aria-label={label} data-testid="photo-tile">
      {content}
    </Link>
  )
}
