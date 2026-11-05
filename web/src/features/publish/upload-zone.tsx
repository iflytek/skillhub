import { useCallback, useEffect, useId, useRef, useState, type ChangeEvent, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useDropzone, type FileRejection } from 'react-dropzone'
import { AlertCircle, CheckCircle2, FileArchive, Loader2, Upload } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'

interface UploadZoneProps {
  analyticsId?: string
  folderAnalyticsId?: string
  pickerAnalyticsId?: string
  onFileSelect: (file: File) => void
  /** The parent packages directory files and owns package validation. */
  onFolderSelect?: (files: File[]) => void
  selectedFile?: File | null
  previewStatus?: 'idle' | 'reading' | 'ready' | 'error'
  previewError?: string
  /** Caller-specific package requirements; the shared default only describes ZIP. */
  formatHint?: string
  isPackaging?: boolean
  onRemove?: () => void
  replaceActionId?: string
  disabled?: boolean
}

export function supportsDirectorySelection(input: HTMLInputElement | null): boolean {
  return Boolean(input && ('webkitdirectory' in input || 'directory' in input))
}

/** One package picker; selection, packaging and publication remain separate steps. */
export function UploadZone({
  onFileSelect,
  onFolderSelect,
  selectedFile,
  previewStatus = 'idle',
  previewError,
  formatHint,
  isPackaging = false,
  onRemove,
  replaceActionId,
  disabled = false,
  analyticsId,
  folderAnalyticsId,
  pickerAnalyticsId,
}: UploadZoneProps) {
  const { t } = useTranslation()
  const hintId = useId()
  const errorId = useId()
  const folderInputRef = useRef<HTMLInputElement>(null)
  const [folderSelectionSupported, setFolderSelectionSupported] = useState(false)
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const unavailable = disabled || isPackaging
  const unavailableRef = useRef(unavailable)
  const mountedRef = useRef(true)
  unavailableRef.current = unavailable

  useEffect(() => {
    const input = folderInputRef.current
    const supported = supportsDirectorySelection(input)
    if (input && supported) {
      input.setAttribute('webkitdirectory', '')
      input.setAttribute('directory', '')
    }
    setFolderSelectionSupported(supported)
  }, [])

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  useEffect(() => { setSelectionError(null) }, [selectedFile])

  const onDrop = useCallback((acceptedFiles: File[], rejectedFiles: FileRejection[]) => {
    if (!mountedRef.current || unavailableRef.current) return
    // Reject the whole selection rather than silently publishing its first valid file.
    if (acceptedFiles.length + rejectedFiles.length > 1) {
      setSelectionError('upload.errors.singleZip')
    } else if (rejectedFiles.length) {
      setSelectionError('upload.errors.zipRequired')
    } else if (acceptedFiles.length === 1) {
      setSelectionError(null)
      onFileSelect(acceptedFiles[0])
    } else {
      setSelectionError('upload.errors.emptySelection')
    }
  }, [onFileSelect])

  const { getRootProps, getInputProps, isDragActive, open } = useDropzone({
    onDrop,
    accept: { 'application/zip': ['.zip'] },
    validator: file => /\.zip$/i.test(file.name) ? null : { code: 'file-invalid-type', message: 'zip-required' },
    maxFiles: 1,
    multiple: false,
    disabled: unavailable,
    // A native full-surface button supplies click and keyboard interaction, without
    // nesting the explicit ZIP/folder action buttons inside an ARIA button.
    noClick: true,
    noKeyboard: true,
  })

  const rejectDirectoryDrop = (event: DragEvent<HTMLElement>) => {
    if (unavailableRef.current) return
    const hasDirectory = Array.from(event.dataTransfer.items ?? []).some(item => item.webkitGetAsEntry?.()?.isDirectory)
    if (hasDirectory) {
      event.preventDefault()
      event.stopPropagation()
      setSelectionError('upload.errors.folderDropUnsupported')
    }
  }

  const handleFolderChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (unavailableRef.current) return
    if (!files.length) {
      setSelectionError('upload.errors.emptySelection')
      return
    }
    setSelectionError(null)
    onFolderSelect?.(files)
  }

  const showError = selectionError ? t(selectionError) : !isPackaging && previewStatus === 'error' ? (previewError || t('publish.packagePreviewFailed')) : undefined
  const inlinePreviewError = !!selectedFile && !!showError && previewStatus === 'error' && !selectionError
  const dragging = isDragActive && selectionError !== 'upload.errors.folderDropUnsupported'
  const reading = isPackaging || previewStatus === 'reading'
  const status = isPackaging ? t('upload.packaging') : t(previewStatus === 'reading' ? 'publish.readingPackage' : previewStatus === 'ready' ? 'publish.packageParsed' : 'publish.packagePreviewFailed')
  const visibleStatus = reading || previewStatus === 'ready' ? status : inlinePreviewError ? showError : undefined
  const folderAction = onFolderSelect && folderSelectionSupported ? (
    <Button
      data-analytics-id={unavailable ? undefined : pickerAnalyticsId}
      type="button"
      variant="link"
      className="relative z-10 h-10 whitespace-normal px-2"
      disabled={unavailable}
      onClick={() => folderInputRef.current?.click()}
    >{t('upload.folderHint')}</Button>
  ) : null

  return (
    <div className="space-y-3">
      <div
        {...getRootProps({ onDrop: rejectDirectoryDrop })}
        data-analytics-id={unavailable ? undefined : analyticsId}
        data-analytics-event={!unavailable && analyticsId ? 'click+keydown+drop' : undefined}
        aria-busy={reading}
        className={cn(
          'relative rounded-xl border bg-background transition-colors',
          dragging ? 'border-primary bg-primary/5' : 'border-border',
          showError && 'border-destructive/60',
          unavailable && 'opacity-60',
        )}
      >
        <input {...getInputProps()} data-analytics-id={unavailable ? undefined : analyticsId} disabled={unavailable} />
        {!selectedFile && <button
          type="button"
          aria-label={t('upload.dragHint')}
          aria-describedby={showError ? errorId : selectedFile ? undefined : hintId}
          disabled={unavailable}
          onClick={open}
          className="absolute inset-0 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed"
        />}
        {selectedFile ? (
          <div className="pointer-events-none flex items-start gap-4 p-5 sm:p-6">
            <div className="flex h-14 w-12 shrink-0 items-center justify-center rounded-lg border bg-muted/30 text-muted-foreground"><FileArchive className="h-6 w-6" aria-hidden="true" /></div>
            <div className="min-w-0 flex-1 space-y-2">
              <p className="break-all text-sm font-medium sm:text-base">{selectedFile.name}</p>
              <p className="text-xs text-muted-foreground">{(selectedFile.size / 1024).toFixed(1)} KB</p>
              <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
                <p id={inlinePreviewError ? errorId : undefined} role={inlinePreviewError ? 'alert' : 'status'} className={cn('flex min-w-0 items-center gap-2 text-xs text-muted-foreground', previewStatus === 'ready' && !isPackaging && 'text-emerald-700 dark:text-emerald-400', inlinePreviewError && 'text-destructive')}>
                  {reading ? <Loader2 className="h-4 w-4 shrink-0 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : previewStatus === 'ready' ? <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" /> : previewStatus === 'error' ? <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" /> : null}
                  {visibleStatus}
                </p>
                <div className="pointer-events-auto relative z-10 flex flex-wrap items-center gap-2">
                  <Button id={replaceActionId} aria-describedby={showError ? errorId : undefined} type="button" variant="ghost" size="sm" disabled={unavailable} onClick={open}>{t('publish.replacePackage')}</Button>
                  {folderAction}
                  {onRemove && <Button data-analytics-id="skill.package.remove" type="button" variant="ghost" size="sm" disabled={unavailable} onClick={() => { setSelectionError(null); onRemove() }}>{t('publish.removeSelectedFile')}</Button>}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="pointer-events-none flex min-h-[260px] flex-col items-center justify-center gap-3 px-5 py-6 text-center sm:px-8">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-muted/70 text-muted-foreground"><Upload className="h-6 w-6" aria-hidden="true" /></div>
            <p className="text-lg font-semibold">{t(isPackaging ? 'upload.packaging' : dragging ? 'upload.dropHint' : 'upload.dragHint')}</p>
            <p id={hintId} className="text-sm text-muted-foreground">{formatHint ?? t('upload.formatHint')}</p>
            <div className="pointer-events-auto relative z-10 mt-1 flex flex-wrap items-center justify-center gap-x-3 gap-y-1">
              <Button type="button" className="h-10" disabled={unavailable} onClick={open}>{t('upload.selectZip')}</Button>
              {folderAction}
            </div>
            {onFolderSelect && folderSelectionSupported && <p className="text-xs leading-5 text-muted-foreground">{t('upload.folderPackagingHint')}</p>}
          </div>
        )}
        {reading && <div role="progressbar" aria-label={status} className="mx-5 mb-3 h-0.5 overflow-hidden rounded-full bg-muted sm:mx-6"><div className="h-full w-1/3 bg-muted-foreground/60" /></div>}
      </div>
      {showError && !inlinePreviewError && <p id={errorId} role="alert" className="text-sm text-destructive">{showError}</p>}
      {onFolderSelect && <input data-analytics-id={folderAnalyticsId} ref={folderInputRef} type="file" className="hidden" multiple onChange={handleFolderChange} disabled={unavailable} />}
    </div>
  )
}
