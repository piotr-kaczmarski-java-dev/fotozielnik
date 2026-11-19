import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { apiGet } from '../../api/client.ts'
import { keys } from '../../api/queries.ts'
import type { PhotoDetails } from '../../api/types.ts'
import { ErrorMessage, Loading } from '../../ui/ErrorMessage.tsx'
import { formatBytes, formatDateTime, wallClock } from './dates.ts'

/** Large preview + what we know about one photo (FR-009). */
export function PhotoPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const photo = useQuery({
    queryKey: keys.photo(id),
    queryFn: () => apiGet<PhotoDetails>(`/api/photos/${encodeURIComponent(id)}`),
    refetchInterval: (q) => (q.state.data?.processingState === 'PENDING' ? 3000 : false),
  })

  return (
    <article className="photo-page">
      <Link to="/" className="button link back">
        ← {t('photo.back')}
      </Link>
      {photo.isPending && <Loading />}
      {photo.isError && <ErrorMessage error={photo.error} onRetry={() => void photo.refetch()} />}
      {photo.data && <PhotoView photo={photo.data} />}
    </article>
  )
}

function PhotoView({ photo }: { photo: PhotoDetails }) {
  const { t } = useTranslation()
  const name = photo.originalPath.split('/').at(-1) ?? photo.originalPath
  const taken = photo.takenAtLocal != null ? formatDateTime(wallClock(photo.takenAtLocal)) : null
  const camera = [photo.cameraMake, photo.cameraModel].filter((v) => v != null).join(' ')
  const loc = photo.location
  return (
    <>
      <h1 className="visually-hidden">{taken ?? name}</h1>
      <figure className="preview">
        {photo.thumbnailReady ? (
          <img src={`/api/photos/${photo.id}/preview`} alt={taken ? t('library.photoAlt', { date: taken }) : name} />
        ) : photo.processingState === 'FAILED' ? (
          <p className="alert alert-error">{t('photo.failed', { reason: photo.processingError ?? '' })}</p>
        ) : (
          <p className="alert alert-info" role="status">
            {t('photo.processing')}
          </p>
        )}
      </figure>
      <dl className="details">
        <dt>{t('photo.taken')}</dt>
        <dd>{taken ?? t('library.noDate')}</dd>
        {camera && (
          <>
            <dt>{t('photo.camera')}</dt>
            <dd>{camera}</dd>
          </>
        )}
        {photo.lens != null && (
          <>
            <dt>{t('photo.lens')}</dt>
            <dd>{photo.lens}</dd>
          </>
        )}
        <dt>{t('photo.location')}</dt>
        <dd>
          {loc != null ? (
            <>
              {loc.latitude.toFixed(5)}, {loc.longitude.toFixed(5)}
              {loc.source === 'INFERRED' && ` (${t('photo.locationInferred')})`}{' '}
              <a
                href={`https://www.openstreetmap.org/?mlat=${loc.latitude}&mlon=${loc.longitude}#map=15/${loc.latitude}/${loc.longitude}`}
                target="_blank"
                rel="noreferrer"
              >
                {t('photo.showOnMap')}
              </a>
            </>
          ) : (
            t('photo.noLocation')
          )}
        </dd>
        {photo.width != null && photo.height != null && (
          <>
            <dt>{t('photo.size')}</dt>
            <dd>
              {t('photo.megapixels', { w: photo.width, h: photo.height })}, {formatBytes(photo.sizeBytes)}
            </dd>
          </>
        )}
        <dt>{t('photo.file')}</dt>
        <dd className="path">{photo.originalPath}</dd>
      </dl>
      <a className="button secondary" href={`/api/photos/${photo.id}/original`} download={name}>
        {t('photo.original')}
      </a>
    </>
  )
}
