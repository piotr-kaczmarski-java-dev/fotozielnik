import { useQuery, useQueryClient } from '@tanstack/react-query'
import { type DragEvent, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { apiGet, apiSend } from '../../api/client.ts'
import { keys } from '../../api/queries.ts'
import type { CheckResponse, ImportBatch, ImportBatchPage, UploadResponse } from '../../api/types.ts'
import { ErrorMessage, Loading } from '../../ui/ErrorMessage.tsx'
import { HashPool } from './hasher.ts'
import { type ImportDeps, type ImportProgress, runImport } from './runImport.ts'
import { classify, fromDataTransfer, fromFileList, type ScanResult } from './scan.ts'

const api: Omit<ImportDeps, 'hash'> = {
  start: (body) => apiSend<ImportBatch>('POST', '/api/imports', body),
  check: (id, sha256s) => apiSend<CheckResponse>('POST', `/api/imports/${id}/check`, { sha256s }),
  upload: (id, sha256, f) =>
    apiSend<UploadResponse>('PUT', `/api/imports/${id}/files/${sha256}`, f.file, {
      'Content-Type': 'application/octet-stream',
      'X-File-Path': encodeURIComponent(f.path),
      'X-File-Modified': String(f.file.lastModified),
    }),
  finish: (id) => apiSend<ImportBatch>('POST', `/api/imports/${id}/finish`),
}

/** UC-01: choose a folder, upload what is new, watch processing (FR-004..FR-008). */
export function ImportPage() {
  const { t } = useTranslation()
  const client = useQueryClient()
  const [scan, setScan] = useState<ScanResult | null>(null)
  const [progress, setProgress] = useState<ImportProgress | null>(null)
  const running = progress !== null && (progress.phase === 'checking' || progress.phase === 'uploading')

  useEffect(() => {
    if (!running) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [running])

  async function start() {
    if (!scan) return
    const pool = new HashPool()
    try {
      await runImport(scan.folder, scan.jpegs, scan.skipped.length, { ...api, hash: (f) => pool.hash(f) }, setProgress)
    } finally {
      pool.terminate()
      void client.invalidateQueries({ queryKey: keys.photos })
      void client.invalidateQueries({ queryKey: keys.imports })
    }
  }

  function reset() {
    setScan(null)
    setProgress(null)
  }

  return (
    <div className="import-page">
      <h1>{t('import.title')}</h1>
      {!scan && <FolderChooser onChosen={setScan} />}
      {scan && !progress && <Review scan={scan} onStart={() => void start()} onCancel={reset} />}
      {progress && <Progress progress={progress} onAnother={reset} />}
      <History />
    </div>
  )
}

function FolderChooser({ onChosen }: { onChosen: (s: ScanResult) => void }) {
  const { t } = useTranslation()
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  useEffect(() => {
    // not in React's typings; makes the picker choose folders (all current browsers support it)
    input.current?.setAttribute('webkitdirectory', '')
  }, [])

  async function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    onChosen(classify(await fromDataTransfer(e.dataTransfer)))
  }

  return (
    <section
      className={`dropzone card ${dragging ? 'dragging' : ''}`}
      onDragOver={(e) => {
        e.preventDefault()
        setDragging(true)
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => void onDrop(e)}
    >
      <p>{t('import.intro')}</p>
      <label className="button primary large">
        {t('import.choose')}
        <input
          ref={input}
          type="file"
          multiple
          className="visually-hidden"
          data-testid="folder-input"
          onChange={(e) => e.target.files && e.target.files.length > 0 && onChosen(classify(fromFileList(e.target.files)))}
        />
      </label>
      <p className="hint">{dragging ? t('import.dropActive') : t('import.drop')}</p>
    </section>
  )
}

function Review({ scan, onStart, onCancel }: { scan: ScanResult; onStart: () => void; onCancel: () => void }) {
  const { t } = useTranslation()
  return (
    <section className="card" aria-live="polite">
      {scan.jpegs.length > 0 ? (
        <p className="big" data-testid="found">
          {t('import.found', { jpegs: scan.jpegs.length, folder: scan.folder })}
        </p>
      ) : (
        <p className="big">{t('import.noJpegs')}</p>
      )}
      {scan.skipped.length > 0 && (
        <details>
          <summary data-testid="skipped">{t('import.skipped', { count: scan.skipped.length })}</summary>
          <ul className="file-list">
            {scan.skipped.slice(0, 500).map((f) => (
              <li key={f.path}>{f.path}</li>
            ))}
          </ul>
        </details>
      )}
      <div className="actions">
        {scan.jpegs.length > 0 && (
          <button type="button" className="button primary large" onClick={onStart}>
            {t('import.start')}
          </button>
        )}
        <button type="button" className="button secondary" onClick={onCancel}>
          {t('import.chooseOther')}
        </button>
      </div>
    </section>
  )
}

function Progress({ progress: p, onAnother }: { progress: ImportProgress; onAnother: () => void }) {
  const { t } = useTranslation()
  const active = p.phase === 'checking' || p.phase === 'uploading'
  const done = p.checked + (p.toUpload > 0 ? p.uploaded + p.failed.length : 0)
  const max = p.total + p.toUpload
  return (
    <section className="card" aria-live="polite" data-testid="import-progress">
      <h2>{t(`import.phase.${p.phase}`)}</h2>
      {active && <progress value={done} max={Math.max(max, 1)} />}
      <p>{t('import.progressChecked', { done: p.checked, total: p.total })}</p>
      {p.toUpload > 0 && <p>{t('import.progressUploaded', { uploaded: p.uploaded, toUpload: p.toUpload })}</p>}
      {p.alreadyInLibrary > 0 && <p>{t('import.alreadyInLibrary', { count: p.alreadyInLibrary })}</p>}
      {active && <p className="alert alert-info">{t('import.keepOpen')}</p>}
      {p.failed.length > 0 && (
        <div className="alert alert-error">
          <p>{t('import.failedFiles', { count: p.failed.length })}</p>
          <ul className="file-list">
            {p.failed.map((f) => (
              <li key={f.path}>
                {f.path} - {t(`errors.${f.reason}`, { defaultValue: t('errors.generic') })}
              </li>
            ))}
          </ul>
        </div>
      )}
      {p.phase === 'error' && (
        <>
          <ErrorMessage error={p.error} />
          <p>{t('import.resumeHint')}</p>
        </>
      )}
      {p.phase === 'done' && p.batchId && <Processing batchId={p.batchId} />}
      {!active && (
        <div className="actions">
          <Link className="button primary" to="/">
            {t('import.goToLibrary')}
          </Link>
          <button type="button" className="button secondary" onClick={onAnother}>
            {t('import.another')}
          </button>
        </div>
      )}
    </section>
  )
}

/** Background processing continues on the server; poll until every new photo is ready. */
function Processing({ batchId }: { batchId: string }) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const batch = useQuery({
    queryKey: keys.importBatch(batchId),
    queryFn: () => apiGet<ImportBatch>(`/api/imports/${batchId}`),
    refetchInterval: (q) => ((q.state.data?.processingPending ?? 1) > 0 ? 2000 : false),
  })
  const pending = batch.data?.processingPending
  useEffect(() => {
    // thumbnails just became ready: the library must not show its older, half-processed copy
    if (pending === 0) void client.invalidateQueries({ queryKey: keys.photos })
  }, [pending, client])
  if (!batch.data) return null
  const b = batch.data
  if (b.filesUploaded === 0) return null
  return b.processingPending > 0 ? (
    <p role="status">{t('import.processing', { done: b.processingDone + b.processingFailed, total: b.filesUploaded })}</p>
  ) : (
    <p role="status" className="alert alert-success" data-testid="processing-done">
      {t('import.processingDone')}
    </p>
  )
}

function History() {
  const { t } = useTranslation()
  const history = useQuery({
    queryKey: keys.imports,
    queryFn: () => apiGet<ImportBatchPage>('/api/imports?size=20'),
  })
  const date = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'long', timeStyle: 'short' })
  return (
    <section className="history">
      <h2>{t('import.history')}</h2>
      {history.isPending && <Loading />}
      {history.isError && <ErrorMessage error={history.error} onRetry={() => void history.refetch()} />}
      {history.data && history.data.items.length === 0 && <p>{t('import.historyEmpty')}</p>}
      {history.data && history.data.items.length > 0 && (
        <ul className="history-list">
          {history.data.items.map((b) => (
            <li key={b.id}>
              <strong>{b.label}</strong> <span className="muted">{date.format(new Date(b.createdAt))}</span>
              <br />
              {t('import.historyRow', { uploaded: b.filesUploaded, duplicate: b.filesDuplicate, failed: b.filesFailed })}
              {b.processingPending > 0 && ` · ${t('import.historyProcessing', { pending: b.processingPending })}`}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
