import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { apiGet } from '../../api/client.ts'
import { keys } from '../../api/queries.ts'
import type { PhotoPage, TrashInfo } from '../../api/types.ts'
import { ErrorMessage, Loading } from '../../ui/ErrorMessage.tsx'
import { groupByMonth } from './dates.ts'
import { PhotoTile } from './PhotoTile.tsx'
import { useTrashMutation } from './trash.ts'

const PAGE_SIZE = 60

/** Navigation state from the photo page after "Przenieś do kosza", so the library can offer "Cofnij". */
export interface TrashedState {
  trashed?: string[]
}

/** Date-ordered grid of the user's photos (FR-009), newest first, loading more on scroll; selection -> trash. */
export function LibraryPage() {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const [selecting, setSelecting] = useState(false)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [undoIds, setUndoIds] = useState<string[]>(() => (location.state as TrashedState | null)?.trashed ?? [])
  const trash = useTrashMutation('trash')
  const restore = useTrashMutation('restore')

  useEffect(() => {
    // the undo offer came via navigation state: drop it from history so a reload does not show it again
    // (not navigate('.'): on an index route that adds "?index" to the address)
    if ((location.state as TrashedState | null)?.trashed) {
      void navigate(location.pathname + location.search, { replace: true, state: null })
    }
  }, [location.state, location.pathname, location.search, navigate])

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
  const trashInfo = useQuery({ queryKey: keys.trashInfo, queryFn: () => apiGet<TrashInfo>('/api/photos/trash/info') })

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

  function toggle(id: string) {
    setSelected((s) => {
      const next = new Set(s)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function stopSelecting() {
    setSelecting(false)
    setSelected(new Set())
  }

  function moveSelectedToTrash() {
    const ids = [...selected]
    trash.mutate(ids, {
      onSuccess: () => {
        setUndoIds(ids)
        stopSelecting()
      },
    })
  }

  // a failed "load more" keeps the photos already shown; only a failed first page replaces the grid
  if (!photos.data) {
    return photos.isError ? <ErrorMessage error={photos.error} onRetry={() => void photos.refetch()} /> : <Loading />
  }

  const all = photos.data.pages.flatMap((p) => p.items)
  const inTrash = trashInfo.data?.count ?? 0

  return (
    <section className={selecting ? 'with-selection-bar' : undefined}>
      <div className="page-header">
        <h1>{t('library.title')}</h1>
        <div className="actions">
          {all.length > 0 && !selecting && (
            <button type="button" className="button secondary" onClick={() => setSelecting(true)}>
              {t('library.select')}
            </button>
          )}
          {inTrash > 0 && (
            <Link to="/trash" className="button link">
              {t('library.trashLink', { count: inTrash })}
            </Link>
          )}
        </div>
      </div>

      {undoIds.length > 0 && (
        <div role="status" className="alert alert-info notice">
          <span>{t('library.trashed', { count: undoIds.length })}</span>
          <button
            type="button"
            className="button secondary"
            disabled={restore.isPending}
            onClick={() => restore.mutate(undoIds, { onSuccess: () => setUndoIds([]) })}
          >
            {t('library.undo')}
          </button>
        </div>
      )}
      {(trash.isError || restore.isError) && <ErrorMessage error={trash.error ?? restore.error} />}

      {all.length === 0 ? (
        <div className="empty-state">
          <p>{t('library.empty')}</p>
          <Link className="button primary" to="/import">
            {t('library.emptyAction')}
          </Link>
        </div>
      ) : (
        <>
          {selecting && <p className="hint">{t('library.selectHint')}</p>}
          {groupByMonth(all).map((group) => (
            <section key={group.key} aria-labelledby={`month-${group.key}`}>
              <h2 id={`month-${group.key}`} className="month-heading">
                {group.label}
              </h2>
              <ul className="photo-grid" data-testid="photo-grid">
                {group.photos.map((p) => (
                  <li key={p.id}>
                    <PhotoTile photo={p} selecting={selecting} selected={selected.has(p.id)} onToggle={toggle} />
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </>
      )}
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

      {selecting && (
        <div className="selection-bar" role="toolbar" aria-label={t('library.select')}>
          <span>{t('library.selected', { count: selected.size })}</span>
          <button
            type="button"
            className="button danger"
            disabled={selected.size === 0 || trash.isPending}
            onClick={moveSelectedToTrash}
          >
            {t('library.toTrash')}
          </button>
          <button type="button" className="button secondary" onClick={stopSelecting}>
            {t('library.cancelSelect')}
          </button>
        </div>
      )}
    </section>
  )
}
