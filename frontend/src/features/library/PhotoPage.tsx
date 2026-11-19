import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import { apiGet } from '../../api/client.ts'
import { keys } from '../../api/queries.ts'
import type { PhotoDetails, TrashInfo } from '../../api/types.ts'
import { ErrorMessage, Loading } from '../../ui/ErrorMessage.tsx'
import { formatBytes, formatDateTime, formatDay, wallClock } from './dates.ts'
import type { TrashedState } from './LibraryPage.tsx'
import { useTrashMutation } from './trash.ts'

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
  const navigate = useNavigate()
  const trash = useTrashMutation('trash')
  const restore = useTrashMutation('restore')
  const trashInfo = useQuery({ queryKey: keys.trashInfo, queryFn: () => apiGet<TrashInfo>('/api/photos/trash/info') })
  const purgeDate =
    photo.deletedAt != null && trashInfo.data
      ? formatDay(new Date(new Date(photo.deletedAt).getTime() + trashInfo.data.retentionDays * 86_400_000))
      : null
  const name = photo.originalPath.split('/').at(-1) ?? photo.originalPath
  const taken = photo.takenAtLocal != null ? formatDateTime(wallClock(photo.takenAtLocal)) : null
  const camera = [photo.cameraMake, photo.cameraModel].filter((v) => v != null).join(' ')
  const loc = photo.location
  return (
    <>
      <h1 className="visually-hidden">{taken ?? name}</h1>
      {photo.deletedAt != null && (
        <div role="status" className="alert alert-info notice">
          <span>{t('photo.inTrash', { date: purgeDate ?? '…' })}</span>
          <button
            type="button"
            className="button secondary"
            disabled={restore.isPending}
            onClick={() => restore.mutate([photo.id])}
          >
            {t('photo.restore')}
          </button>
        </div>
      )}
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
      <div className="actions">
        <a className="button secondary" href={`/api/photos/${photo.id}/original`} download={name}>
          {t('photo.original')}
        </a>
        {photo.deletedAt == null && (
          <button
            type="button"
            className="button danger"
            disabled={trash.isPending}
            onClick={() =>
              trash.mutate([photo.id], {
                onSuccess: () => void navigate('/', { state: { trashed: [photo.id] } satisfies TrashedState }),
              })
            }
          >
            {t('photo.toTrash')}
          </button>
        )}
      </div>
      {(trash.isError || restore.isError) && <ErrorMessage error={trash.error ?? restore.error} />}
    </>
  )
}
