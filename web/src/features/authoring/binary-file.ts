/**
 * Binary draft files (images, office documents, PDFs) round-trip through the
 * API as base64 and cannot be edited in the plain-text editor; the workbench
 * shows them read-only with an upload-to-replace action instead.
 */

const BINARY_EXTENSIONS = new Set([
  '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico',
  '.pdf', '.doc', '.xls', '.ppt', '.docx', '.xlsx', '.pptx',
])

export function isBinaryDraftPath(path: string): boolean {
  const lower = path.toLowerCase()
  for (const extension of BINARY_EXTENSIONS) {
    if (lower.endsWith(extension)) {
      return true
    }
  }
  return false
}

export function formatBytes(size: number): string {
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

/** Reads a browser File as base64 (no data: prefix) for the API's base64 encoding. */
export function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error ?? new Error('failed to read file'))
    reader.onload = () => {
      const buffer = reader.result
      if (!(buffer instanceof ArrayBuffer)) {
        reject(new Error('failed to read file'))
        return
      }
      resolve(bytesToBase64(new Uint8Array(buffer)))
    }
    reader.readAsArrayBuffer(file)
  })
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  const chunkSize = 0x8000
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize))
  }
  return btoa(binary)
}
