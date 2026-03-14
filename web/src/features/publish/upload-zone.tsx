import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useDropzone } from 'react-dropzone'
import { cn } from '@/shared/lib/utils'

interface UploadZoneProps {
  onFileSelect: (file: File) => void
  disabled?: boolean
}

export function UploadZone({ onFileSelect, disabled }: UploadZoneProps) {
  const { t } = useTranslation()
  const onDrop = useCallback(
    (acceptedFiles: File[]) => {
      if (acceptedFiles.length > 0) {
        onFileSelect(acceptedFiles[0])
      }
    },
    [onFileSelect]
  )

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'application/zip': ['.zip'],
    },
    maxFiles: 1,
    disabled,
  })

  return (
    <div
      {...getRootProps()}
      className={cn(
        'border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-all duration-300',
        isDragActive && 'border-primary bg-primary/5 scale-[1.01]',
        !isDragActive && 'border-border/60 hover:border-primary/40 hover:bg-secondary/30',
        disabled && 'opacity-50 cursor-not-allowed'
      )}
    >
      <input {...getInputProps()} />
      <div className="flex flex-col items-center gap-3">
        <div className="w-14 h-14 rounded-2xl bg-secondary/60 flex items-center justify-center">
          <svg
            className={cn(
              'w-7 h-7 transition-colors',
              isDragActive ? 'text-primary' : 'text-muted-foreground'
            )}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
            />
          </svg>
        </div>
        {isDragActive ? (
          <p className="text-sm text-primary font-medium">{t('upload.dropHint')}</p>
        ) : (
          <>
            <p className="text-sm font-medium text-foreground">{t('upload.dragHint')}</p>
            <p className="text-xs text-muted-foreground">{t('upload.formatHint')}</p>
          </>
        )}
      </div>
    </div>
  )
}
