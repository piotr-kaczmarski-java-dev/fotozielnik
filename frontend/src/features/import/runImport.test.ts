import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api/client.ts'
import type { ImportBatch } from '../../api/types.ts'
import { type ImportDeps, type ImportProgress, runImport } from './runImport.ts'
import { classify, type ScannedFile } from './scan.ts'

const file = (path: string, content = path): ScannedFile => ({ path, file: new File([content], path.split('/').at(-1)!) })

function fakeServer(stored: string[] = []) {
  const library = new Set(stored)
  const uploads: string[] = []
  const deps: ImportDeps = {
    start: async (body) => ({ id: 'b1', label: body.label }) as ImportBatch,
    check: async (_id, hashes) => ({ missing: hashes.filter((h) => !library.has(h)) }),
    upload: async (_id, sha) => {
      uploads.push(sha)
      const created = !library.has(sha)
      library.add(sha)
      return { photoId: `p-${sha}`, created }
    },
    finish: async () => ({ id: 'b1' }) as ImportBatch,
    hash: async (f) => `h:${await f.text()}`,
    sleep: async () => {},
  }
  return { deps, uploads }
}

describe('runImport', () => {
  it('uploads only files the library does not have', async () => {
    const { deps, uploads } = fakeServer(['h:a'])
    const result = await runImport('Łąka', [file('Łąka/a.jpg', 'a'), file('Łąka/b.jpg', 'b')], 1, deps, () => {})
    expect(result.phase).toBe('done')
    expect(uploads).toEqual(['h:b'])
    expect(result).toMatchObject({ total: 2, checked: 2, alreadyInLibrary: 1, toUpload: 1, uploaded: 1 })
  })

  it('a second run of the same folder uploads nothing', async () => {
    const { deps, uploads } = fakeServer()
    const folder = [file('f/a.jpg', 'a'), file('f/b.jpg', 'b')]
    await runImport('f', folder, 0, deps, () => {})
    uploads.length = 0
    const again = await runImport('f', folder, 0, deps, () => {})
    expect(uploads).toEqual([])
    expect(again.alreadyInLibrary).toBe(2)
  })

  it('uploads a photo copied twice into the folder only once', async () => {
    const { deps, uploads } = fakeServer()
    const result = await runImport('f', [file('f/a.jpg', 'x'), file('f/kopia/a.jpg', 'x')], 0, deps, () => {})
    expect(uploads).toEqual(['h:x'])
    expect(result.alreadyInLibrary).toBe(1)
  })

  it('retries network failures and reports rejected files without stopping', async () => {
    const { deps } = fakeServer()
    let flaky = 0
    const upload = deps.upload
    deps.upload = async (id, sha, f) => {
      if (f.path.endsWith('flaky.jpg') && flaky++ < 2) throw new TypeError('Failed to fetch')
      if (f.path.endsWith('bad.jpg')) throw new ApiError({ status: 400, code: 'import.not-a-jpeg' })
      return upload(id, sha, f)
    }
    const progress: ImportProgress[] = []
    const result = await runImport(
      'f',
      [file('f/flaky.jpg'), file('f/bad.jpg'), file('f/ok.jpg')],
      0,
      deps,
      (p) => progress.push(p),
    )
    expect(result.phase).toBe('done')
    expect(result.uploaded).toBe(2)
    expect(result.failed).toEqual([{ path: 'f/bad.jpg', reason: 'import.not-a-jpeg' }])
    expect(progress.at(-1)?.phase).toBe('done')
  })

  it('stops with an error when the server says the import is over', async () => {
    const { deps } = fakeServer()
    deps.upload = async () => {
      throw new ApiError({ status: 409, code: 'import.finished' })
    }
    const result = await runImport('f', [file('f/a.jpg')], 0, deps, () => {})
    expect(result.phase).toBe('error')
  })
})

describe('classify', () => {
  it('takes JPEGs in any letter case, skips other files and ignores system clutter', () => {
    const r = classify([
      file('Wiosna/IMG_1.JPG'),
      file('Wiosna/a/img2.jpeg'),
      file('Wiosna/notes.txt'),
      file('Wiosna/RAW/IMG_1.CR2'),
      file('Wiosna/.DS_Store'),
      file('Wiosna/Thumbs.db'),
    ])
    expect(r.folder).toBe('Wiosna')
    expect(r.jpegs.map((f) => f.path)).toEqual(['Wiosna/a/img2.jpeg', 'Wiosna/IMG_1.JPG'])
    expect(r.skipped.map((f) => f.path)).toEqual(['Wiosna/notes.txt', 'Wiosna/RAW/IMG_1.CR2'])
  })
})
