import type { HashRequest, HashResponse } from './hashWorker.ts'

/** A small pool of hashing workers; `hash()` resolves to the lower-case hex SHA-256 of a file. */
export class HashPool {
  private readonly idle: Worker[] = []
  private readonly all: Worker[] = []
  private readonly waiting: Array<(w: Worker) => void> = []
  private nextId = 0

  constructor(size = Math.max(1, Math.min(4, Math.floor((navigator.hardwareConcurrency || 2) / 2)))) {
    for (let i = 0; i < size; i++) {
      const w = new Worker(new URL('./hashWorker.ts', import.meta.url), { type: 'module' })
      this.all.push(w)
      this.idle.push(w)
    }
  }

  async hash(file: File): Promise<string> {
    const worker = await this.acquire()
    try {
      return await new Promise<string>((resolve, reject) => {
        const id = this.nextId++
        worker.onmessage = (e: MessageEvent<HashResponse>) => {
          if (e.data.id !== id) return
          if ('sha256' in e.data) resolve(e.data.sha256)
          else reject(new Error(e.data.error))
        }
        worker.onerror = (e) => reject(new Error(e.message))
        worker.postMessage({ id, file } satisfies HashRequest)
      })
    } finally {
      this.release(worker)
    }
  }

  terminate() {
    this.all.forEach((w) => w.terminate())
  }

  private acquire(): Promise<Worker> {
    const w = this.idle.pop()
    if (w) return Promise.resolve(w)
    return new Promise((resolve) => this.waiting.push(resolve))
  }

  private release(w: Worker) {
    const next = this.waiting.shift()
    if (next) next(w)
    else this.idle.push(w)
  }
}
