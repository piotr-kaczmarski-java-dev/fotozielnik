/// <reference lib="webworker" />
// Streaming SHA-256 of one file at a time (ADR-0012: hash-wasm in a Web Worker, so 20 MB photos never have
// to sit in memory as a whole and the page stays responsive).
import { createSHA256 } from 'hash-wasm'

const CHUNK = 4 * 1024 * 1024

export interface HashRequest {
  id: number
  file: File
}
export type HashResponse = { id: number; sha256: string } | { id: number; error: string }

self.onmessage = async (event: MessageEvent<HashRequest>) => {
  const { id, file } = event.data
  try {
    const hasher = await createSHA256()
    hasher.init()
    for (let offset = 0; offset < file.size; offset += CHUNK) {
      const chunk = await file.slice(offset, offset + CHUNK).arrayBuffer()
      hasher.update(new Uint8Array(chunk))
    }
    self.postMessage({ id, sha256: hasher.digest('hex') } satisfies HashResponse)
  } catch (e) {
    self.postMessage({ id, error: e instanceof Error ? e.message : String(e) } satisfies HashResponse)
  }
}
