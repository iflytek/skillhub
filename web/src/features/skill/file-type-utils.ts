/**
 * File type detection and preview capability utilities.
 * Determines which files can be previewed and provides appropriate icons/labels.
 */

// Maximum file size for preview (1MB)
const MAX_PREVIEW_SIZE = 1024 * 1024

// File extensions that support text preview
const PREVIEWABLE_EXTENSIONS = new Set([
  // Markdown
  'md', 'mdx', 'markdown',
  // Code
  'ts', 'tsx', 'js', 'jsx', 'json', 'yaml', 'yml',
  'py', 'java', 'go', 'rs', 'c', 'cpp', 'h', 'hpp',
  'sh', 'bash', 'zsh', 'fish',
  // Config
  'txt', 'xml', 'toml', 'ini', 'env',
  // Web
  'html', 'css', 'scss', 'sass', 'less',
  'vue', 'svelte',
])

// Binary file extensions that cannot be previewed
const BINARY_EXTENSIONS = new Set([
  'png', 'jpg', 'jpeg', 'gif', 'bmp', 'ico', 'svg',
  'mp4', 'avi', 'mov', 'wmv', 'flv', 'webm',
  'mp3', 'wav', 'ogg', 'flac',
  'zip', 'tar', 'gz', 'rar', '7z',
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
  'exe', 'dll', 'so', 'dylib',
])

/**
 * Extracts the file extension from a filename.
 *
 * @param fileName - The name of the file
 * @returns The lowercase file extension without the dot
 */
export function getFileExtension(fileName: string): string {
  const parts = fileName.split('.')
  return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : ''
}

/**
 * Checks if a file can be previewed based on its name and size.
 *
 * @param fileName - The name of the file
 * @param fileSize - The size of the file in bytes
 * @returns True if the file can be previewed
 */
export function isPreviewable(fileName: string, fileSize: number): boolean {
  if (fileSize > MAX_PREVIEW_SIZE) {
    return false
  }

  const ext = getFileExtension(fileName)

  if (BINARY_EXTENSIONS.has(ext)) {
    return false
  }

  // Allow files with no extension or known previewable extensions
  return PREVIEWABLE_EXTENSIONS.has(ext) || ext === ''
}

/**
 * Checks if a file can be previewed and provides a reason if not.
 *
 * @param fileName - The name of the file
 * @param fileSize - The size of the file in bytes
 * @returns Object with canPreview flag and optional reason
 */
export function canPreviewFile(fileName: string, fileSize: number): {
  canPreview: boolean
  reason?: 'too-large' | 'binary' | 'unsupported'
} {
  if (fileSize > MAX_PREVIEW_SIZE) {
    return { canPreview: false, reason: 'too-large' }
  }

  const ext = getFileExtension(fileName)

  if (BINARY_EXTENSIONS.has(ext)) {
    return { canPreview: false, reason: 'binary' }
  }

  if (!PREVIEWABLE_EXTENSIONS.has(ext) && ext !== '') {
    return { canPreview: false, reason: 'unsupported' }
  }

  return { canPreview: true }
}

/**
 * Gets a human-readable label for the file type.
 *
 * @param fileName - The name of the file
 * @returns A label describing the file type
 */
export function getFileTypeLabel(fileName: string): string {
  const ext = getFileExtension(fileName)

  const labelMap: Record<string, string> = {
    md: 'markdown',
    mdx: 'markdown',
    ts: 'typescript',
    tsx: 'typescript',
    js: 'javascript',
    jsx: 'javascript',
    py: 'python',
    sh: 'bash',
    bash: 'bash',
    yml: 'yaml',
  }

  return labelMap[ext] || ext || 'text'
}

/**
 * Gets the appropriate Lucide icon name for a file.
 *
 * @param fileName - The name of the file
 * @returns The name of the Lucide icon component to use
 */
export function getFileIcon(fileName: string): string {
  const ext = getFileExtension(fileName)

  // Return icon name from lucide-react
  if (['md', 'mdx'].includes(ext)) return 'FileText'
  if (['ts', 'tsx', 'js', 'jsx'].includes(ext)) return 'FileCode'
  if (['json', 'yaml', 'yml'].includes(ext)) return 'FileJson'
  if (['sh', 'bash'].includes(ext)) return 'Terminal'
  if (BINARY_EXTENSIONS.has(ext)) return 'File'

  return 'FileText'
}
