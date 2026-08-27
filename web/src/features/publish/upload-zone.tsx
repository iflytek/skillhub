import { useCallback, useEffect, useRef, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useDropzone } from 'react-dropzone'
import { cn } from '@/shared/lib/utils'

interface UploadZoneProps {
  onFileSelect: (file: File) => void
  /** Optional: called with the raw files of a picked folder (webkitdirectory). */
  onFolderSelect?: (files: File[]) => void
  disabled?: boolean
}

/**
 * Provides the publish page dropzone for uploading one zip package at a time.
 * The component is intentionally stateless so packaging validation can remain in
 * the publish flow that knows the surrounding form and backend constraints.
 */
export function UploadZone({ onFileSelect, onFolderSelect, disabled }: UploadZoneProps) {
  const { t } = useTranslation()
  const folderInputRef = useRef<HTMLInputElement>(null)

  // `webkitdirectory` / `directory` are not in React's input attribute types;
  // set them imperatively so the folder picker works without an untyped cast.
  useEffect(() => {
    const el = folderInputRef.current
    if (el) {
      el.setAttribute('webkitdirectory', '')
      el.setAttribute('directory', '')
    }
  }, [])

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

  const handleFolderChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files
    if (files && files.length > 0) {
      onFolderSelect?.(Array.from(files))
    }
    // Reset so picking the same folder again re-triggers change.
    event.target.value = ''
  }

  return (
    <div className="flex flex-col gap-3">
      <div
        {...getRootProps()}
        className={cn(
          'upload-zone rounded-xl p-10 text-center cursor-pointer transition-all duration-300',
          isDragActive && 'border-primary bg-primary/5 scale-[1.01]',
          disabled && 'opacity-50 cursor-not-allowed'
        )}
      >
        <input {...getInputProps()} />
        <div className="flex flex-col items-center gap-3">
          <div className="w-14 h-14 rounded-2xl bg-secondary/60 flex items-center justify-center">
            <svg
              className={cn(
                'w-7 h-7 upload-zone-icon transition-colors',
                isDragActive && 'text-primary'
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
      {onFolderSelect && (
        <div className="text-center">
          <input
            ref={folderInputRef}
            type="file"
            className="hidden"
            multiple
            onChange={handleFolderChange}
            disabled={disabled}
          />
          <button
            type="button"
            className="text-xs text-muted-foreground underline-offset-2 hover:text-foreground hover:underline disabled:cursor-not-allowed disabled:opacity-50"
            onClick={() => folderInputRef.current?.click()}
            disabled={disabled}
          >
            {t('upload.folderHint')}
          </button>
        </div>
      )}
    </div>
  )
}
