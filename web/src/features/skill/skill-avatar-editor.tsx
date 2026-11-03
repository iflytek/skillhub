import { useEffect, useRef, useState } from 'react'
import ReactCrop, { centerCrop, makeAspectCrop, convertToPixelCrop, cropToCanvas, type PercentCrop } from 'react-image-crop'
import { useDropzone } from 'react-dropzone'
import 'react-image-crop/dist/ReactCrop.css'
import { useTranslation } from 'react-i18next'
import { buildApiUrl, skillAvatarApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'
import { SkillAvatar } from './skill-avatar'

export type AvatarEdit = { action: 'KEEP' | 'REPLACE' | 'REMOVE'; uploadId?: string; ready: boolean }
export const KEEP_AVATAR: AvatarEdit = { action: 'KEEP', ready: true }

async function exportCrop(image: HTMLImageElement, crop: PercentCrop): Promise<Blob> {
  const cropped = document.createElement('canvas')
  await cropToCanvas(image, cropped, convertToPixelCrop(crop, image.width, image.height))
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 512
  const context = canvas.getContext('2d')
  if (!context) throw new Error('Canvas unavailable')
  context.drawImage(cropped, 0, 0, 512, 512)
  return new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Image conversion failed')), 'image/png'))
}

/** A private upload is staged here; only the parent's successful submit associates it. */
export function SkillAvatarEditor({ name, currentUrl, canRemove = false, disabled = false, onChange }: {
  name: string; currentUrl?: string | null; canRemove?: boolean; disabled?: boolean; onChange: (edit: AvatarEdit) => void
}) {
  const { t } = useTranslation()
  const [sourceUrl, setSourceUrl] = useState<string | null>(null)
  const [crop, setCrop] = useState<PercentCrop>()
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [state, setState] = useState<'KEEP' | 'SELECTED' | 'UPLOADING' | 'READY' | 'ERROR' | 'REMOVE'>('KEEP')
  const [error, setError] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const edit = useRef<AvatarEdit>(KEEP_AVATAR)
  const beforeSelection = useRef({ edit: KEEP_AVATAR, previewUrl: null as string | null, state: 'KEEP' as typeof state })
  const chooseButton = useRef<HTMLButtonElement>(null)
  const generation = useRef(0)
  const image = useRef<HTMLImageElement>(null)
  useEffect(() => () => { generation.current += 1 }, [])
  useEffect(() => () => { if (sourceUrl) URL.revokeObjectURL(sourceUrl) }, [sourceUrl])

  function reset(remove = false) {
    generation.current += 1
    setSourceUrl(null)
    setPreviewUrl(null)
    setError('')
    setState(remove ? 'REMOVE' : 'KEEP')
    setCrop(undefined)
    if (inputRef.current) inputRef.current.value = ''
    setDialogOpen(false)
    edit.current = remove ? { action: 'REMOVE', ready: true } : KEEP_AVATAR
    onChange(edit.current)
  }

  function cancelSelection() {
    generation.current += 1
    setDialogOpen(false)
    setSourceUrl(null)
    setCrop(undefined)
    setError('')
    setPreviewUrl(beforeSelection.current.previewUrl)
    setState(beforeSelection.current.state)
    edit.current = beforeSelection.current.edit
    onChange(edit.current)
    if (inputRef.current) inputRef.current.value = ''
  }

  async function upload() {
    if (!image.current || !crop?.width || !crop.height) return
    const attempt = ++generation.current
    setState('UPLOADING')
    setError('')
    onChange({ action: 'REPLACE', ready: false })
    try {
      const blob = await exportCrop(image.current, crop)
      if (attempt !== generation.current) return
      const staged = await skillAvatarApi.stage(blob)
      if (attempt !== generation.current) return
      setPreviewUrl(staged.previewUrl)
      setState('READY')
      edit.current = { action: 'REPLACE', uploadId: staged.uploadId, ready: true }
      onChange(edit.current)
      setDialogOpen(false)
      setSourceUrl(null)
      setCrop(undefined)
      if (inputRef.current) inputRef.current.value = ''
    } catch (cause) {
      if (attempt !== generation.current) return
      setState('ERROR')
      setError(cause instanceof Error ? cause.message : t('skillAvatar.failed'))
    }
  }

  const { getRootProps, getInputProps, inputRef, open } = useDropzone({
    accept: { 'image/png': ['.png'], 'image/jpeg': ['.jpg', '.jpeg'] },
    maxSize: 2 * 1024 * 1024,
    maxFiles: 1,
    multiple: false,
    disabled: disabled || state === 'UPLOADING',
    noClick: true,
    noKeyboard: true,
    onDrop: (accepted, rejected) => {
      if (state === 'KEEP' || state === 'READY' || state === 'REMOVE') {
        beforeSelection.current = { edit: edit.current, previewUrl, state }
      }
      generation.current += 1
      setPreviewUrl(null)
      setSourceUrl(null)
      setCrop(undefined)
      onChange({ action: 'REPLACE', ready: false })
      const file = accepted[0]
      if (rejected.length || !file || !['image/png', 'image/jpeg'].includes(file.type)) {
        setState('ERROR'); setError(t('skillAvatar.invalid')); return
      }
      setSourceUrl(URL.createObjectURL(file))
      setError(''); setState('SELECTED'); setDialogOpen(true)
    },
  })

  return <section className="space-y-2" aria-label={t('skillAvatar.title')}>
    <h2 className="text-sm font-medium">{t('skillAvatar.optionalTitle')}</h2>
    <div {...getRootProps()} data-analytics-id={disabled || state === 'UPLOADING' ? undefined : 'skill.avatar.file.drop'} data-analytics-event="drop" className="flex items-center gap-3">
      {state === 'READY' && previewUrl ? <img src={buildApiUrl(previewUrl)} alt={t('skillAvatar.cropPreview')} className="h-16 w-16 shrink-0 rounded-lg object-cover" />
        : <SkillAvatar name={name} url={state === 'REMOVE' ? null : currentUrl} size="h-16 w-16 shrink-0 text-xl" />}
      <input {...getInputProps({ 'aria-label': t('skillAvatar.choose'), className: 'sr-only', style: undefined, disabled: disabled || state === 'UPLOADING' })} data-analytics-id="skill.avatar.file.select" />
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap gap-1">
          <Button data-analytics-id={currentUrl || state === 'READY' ? 'skill.avatar.change' : 'skill.avatar.choose'} ref={chooseButton} type="button" size="sm" variant="outline" disabled={disabled || state === 'UPLOADING'} onClick={open}>{t(currentUrl || state === 'READY' ? 'skillAvatar.change' : 'skillAvatar.choose')}</Button>
          {(canRemove || state === 'READY') && state !== 'REMOVE' && <Button data-analytics-id="skill.avatar.remove" type="button" size="sm" variant="ghost" disabled={disabled || dialogOpen} onClick={() => reset(canRemove)}>{t('skillAvatar.remove')}</Button>}
          {state !== 'KEEP' && !dialogOpen && <Button data-analytics-id="skill.avatar.reset" type="button" size="sm" variant="ghost" disabled={disabled} onClick={() => reset()}>{t('skillAvatar.discardChange')}</Button>}
        </div>
        {state !== 'KEEP' && !dialogOpen && <p role="status" className="text-xs text-muted-foreground">{t(state === 'READY' ? 'skillAvatar.stagedReady' : state === 'REMOVE' ? 'skillAvatar.removedDraft' : `skillAvatar.uploadStates.${state}`)}</p>}
      </div>
    </div>
    {!dialogOpen && error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    <Dialog open={dialogOpen} onOpenChange={value => { if (!value) cancelSelection() }}>
      <DialogContent closeAnalyticsId="skill.avatar.cancel" className="w-[min(calc(100vw-2rem),26rem)] gap-4 p-4 sm:p-6"
        onCloseAutoFocus={event => { event.preventDefault(); chooseButton.current?.focus() }}>
        <DialogHeader>
          <DialogTitle>{t('skillAvatar.cropTitle')}</DialogTitle>
          <DialogDescription>{t('skillAvatar.cropHelp')}</DialogDescription>
        </DialogHeader>
        {sourceUrl && <div data-analytics-id={disabled || state === 'UPLOADING' ? undefined : 'skill.avatar.crop.adjust'} data-analytics-event="pointerup+keydown" className="flex h-80 max-h-[min(320px,45vh)] min-w-0 items-center justify-center overflow-hidden rounded-lg bg-muted/40">
          <ReactCrop crop={crop} aspect={1} keepSelection minWidth={16} minHeight={16}
            className="max-h-[min(320px,45vh)] max-w-full"
            disabled={disabled || state === 'UPLOADING'}
            onChange={(_, percentCrop) => setCrop(percentCrop)}
            ariaLabels={{
              cropArea: t('skillAvatar.cropArea'),
              nwDragHandle: t('skillAvatar.cropHandles.nw'), nDragHandle: t('skillAvatar.cropHandles.n'),
              neDragHandle: t('skillAvatar.cropHandles.ne'), eDragHandle: t('skillAvatar.cropHandles.e'),
              seDragHandle: t('skillAvatar.cropHandles.se'), sDragHandle: t('skillAvatar.cropHandles.s'),
              swDragHandle: t('skillAvatar.cropHandles.sw'), wDragHandle: t('skillAvatar.cropHandles.w'),
            }}>
            <img ref={image} src={sourceUrl} alt={t('skillAvatar.cropPreview')}
              className="block max-h-[min(320px,45vh)] max-w-full object-contain"
              onLoad={event => {
                const { naturalWidth, naturalHeight } = event.currentTarget
                setCrop(centerCrop(makeAspectCrop({ unit: '%', width: Math.min(75, 75 * naturalHeight / naturalWidth) }, 1, naturalWidth, naturalHeight), naturalWidth, naturalHeight))
              }} onError={() => { setState('ERROR'); setError(t('skillAvatar.invalid')); setCrop(undefined) }} />
          </ReactCrop>
        </div>}
        {state === 'UPLOADING' && <p role="status" className="text-sm text-muted-foreground">{t('skillAvatar.uploadStates.UPLOADING')}</p>}
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <DialogFooter className="flex-row justify-end">
          <Button data-analytics-id="skill.avatar.cancel" type="button" variant="outline" onClick={cancelSelection}>{t('dialog.cancel')}</Button>
          <Button data-analytics-id={state === 'ERROR' ? 'skill.avatar.retry' : 'skill.avatar.upload'} type="button" disabled={disabled || state === 'UPLOADING' || !crop?.width || !crop.height} onClick={() => void upload()}>{t(state === 'ERROR' ? 'skillAvatar.uploadRetry' : 'skillAvatar.cropConfirm')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
}
