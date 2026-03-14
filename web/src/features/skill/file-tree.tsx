import { useTranslation } from 'react-i18next'
import type { SkillFile } from '@/api/types'

interface FileTreeProps {
  files: SkillFile[]
  onFileClick?: (file: SkillFile) => void
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function FileTree({ files, onFileClick }: FileTreeProps) {
  const { t } = useTranslation()
  return (
    <div className="border rounded-lg overflow-hidden">
      <div className="bg-muted px-4 py-2 text-sm font-medium">
        {t('fileTree.title', { count: files.length })}
      </div>
      <div className="divide-y">
        {files.map((file) => (
          <div
            key={file.id}
            className="px-4 py-2 hover:bg-muted/50 cursor-pointer flex items-center justify-between"
            onClick={() => onFileClick?.(file)}
          >
            <span className="text-sm font-mono">{file.filePath}</span>
            <span className="text-xs text-muted-foreground">
              {formatFileSize(file.fileSize)}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
