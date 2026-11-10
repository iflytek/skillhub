import { useRef, useState, type ChangeEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useBlocker } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ImagePlus } from 'lucide-react'
import { ApiError } from '@/api/client'
import { presentationAssetUrl, sitePresentationApi, type SitePresentationResponse, type SitePresentationSettings } from '@/api/site-presentation'
import { defaultSitePresentation, presentationText, SITE_PRESENTATION_QUERY_KEY } from '@/shared/hooks/use-site-presentation'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { withBasePath } from '@/shared/lib/base-path'
import { PRESENTATION_CONTENT, PRESENTATION_ITEMS, PRESENTATION_LINKS, type PresentationSection } from '@/shared/lib/presentation-content'

const ADMIN_QUERY_KEY = ['system-config', 'presentation'] as const
const LOCALES = ['zh', 'en', 'ko', 'ru'] as const
const sameSettings = (left: SitePresentationSettings, right: SitePresentationSettings) => JSON.stringify(left) === JSON.stringify(right)
type Draft = SitePresentationResponse & { original: SitePresentationSettings }

function AssetUpload({ name, value, disabled, onChange, onBusy }: { name: 'logoUrl' | 'faviconUrl'; value: string | null | undefined; disabled: boolean; onChange: (value: string | null) => void; onBusy: (busy: boolean) => void }) {
  const { t } = useTranslation()
  const input = useRef<HTMLInputElement>(null)
  const [error, setError] = useState('')
  const [failedUrl, setFailedUrl] = useState<string>()
  const upload = useMutation({
    mutationFn: sitePresentationApi.upload,
    onMutate: () => { onBusy(true); setError('') },
    onSuccess: (response) => onChange(response.url),
    onError: (cause) => setError(cause instanceof Error ? cause.message : t('sitePresentation.uploadFailed')),
    onSettled: () => onBusy(false),
  })
  const receive = (file: File | undefined) => {
    if (!file || disabled || upload.isPending) return
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 2 * 1024 * 1024) { setError(t('sitePresentation.assetHelp')); return }
    upload.mutate(file)
  }
  const changeFile = (event: ChangeEvent<HTMLInputElement>) => { receive(event.target.files?.[0]); event.target.value = '' }
  const src = presentationAssetUrl(value)
  return <div className="min-w-0 space-y-2">
    <Label htmlFor={`presentation-${name}`}>{t(`sitePresentation.${name}`)}</Label>
    <div className="flex min-h-32 flex-wrap items-center gap-4 rounded-xl border border-dashed border-border bg-secondary/30 p-4" onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); receive(event.dataTransfer.files[0]) }}>
      <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg border bg-background">
        {src && failedUrl !== src ? <img src={src} alt={t(`sitePresentation.${name}`)} className="h-12 w-12 object-contain" onError={() => setFailedUrl(src)} /> : <ImagePlus className="h-6 w-6 text-muted-foreground" aria-hidden />}
      </div>
      <div className="min-w-0 flex-1 space-y-2">
        <input ref={input} id={`presentation-${name}`} aria-label={t(`sitePresentation.${name}`)} type="file" accept="image/png,image/jpeg" disabled={disabled || upload.isPending} onChange={changeFile} className="block w-full text-sm file:mr-3 file:rounded-md file:border file:border-border file:bg-background file:px-3 file:py-2 file:text-foreground" />
        <p className="text-xs leading-relaxed text-muted-foreground">{upload.isPending ? t('sitePresentation.uploading') : t('sitePresentation.assetHelp')}</p>
        {value && <Button type="button" variant="ghost" size="sm" disabled={disabled || upload.isPending} onClick={() => onChange(null)}>{t('sitePresentation.removeAsset')}</Button>}
      </div>
    </div>
    {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
  </div>
}

function SettingsSection({ title, help, onReset, disabled, children }: { title: string; help: string; onReset: () => void; disabled: boolean; children: ReactNode }) {
  const { t } = useTranslation()
  return <section className="space-y-4 border-t pt-6">
    <div className="flex flex-wrap items-start justify-between gap-3"><div className="space-y-1"><h3 className="font-semibold">{title}</h3><p className="text-sm text-muted-foreground">{help}</p></div><Button type="button" variant="ghost" size="sm" disabled={disabled} onClick={onReset}>{t('sitePresentation.restore')}</Button></div>
    {children}
  </section>
}

export function SitePresentationSettingsPanel() {
  const { t, i18n } = useTranslation()
  const client = useQueryClient()
  const query = useQuery({ queryKey: ADMIN_QUERY_KEY, queryFn: sitePresentationApi.getAdmin, meta: { skipGlobalErrorHandler: true } })
  const [draft, setDraft] = useState<Draft | null>(null)
  const [locale, setLocale] = useState(i18n.resolvedLanguage?.split('-')[0] ?? 'en')
  const [preview, setPreview] = useState(false)
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [conflict, setConflict] = useState(false)
  const [resetGroup, setResetGroup] = useState<'brand' | 'links' | PresentationSection | null>(null)
  const value = draft?.settings ?? query.data?.settings
  const dirty = draft !== null && !sameSettings(draft.settings, draft.original)
  const blocker = useBlocker({ shouldBlockFn: () => dirty || uploading, enableBeforeUnload: dirty || uploading, withResolver: true })
  const save = useMutation({
    mutationFn: (request: SitePresentationResponse) => sitePresentationApi.update(request),
    onSuccess: (response) => {
      client.setQueryData(ADMIN_QUERY_KEY, response)
      client.setQueryData(SITE_PRESENTATION_QUERY_KEY, response)
      setDraft(null); setError(''); setConflict(false); setSaved(true)
    },
    onError: (cause) => { setSaved(false); setConflict(cause instanceof ApiError && cause.status === 409); setError(cause instanceof Error ? cause.message : t('sitePresentation.saveFailed')) },
  })
  const edit = (patch: Partial<SitePresentationSettings>) => {
    if (!query.data) return
    setDraft((current) => ({ settings: { ...(current?.settings ?? query.data!.settings), ...patch }, version: current?.version ?? query.data!.version, original: current?.original ?? query.data!.settings }))
    setSaved(false)
  }
  const reset = () => {
    const defaults = defaultSitePresentation()
    if (resetGroup === 'brand') edit({ siteName: defaults.siteName, logoUrl: defaults.logoUrl, faviconUrl: defaults.faviconUrl })
    if (resetGroup === 'links') edit({ links: {} })
    if (resetGroup && resetGroup !== 'brand' && resetGroup !== 'links' && value) edit({ sections: { ...value.sections, [resetGroup]: defaults.sections[resetGroup] } })
    setResetGroup(null)
  }
  const busy = uploading || save.isPending
  const textFieldLabel = (section: PresentationSection, slot: string) => {
    const suffix = slot.endsWith('Description') ? 'Description' : slot.endsWith('Title') ? 'Title' : undefined
    if (suffix) {
      const prefix = slot.slice(0, -suffix.length)
      const fallback: Record<string, string> = PRESENTATION_CONTENT[section]
      const titleKey = fallback[`${prefix}Title`]
      if (titleKey && !titleKey.startsWith('=')) return `${t(titleKey)} · ${t(`sitePresentation.fields.${suffix.toLowerCase()}`)}`
    }
    return t(`sitePresentation.fields.${slot}`)
  }
  const sectionIds = Object.keys(PRESENTATION_CONTENT) as PresentationSection[]
  return <Card>
    <CardHeader><CardTitle>{t('sitePresentation.title')}</CardTitle><p className="text-sm text-muted-foreground">{t('sitePresentation.intro')}</p></CardHeader>
    <CardContent>
      {query.isPending && <p role="status">{t('common.loading')}</p>}
      {query.isError && <div role="alert" className="space-y-3"><p>{t('sitePresentation.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('sitePresentation.retry')}</Button></div>}
      {value && <form className="space-y-6" onSubmit={(event) => { event.preventDefault(); if (draft && dirty && !busy) save.mutate({ settings: draft.settings, version: draft.version }) }}>
        <fieldset disabled={busy} className="min-w-0 space-y-6">
          <SettingsSection title={t('sitePresentation.brandTitle')} help={t('sitePresentation.brandHelp')} disabled={busy} onReset={() => setResetGroup('brand')}>
            <div className="space-y-2"><Label htmlFor="presentation-name">{t('sitePresentation.siteName')}</Label><Input id="presentation-name" maxLength={80} value={value.siteName ?? ''} placeholder="SkillHub" onChange={(event) => edit({ siteName: event.target.value || null })} /><p className="text-xs text-muted-foreground">{t('sitePresentation.defaultHelp')}</p></div>
            <div className="grid gap-5 xl:grid-cols-2">
              <AssetUpload name="logoUrl" value={value.logoUrl} disabled={busy} onBusy={setUploading} onChange={(logoUrl) => edit({ logoUrl })} />
              <AssetUpload name="faviconUrl" value={value.faviconUrl} disabled={busy} onBusy={setUploading} onChange={(faviconUrl) => edit({ faviconUrl })} />
            </div>
          </SettingsSection>
          <section className="space-y-4 border-t pt-6">
            <div className="space-y-1"><h3 className="font-semibold">{t('sitePresentation.homeTitle')}</h3><p className="text-sm text-muted-foreground">{t('sitePresentation.homeHelp')}</p></div>
            <div className="space-y-2"><Label htmlFor="presentation-language">{t('sitePresentation.textLanguage')}</Label><Select value={locale} onValueChange={setLocale}><SelectTrigger id="presentation-language" className="w-full sm:max-w-64"><SelectValue /></SelectTrigger><SelectContent>{LOCALES.map((language) => <SelectItem key={language} value={language}>{t(`sitePresentation.language.${language}`)}</SelectItem>)}</SelectContent></Select><p className="text-xs leading-relaxed text-muted-foreground">{t('sitePresentation.languageHelp')}</p></div>
            <div className="space-y-3">{sectionIds.map((section) => {
              const config = value.sections[section]
              return <div key={section} className="min-w-0 rounded-lg border">
                <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                  <label className="flex items-center gap-3 text-sm font-medium"><input type="checkbox" checked={config.enabled} onChange={(event) => edit({ sections: { ...value.sections, [section]: { ...config, enabled: event.target.checked } } })} />{t(`sitePresentation.regions.${section}`)}</label>
                  <Button type="button" variant="ghost" size="sm" disabled={busy} onClick={() => setResetGroup(section)} aria-label={`${t('sitePresentation.restore')} · ${t(`sitePresentation.regions.${section}`)}`}>{t('sitePresentation.restore')}</Button>
                </div>
                <details className="border-t" onToggle={(event) => { const open = event.currentTarget.open; setExpanded(current => current[section] === open ? current : { ...current, [section]: open }) }}>
                  <summary className="cursor-pointer px-4 py-3 text-sm text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">{t('sitePresentation.editRegion')}</summary>
                  {expanded[section] && <div className="min-w-0 space-y-5 border-t p-4">
                    {PRESENTATION_ITEMS[section].length > 0 && <fieldset className="space-y-3"><legend className="mb-2 text-sm font-medium">{t('sitePresentation.regionItems')}</legend><div className="flex flex-wrap gap-x-6 gap-y-3">{PRESENTATION_ITEMS[section].map(item => <label key={item} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={config.items?.[item] !== false} onChange={(event) => edit({ sections: { ...value.sections, [section]: { ...config, items: { ...config.items, [item]: event.target.checked } } } })} />{t(`sitePresentation.items.${item}`)}</label>)}</div></fieldset>}
                    {section === 'quickStart' && <p className="rounded-md bg-secondary p-3 text-xs leading-relaxed text-muted-foreground">{t('sitePresentation.technicalBoundary')}</p>}
                    <div className="grid min-w-0 gap-4 lg:grid-cols-2">{Object.keys(PRESENTATION_CONTENT[section]).map(slot => {
                      const id = `presentation-${section}-${slot}`
                      const multiline = /description$/i.test(slot) || slot === 'copyright'
                      const props = { id, maxLength: multiline ? 1000 : 160, value: config.texts[slot]?.[locale] ?? '', placeholder: presentationText(defaultSitePresentation(), section, slot, locale, key => t(key, { lng: locale })), onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => edit({ sections: { ...value.sections, [section]: { ...config, texts: { ...config.texts, [slot]: { ...config.texts[slot], [locale]: event.target.value } } } } }) }
                      return <div key={slot} className={`min-w-0 space-y-2${multiline ? ' lg:col-span-2' : ''}`}><Label htmlFor={id}>{textFieldLabel(section, slot)}</Label>{multiline ? <Textarea {...props} className="min-h-24" /> : <Input {...props} />}</div>
                    })}</div>
                  </div>}
                </details>
              </div>
            })}</div>
          </section>
          <SettingsSection title={t('sitePresentation.linksTitle')} help={t('sitePresentation.linksHelp')} disabled={busy} onReset={() => setResetGroup('links')}>
            <details><summary className="cursor-pointer py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">{t('sitePresentation.editLinks')}</summary><div className="grid min-w-0 gap-4 pt-4 lg:grid-cols-2">{Object.entries(PRESENTATION_LINKS).map(([slot, fallback]) => <div key={slot} className="min-w-0 space-y-2"><Label htmlFor={`presentation-link-${slot}`}>{t(`sitePresentation.linkLabels.${slot}`)}</Label><Input id={`presentation-link-${slot}`} maxLength={2048} placeholder={fallback} value={value.links[slot] ?? ''} onChange={(event) => edit({ links: { ...value.links, [slot]: event.target.value } })} /></div>)}</div></details>
          </SettingsSection>
        </fieldset>
        {error && <div role="alert" className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"><p>{conflict ? t('sitePresentation.conflict') : error}</p>{conflict && <Button type="button" variant="outline" onClick={() => void query.refetch()}>{t('sitePresentation.retry')}</Button>}</div>}
        <div className="flex flex-wrap items-center justify-between gap-4 border-t pt-5">
          <p role="status" className="text-sm text-muted-foreground">{saved ? t('sitePresentation.saved') : dirty ? t('sitePresentation.unsaved') : t('sitePresentation.current')}</p>
          <div className="flex flex-wrap gap-3"><Button type="button" variant="outline" disabled={busy} onClick={() => setPreview(true)}>{t('sitePresentation.preview')}</Button><Button type="submit" disabled={!dirty || busy || conflict}>{save.isPending ? t('sitePresentation.saving') : t('sitePresentation.save')}</Button></div>
        </div>
        {conflict && <p className="text-sm text-muted-foreground">{t('sitePresentation.conflictHelp')} <Button type="button" variant="ghost" onClick={() => { setDraft(null); setConflict(false); setError(''); void query.refetch() }}>{t('sitePresentation.discardReload')}</Button></p>}
      </form>}
      <ConfirmDialog open={resetGroup !== null} onOpenChange={(open) => { if (!open) setResetGroup(null) }} title={t('sitePresentation.restoreTitle')} description={t('sitePresentation.restoreHelp')} onConfirm={reset} />
      <ConfirmDialog open={blocker.status === 'blocked'} onOpenChange={(open) => { if (!open && blocker.status === 'blocked') blocker.reset() }} title={t('sitePresentation.leaveTitle')} description={t('sitePresentation.leaveHelp')} confirmText={t('sitePresentation.leave')} cancelText={t('sitePresentation.keepEditing')} onConfirm={() => { if (blocker.status === 'blocked') blocker.proceed() }} />
      <Dialog open={preview} onOpenChange={setPreview}><DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl"><DialogHeader><DialogTitle>{t('sitePresentation.previewTitle')}</DialogTitle><DialogDescription>{t('sitePresentation.previewHelp')}</DialogDescription></DialogHeader>{value && <div className="space-y-5 rounded-xl border bg-background p-5">
        <div className="flex min-w-0 items-center gap-3 border-b pb-4">{value.logoUrl && <img src={presentationAssetUrl(value.logoUrl)} alt="" className="h-8 w-8 object-contain" onError={(event) => { if (!event.currentTarget.src.endsWith('/favicon.svg')) event.currentTarget.src = withBasePath('/favicon.svg') }} />}<strong className="[overflow-wrap:anywhere]">{value.siteName || 'SkillHub'}</strong></div>
        {sectionIds.filter(section => value.sections[section].enabled).map(section => <section key={section} className="space-y-2 border-b pb-4 last:border-0"><p className="text-xs font-medium text-muted-foreground">{t(`sitePresentation.regions.${section}`)}</p>{section !== 'footer' && <h3 className="whitespace-pre-wrap text-lg font-semibold [overflow-wrap:anywhere]">{presentationText(value, section, 'title', i18n.resolvedLanguage, t)}</h3>}<p className="whitespace-pre-wrap text-sm text-muted-foreground [overflow-wrap:anywhere]">{presentationText(value, section, 'description', i18n.resolvedLanguage, t)}</p>{PRESENTATION_ITEMS[section].length > 0 && <p className="text-xs text-muted-foreground">{PRESENTATION_ITEMS[section].filter(item => value.sections[section].items?.[item] !== false).map(item => t(`sitePresentation.items.${item}`)).join(' · ')}</p>}</section>)}
      </div>}</DialogContent></Dialog>
    </CardContent>
  </Card>
}
