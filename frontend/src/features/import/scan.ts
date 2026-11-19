/** A file picked by the user, with its path inside the chosen folder ("Łąka/2024/IMG_1.jpg"). */
export interface ScannedFile {
  file: File
  path: string
}

export interface ScanResult {
  folder: string
  jpegs: ScannedFile[]
  skipped: ScannedFile[]
}

const JPEG = /\.(jpe?g|jpe)$/i
// System clutter is ignored silently instead of being reported as "not a photo".
const IGNORED = /(^|\/)(\.[^/]*|thumbs\.db|desktop\.ini)$/i

/** Splits a folder's files into JPEGs to import and other files to skip (FR-004). */
export function classify(files: ScannedFile[]): ScanResult {
  const jpegs: ScannedFile[] = []
  const skipped: ScannedFile[] = []
  for (const f of files) {
    if (IGNORED.test(f.path)) continue
    ;(JPEG.test(f.path) ? jpegs : skipped).push(f)
  }
  jpegs.sort((a, b) => a.path.localeCompare(b.path, 'pl'))
  const folder = files[0]?.path.split('/')[0] ?? ''
  return { folder, jpegs, skipped }
}

/** Files from `<input type="file" webkitdirectory>`. */
export function fromFileList(list: FileList): ScannedFile[] {
  return Array.from(list, (file) => ({ file, path: file.webkitRelativePath || file.name }))
}

/** Files from a dropped folder (or files), walking directories recursively. */
export async function fromDataTransfer(dt: DataTransfer): Promise<ScannedFile[]> {
  const entries = Array.from(dt.items)
    .map((item) => item.webkitGetAsEntry())
    .filter((e): e is FileSystemEntry => e !== null)
  const out: ScannedFile[] = []
  for (const entry of entries) await walk(entry, out)
  return out
}

async function walk(entry: FileSystemEntry, out: ScannedFile[]): Promise<void> {
  const path = entry.fullPath.replace(/^\//, '')
  if (entry.isFile) {
    const file = await new Promise<File>((resolve, reject) => (entry as FileSystemFileEntry).file(resolve, reject))
    out.push({ file, path })
    return
  }
  if (entry.isDirectory) {
    const reader = (entry as FileSystemDirectoryEntry).createReader()
    // readEntries returns at most ~100 entries per call: keep reading until it returns none
    for (;;) {
      const batch = await new Promise<FileSystemEntry[]>((resolve, reject) => reader.readEntries(resolve, reject))
      if (batch.length === 0) break
      for (const child of batch) await walk(child, out)
    }
  }
}
