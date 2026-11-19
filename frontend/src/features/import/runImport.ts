// The import flow of UC-01, independent of React so it can be tested with fakes:
// start batch -> hash files -> ask which are new (chunks) -> upload only those, a few in parallel -> finish.
import { ApiError } from '../../api/client.ts'
import type { CheckResponse, ImportBatch, StartImportRequest, UploadResponse } from '../../api/types.ts'
import type { ScannedFile } from './scan.ts'

export interface ImportDeps {
  start(body: StartImportRequest): Promise<ImportBatch>
  check(batchId: string, sha256s: string[]): Promise<CheckResponse>
  upload(batchId: string, sha256: string, file: ScannedFile): Promise<UploadResponse>
  finish(batchId: string): Promise<ImportBatch>
  hash(file: File): Promise<string>
  /** waits between upload retries; injectable so tests do not sleep */
  sleep?(ms: number): Promise<void>
}

export interface FailedFile {
  path: string
  /** the backend's error code (e.g. `import.not-a-jpeg`) or `network` */
  reason: string
}

export interface ImportProgress {
  phase: 'checking' | 'uploading' | 'done' | 'error'
  batchId: string | null
  total: number
  checked: number
  alreadyInLibrary: number
  toUpload: number
  uploaded: number
  failed: FailedFile[]
  error?: unknown
}

const CHECK_CHUNK = 200 // hashes per check request (API allows 1000)
const PARALLEL_UPLOADS = 3
const MAX_QUEUED_UPLOADS = 200 // stop hashing ahead when uploads lag behind
const RETRY_DELAYS_MS = [2_000, 5_000, 15_000]

export async function runImport(
  label: string,
  files: ScannedFile[],
  skippedCount: number,
  deps: ImportDeps,
  onProgress: (p: ImportProgress) => void,
): Promise<ImportProgress> {
  const sleep = deps.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)))
  const p: ImportProgress = {
    phase: 'checking',
    batchId: null,
    total: files.length,
    checked: 0,
    alreadyInLibrary: 0,
    toUpload: 0,
    uploaded: 0,
    failed: [],
  }
  const emit = () => onProgress({ ...p, failed: [...p.failed] })
  emit()

  try {
    const batch = await deps.start({ label, filesFound: files.length, filesSkipped: skippedCount })
    const batchId = batch.id
    p.batchId = batchId

    const seen = new Set<string>()
    const uploads = new UploadQueue(PARALLEL_UPLOADS)

    for (let i = 0; i < files.length; i += CHECK_CHUNK) {
      await uploads.waitBelow(MAX_QUEUED_UPLOADS)
      const chunk = files.slice(i, i + CHECK_CHUNK)
      const hashed = await Promise.all(chunk.map(async (f) => ({ f, sha256: await deps.hash(f.file) })))
      // the same photo twice in one folder (a copy): upload it once
      const unique = hashed.filter(({ sha256 }) => {
        if (seen.has(sha256)) {
          p.alreadyInLibrary++
          return false
        }
        seen.add(sha256)
        return true
      })
      const missing = new Set(
        unique.length ? (await deps.check(batchId, unique.map((u) => u.sha256))).missing : [],
      )
      p.alreadyInLibrary += unique.length - missing.size
      p.checked += chunk.length
      for (const u of unique.filter((x) => missing.has(x.sha256))) {
        p.toUpload++
        if (p.phase === 'checking') p.phase = 'uploading'
        uploads.add(async () => {
          const outcome = await uploadWithRetry(() => deps.upload(batchId, u.sha256, u.f), sleep)
          if (outcome === 'ok') p.uploaded++
          else if (outcome === 'duplicate') p.alreadyInLibrary++
          else p.failed.push({ path: u.f.path, reason: outcome.reason })
          emit()
        })
      }
      emit()
    }
    await uploads.idle()
    await deps.finish(batchId)
    p.phase = 'done'
  } catch (e) {
    p.phase = 'error'
    p.error = e
  }
  emit()
  return p
}

type Outcome = 'ok' | 'duplicate' | { reason: string }

/** Network problems and server errors are retried; a rejected file (4xx) is reported and skipped. */
async function uploadWithRetry(send: () => Promise<UploadResponse>, sleep: (ms: number) => Promise<void>): Promise<Outcome> {
  for (let attempt = 0; ; attempt++) {
    try {
      const r = await send()
      return r.created ? 'ok' : 'duplicate'
    } catch (e) {
      const retryable = !(e instanceof ApiError) || e.status >= 500 || e.code === 'import.hash-mismatch'
      if (e instanceof ApiError && e.code === 'import.finished') throw e // the whole import is over
      const delay = RETRY_DELAYS_MS[attempt]
      if (!retryable || delay === undefined) {
        return { reason: e instanceof ApiError ? (e.code ?? `http-${e.status}`) : 'network' }
      }
      await sleep(delay)
    }
  }
}

/** Runs async tasks with limited parallelism; the first unexpected error stops the import. */
class UploadQueue {
  private running = 0
  private readonly queue: Array<() => Promise<void>> = []
  private readonly waiters: Array<() => void> = []
  private failure: unknown = null

  private readonly parallel: number

  constructor(parallel: number) {
    this.parallel = parallel
  }

  add(task: () => Promise<void>) {
    this.queue.push(task)
    this.pump()
  }

  /** Resolves when fewer than `n` tasks are waiting or running. */
  async waitBelow(n: number) {
    while (this.queue.length + this.running >= n) await this.next()
    this.throwIfFailed()
  }

  async idle() {
    while (this.queue.length + this.running > 0) await this.next()
    this.throwIfFailed()
  }

  private next() {
    return new Promise<void>((resolve) => this.waiters.push(resolve))
  }

  private throwIfFailed() {
    if (this.failure !== null) throw this.failure
  }

  private pump() {
    while (this.running < this.parallel && this.queue.length > 0 && this.failure === null) {
      const task = this.queue.shift()!
      this.running++
      task()
        .catch((e: unknown) => {
          this.failure ??= e
          this.queue.length = 0
        })
        .finally(() => {
          this.running--
          this.waiters.splice(0).forEach((w) => w())
          this.pump()
        })
    }
  }
}
