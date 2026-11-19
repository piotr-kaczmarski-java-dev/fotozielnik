import { useInfiniteQuery } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { apiGet } from '../../api/client.ts'
import { keys } from '../../api/queries.ts'
import type { PhotoPage, PhotoSummary } from '../../api/types.ts'
import { ErrorMessage, Loading } from '../../ui/ErrorMessage.tsx'
import { formatDay, groupByMonth, photoDate } from './dates.ts'

const PAGE_SIZE = 60

/** Date-ordered grid of the user's photos (FR-009), newest first, loading more on scroll. */
export function LibraryPage() {
  const { t } = useTranslation()
  const photos = useInfiniteQuery({
    queryKey: keys.photos,
    queryFn: ({ pageParam }) =>
      apiGet<PhotoPage>(`/api/photos?size=${PAGE_SIZE}${pageParam ? `&cursor=${encodeURIComponent(pageParam)}` : ''}`),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    // while thumbnails are still being made, look again now and then
    refetchInterval: (query) =>
      query.state.data?.pages.some((p) => p.items.some((i) => i.processingState === 'PENDING')) ? 5000 : false,
  })

  const sentinel = useRef<HTMLDivElement>(null)
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = photos
  useEffect(() => {
    const el = sentinel.current
    if (!el || !hasNextPage) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting) && !isFetchingNextPage) void fetchNextPage()
      },
      { rootMargin: '600px' },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  // a failed "load more" keeps the photos already shown; only a failed first page replaces the grid
  if (!photos.data) {
    return photos.isError ? <ErrorMessage error={photos.error} onRetry={() => void photos.refetch()} /> : <Loading />
  }

  const all = photos.data.pages.flatMap((p) => p.items)
  if (all.length === 0) {
    return (
      <section className="empty-state">
        <h1>{t('library.title')}</h1>
        <p>{t('library.empty')}</p>
        <Link className="button primary" to="/import">
          {t('library.emptyAction')}
        </Link>
      </section>
    )
  }

  return (
    <section>
      <h1>{t('library.title')}</h1>
      {groupByMonth(all).map((group) => (
        <section key={group.key} aria-labelledby={`month-${group.key}`}>
          <h2 id={`month-${group.key}`} className="month-heading">
            {group.label}
          </h2>
          <ul className="photo-grid" data-testid="photo-grid">
            {group.photos.map((p) => (
              <li key={p.id}>
                <PhotoTile photo={p} />
              </li>
            ))}
          </ul>
        </section>
      ))}
      <div ref={sentinel} />
      {photos.hasNextPage && (
        <button
          type="button"
          className="button secondary more"
          onClick={() => void photos.fetchNextPage()}
          disabled={photos.isFetchingNextPage}
        >
          {photos.isFetchingNextPage ? t('common.loading') : t('library.more')}
        </button>
      )}
      {photos.isFetchNextPageError && (
        <ErrorMessage error={photos.error} onRetry={() => void photos.fetchNextPage()} />
      )}
    </section>
  )
}

function PhotoTile({ photo }: { photo: PhotoSummary }) {
  const { t } = useTranslation()
  const { date, known } = photoDate(photo)
  const label = known ? t('library.photoAlt', { date: formatDay(date) }) : t('library.photoAltNoDate', { name: formatDay(date) })
  return (
    <Link to={`/photos/${photo.id}`} className="tile" aria-label={label} data-testid="photo-tile">
      {photo.thumbnailReady ? (
        <img src={`/api/photos/${photo.id}/thumb`} alt="" loading="lazy" decoding="async" />
      ) : (
        <span className={`tile-placeholder ${photo.processingState === 'FAILED' ? 'failed' : ''}`}>
          {photo.processingState === 'FAILED' ? t('library.failed') : t('library.processing')}
        </span>
      )}
    </Link>
  )
}
