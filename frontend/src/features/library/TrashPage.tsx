import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { apiGet, apiSend } from '../../api/client.ts'
import { keys } from '../../api/queries.ts'
import type { PhotoPage, TrashInfo, TrashResponse } from '../../api/types.ts'
import { ErrorMessage, Loading } from '../../ui/ErrorMessage.tsx'
import { PhotoTile } from './PhotoTile.tsx'
import { useTrashMutation } from './trash.ts'

const DAY = 86_400_000

/** FR-022: what is in the trash, restore some, or empty it for good. */
export function TrashPage() {
  const { t } = useTranslation()
  const client = useQueryClient()
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [notice, setNotice] = useState<string | null>(null)
  const [now] = useState(() => Date.now()) // "days left" is counted from when the page was opened
  const restore = useTrashMutation('restore')
  const info = useQuery({ queryKey: keys.trashInfo, queryFn: () => apiGet<TrashInfo>('/api/photos/trash/info') })
  const photos = useInfiniteQuery({
    queryKey: keys.trash,
    queryFn: ({ pageParam }) =>
      apiGet<PhotoPage>(`/api/photos/trash?size=60${pageParam ? `&cursor=${encodeURIComponent(pageParam)}` : ''}`),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
  const empty = useMutation({
    mutationFn: () => apiSend<TrashResponse>('POST', '/api/photos/trash/empty'),
    onSuccess: () => {
      setNotice(t('trash.emptied'))
      setSelected(new Set())
      // purging runs in the background: show the trash as empty right away, the server follows shortly
      client.setQueryData(keys.trash, { pages: [{ items: [], nextCursor: null }], pageParams: [null] })
      client.setQueryData(keys.trashInfo, (old: TrashInfo | undefined) => (old ? { ...old, count: 0 } : old))
    },
  })

  function toggle(id: string) {
    setSelected((s) => {
      const next = new Set(s)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const days = info.data?.retentionDays ?? 30
  const all = photos.data?.pages.flatMap((p) => p.items) ?? []
  const count = info.data?.count ?? all.length

  return (
    <section className="with-selection-bar">
      <div className="page-header">
        <h1>{t('trash.title')}</h1>
        <Link to="/" className="button link">
          ← {t('trash.back')}
        </Link>
      </div>
      <p>{t('trash.info', { days })}</p>
      {notice && (
        <p role="status" className="alert alert-success">
          {notice}
        </p>
      )}
      {(restore.isError || empty.isError) && <ErrorMessage error={restore.error ?? empty.error} />}
      {photos.isPending && <Loading />}
      {photos.isError && <ErrorMessage error={photos.error} onRetry={() => void photos.refetch()} />}
      {photos.data && all.length === 0 && <p className="empty-state">{t('trash.empty')}</p>}
      {all.length > 0 && (
        <>
          <p className="hint">{t('trash.selectHint')}</p>
          <ul className="photo-grid" data-testid="trash-grid">
            {all.map((p) => {
              const left =
                p.deletedAt != null ? Math.max(0, Math.ceil((Date.parse(p.deletedAt) + days * DAY - now) / DAY)) : days
              return (
                <li key={p.id}>
                  <PhotoTile
                    photo={p}
                    selecting
                    selected={selected.has(p.id)}
                    onToggle={toggle}
                    badge={t('trash.daysLeft', { count: left })}
                  />
                </li>
              )
            })}
          </ul>
          {photos.hasNextPage && (
            <button type="button" className="button secondary more" onClick={() => void photos.fetchNextPage()}>
              {t('library.more')}
            </button>
          )}
        </>
      )}
      {all.length > 0 && (
        <div className="selection-bar" role="toolbar" aria-label={t('trash.title')}>
          <span>{t('library.selected', { count: selected.size })}</span>
          <button
            type="button"
            className="button primary"
            disabled={selected.size === 0 || restore.isPending}
            onClick={() => {
              const ids = [...selected]
              restore.mutate(ids, {
                onSuccess: (r) => {
                  setNotice(t('trash.restored', { count: r.changed }))
                  setSelected(new Set())
                },
              })
            }}
          >
            {t('trash.restoreSelected')}
          </button>
          <button
            type="button"
            className="button danger"
            disabled={empty.isPending}
            onClick={() => {
              if (window.confirm(t('trash.confirmEmpty', { count }))) empty.mutate()
            }}
          >
            {t('trash.emptyTrash')}
          </button>
        </div>
      )}
    </section>
  )
}
