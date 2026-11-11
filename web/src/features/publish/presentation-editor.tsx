import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { useBlocker } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Loader2 } from 'lucide-react'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/shared/ui/dialog'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { cn } from '@/shared/lib/utils'

function LoadFailure({ onError }: { onError: () => void }) {
  useEffect(() => { onError() }, [onError])
  return <></>
}
const RichEditor = lazy(() => import('./presentation-editor-rich').catch(() => ({ default: LoadFailure })))

export interface PresentationEditorProps {
  open: boolean
  value: string
  onApply: (value: string) => void
  onCancel: () => void
  maxLength?: number
}

// Raw HTML and MDX are not converted through a rich-text document: preserving the source
// takes precedence over visual editing of syntax outside the supported Markdown subset.
export function requiresSourceEditing(markdown: string): boolean {
  return /<\/?[a-zA-Z][^>]*>|<!--|^import\s.+from\s|^export\s/m.test(markdown)
}

function EditorSession({ value, onApply, onCancel, maxLength = 20000 }: Omit<PresentationEditorProps, 'open'>) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState(value)
  const [mode, setMode] = useState(requiresSourceEditing(value) ? 'source' : 'visual')
  const [split, setSplit] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [visualFailed, setVisualFailed] = useState(false)
  const [richReady, setRichReady] = useState(false)
  const [richTouched, setRichTouched] = useState(false)
  const readRichDraft = useRef<(() => string | undefined) | null>(null)
  const [imageOpen, setImageOpen] = useState(false)
  const [imageUrl, setImageUrl] = useState('')
  const [imageAlt, setImageAlt] = useState('')
  const [imageError, setImageError] = useState(false)
  const insertImageRef = useRef<((markdown: string) => void) | null>(null)
  const overlayRef = useRef<HTMLDivElement>(null)
  const returnFocusRef = useRef(document.activeElement instanceof HTMLElement ? document.activeElement : null)
  const closingRef = useRef(false)
  const restoreTriggerFocus = (event: Event) => {
    event.preventDefault()
    const restore = () => {
      // The nested confirmation's FocusScope must finish teardown before focus is restored.
      if (returnFocusRef.current?.isConnected && !document.querySelector('[data-testid="presentation-editor"]')) {
        returnFocusRef.current.focus({ preventScroll: true })
      }
    }
    const raf = globalThis.requestAnimationFrame
    if (typeof raf === 'function') raf(() => raf(restore))
    else setTimeout(restore, 0)
  }
  const closeEditor = (close: () => void) => { closingRef.current = true; close() }
  const dirty = draft !== value || richTouched
  const tooLong = draft.length > maxLength
  const visualUnavailable = requiresSourceEditing(draft)
  const blocker = useBlocker({ shouldBlockFn: () => dirty, enableBeforeUnload: dirty, withResolver: true })
  const flushDraft = () => {
    const current = readRichDraft.current?.() ?? draft
    setDraft(current)
    setRichTouched(false)
    return current
  }
  const requestCancel = () => { if (flushDraft() !== value) setDiscardOpen(true); else closeEditor(onCancel) }
  const applyDraft = () => {
    const current = flushDraft()
    if (current.length <= maxLength) closeEditor(() => onApply(current))
  }
  const handleFailure = () => { setVisualFailed(true); setMode('source') }
  const openImage = (insert: (markdown: string) => void) => {
    insertImageRef.current = insert
    setImageUrl('')
    setImageAlt('')
    setImageError(false)
    setImageOpen(true)
  }
  const insertImage = () => {
    try {
      const url = new URL(imageUrl)
      if (url.protocol !== 'https:') throw new Error('Invalid scheme')
      const escapedAlt = imageAlt.replace(/[\\[\]]/g, '\\$&').replace(/[\r\n]/g, ' ')
      // Percent-encode Markdown delimiters rather than interpolating executable markup.
      const safeUrl = url.href.replace(/\(/g, '%28').replace(/\)/g, '%29')
      insertImageRef.current?.(`![${escapedAlt}](${safeUrl})`)
      setImageOpen(false)
    } catch { setImageError(true) }
  }

  return (
    <>
      <Dialog open onOpenChange={(next) => { if (!next) requestCancel() }}>
        <DialogContent
          ref={overlayRef}
          hideClose
          className="!fixed !inset-0 !left-0 !top-0 !flex !h-dvh !max-h-none !w-full !max-w-none !translate-x-0 !translate-y-0 !flex-col !gap-0 !rounded-none !border-0 !bg-background !p-0 !shadow-none"
          onCloseAutoFocus={restoreTriggerFocus}
          onEscapeKeyDown={(event) => { event.preventDefault(); requestCancel() }}
          onInteractOutside={(event) => event.preventDefault()}
          data-testid="presentation-editor"
        >
          <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
            <div className="mx-auto w-full max-w-[1200px] px-4 py-6 sm:px-8 sm:py-10 xl:px-0">
              <Button type="button" variant="ghost" size="sm" className="mb-5 -ml-3" onClick={requestCancel}>
                <ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('publish.editor.cancel')}
              </Button>
              <div className="mb-7 flex flex-wrap items-start justify-between gap-3">
                <div>
                  <DialogTitle className="text-2xl font-semibold sm:text-[30px] sm:leading-9">{t('publish.editor.title')}</DialogTitle>
                  <DialogDescription className="mt-2 text-sm text-muted-foreground">{t('publish.editor.description')}</DialogDescription>
                </div>
                <span className="pt-2 text-xs text-muted-foreground" role="status">{t(dirty ? 'publish.editor.unsaved' : 'publish.editor.unchanged')}</span>
              </div>
              <Tabs value={mode} onValueChange={(next) => { flushDraft(); setMode(next); setRichReady(false) }} className="overflow-hidden rounded-xl border border-border/70 bg-background">
                <div className="px-3 pt-3 sm:px-4">
                  <TabsList className="!inline-flex !gap-1 !rounded-full !border-0 !bg-muted/65 !p-1" aria-label={t('publish.editor.title')}>
                    {(['visual', 'source', 'preview'] as const).map((tab) => (
                      <TabsTrigger key={tab} value={tab} disabled={tab === 'visual' && (visualUnavailable || visualFailed)} className="!rounded-full !border-0 !px-3 !py-2 text-xs sm:!px-4 sm:text-sm data-[state=active]:!bg-background data-[state=active]:!shadow-sm after:!hidden">
                        {t(`publish.editor.${tab}`)}
                      </TabsTrigger>
                    ))}
                  </TabsList>
                </div>
                {(visualUnavailable || visualFailed) && <p className="mx-4 my-3 rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground" role="status">{t(visualFailed ? 'publish.editor.loadFailed' : 'publish.editor.visualUnavailable')}</p>}
                <TabsContent value="visual" className="!mt-3 outline-none">
                  {!richReady && <div className="flex items-center gap-2 px-6 py-4 text-sm text-muted-foreground" role="status"><Loader2 className="h-4 w-4 animate-spin motion-reduce:animate-none" />{t('publish.editor.loading')}</div>}
                  <Suspense fallback={null}>
                    <RichEditor value={draft} onChange={setDraft} onReady={() => setRichReady(true)} onTouched={() => setRichTouched(true)} onRegister={(read) => { readRichDraft.current = read }} onError={handleFailure} onImage={openImage} />
                  </Suspense>
                </TabsContent>
                <TabsContent value="source" className="!mt-3 outline-none">
                  <div className="flex items-center justify-between gap-3 border-b border-border/60 px-4 py-3 text-xs text-muted-foreground">
                    <span>{t('publish.editor.markdownLabel')}</span>
                    <div className="flex shrink-0 gap-1 rounded-md bg-muted/60 p-0.5" role="group" aria-label={t('publish.editor.source')}>
                      {[false, true].map((isSplit) => <Button key={String(isSplit)} type="button" size="sm" variant="ghost" aria-pressed={split === isSplit} onClick={() => setSplit(isSplit)} className={cn('h-7 px-3 text-xs', split === isSplit && 'bg-background shadow-sm')}>{t(isSplit ? 'publish.editor.split' : 'publish.editor.sourceOnly')}</Button>)}
                    </div>
                  </div>
                  <div className={cn('grid', split && 'md:grid-cols-2')}>
                    <Textarea aria-label={t('publish.editor.markdownLabel')} value={draft} onChange={(event) => setDraft(event.target.value)} spellCheck={false} aria-invalid={tooLong} aria-describedby={tooLong ? 'presentation-limit-error' : undefined} className="min-h-[480px] resize-y rounded-none border-0 bg-transparent p-6 font-mono text-sm leading-7 shadow-none focus-visible:ring-inset sm:p-8" />
                    {split && <div className="min-w-0 border-t border-border/60 p-6 md:border-l md:border-t-0 sm:p-8"><MarkdownRenderer content={draft} /></div>}
                  </div>
                </TabsContent>
                <TabsContent value="preview" className="!mt-3 min-h-[480px] border-t border-border/60 p-6 outline-none sm:px-12 sm:py-8"><MarkdownRenderer content={draft} /></TabsContent>
              </Tabs>
              <div className="mt-4 flex flex-wrap items-start justify-between gap-2 text-xs text-muted-foreground">
                <p>{t('publish.editor.returnHint')}</p>
                <span className={cn('tabular-nums', tooLong && 'text-destructive')}>{draft.length} / {maxLength}</span>
              </div>
              {tooLong && <p id="presentation-limit-error" role="alert" className="mt-2 text-sm text-destructive">{t('publish.editor.tooLong', { max: maxLength })}</p>}
              <div className="mt-7 flex justify-end gap-3 pb-3">
                <Button type="button" variant="outline" onClick={requestCancel}>{t('publish.editor.cancel')}</Button>
                <Button type="button" disabled={tooLong} onClick={applyDraft}>{t('publish.editor.apply')}</Button>
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>
      <ConfirmDialog onCloseAutoFocus={(event) => { if (closingRef.current) event.preventDefault() }} open={discardOpen || blocker.status === 'blocked'} onOpenChange={(next) => { if (!next) { setDiscardOpen(false); if (blocker.status === 'blocked') blocker.reset() } }} title={t('publish.editor.discardTitle')} description={t('publish.editor.discardDescription')} confirmText={t('publish.editor.discard')} cancelText={t('publish.editor.keepEditing')} onConfirm={() => { const proceed = blocker.status === 'blocked' ? blocker.proceed : undefined; setDiscardOpen(false); closeEditor(onCancel); proceed?.() }} />
      <Dialog open={imageOpen} onOpenChange={setImageOpen}>
        <DialogContent>
          <DialogTitle>{t('publish.editor.insertImage')}</DialogTitle>
          <DialogDescription>{t('publish.editor.imageUrl')}</DialogDescription>
          <div className="space-y-2"><Label htmlFor="presentation-image-url">{t('publish.editor.imageUrl')}</Label><Input id="presentation-image-url" type="url" value={imageUrl} onChange={(event) => { setImageUrl(event.target.value); setImageError(false) }} aria-invalid={imageError} /></div>
          <div className="space-y-2"><Label htmlFor="presentation-image-alt">{t('publish.editor.imageAlt')}</Label><Input id="presentation-image-alt" value={imageAlt} onChange={(event) => setImageAlt(event.target.value)} /></div>
          {imageError && <p className="text-sm text-destructive" role="alert">{t('publish.editor.invalidImageUrl')}</p>}
          <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => setImageOpen(false)}>{t('dialog.cancel')}</Button><Button type="button" onClick={insertImage}>{t('publish.editor.insert')}</Button></div>
        </DialogContent>
      </Dialog>
    </>
  )
}

export function PresentationEditor({ open, ...props }: PresentationEditorProps) {
  return open ? <EditorSession {...props} /> : null
}
