import { useEffect, useRef, useState, type ChangeEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useBlocker } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Eye, Upload } from 'lucide-react'
import { ApiError } from '@/api/client'
import { presentationAssetUrl, sitePresentationApi, type SitePresentationResponse, type SitePresentationSettings } from '@/api/site-presentation'
import { defaultSitePresentation, presentationText, SITE_PRESENTATION_QUERY_KEY } from '@/shared/hooks/use-site-presentation'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogTrigger } from '@/shared/ui/dialog'
import { Switch } from '@/shared/ui/switch'
import { SitePresentationPreview } from './site-presentation-preview'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { withBasePath } from '@/shared/lib/base-path'
import { PRESENTATION_CONTENT, PRESENTATION_LINKS, type PresentationSection } from '@/shared/lib/presentation-content'

const ADMIN_QUERY_KEY = ['system-config', 'presentation'] as const
const LOCALES = ['zh', 'en', 'ko', 'ru'] as const
const normalizeSettings = (value: SitePresentationSettings): SitePresentationSettings => ({ ...value, sections: { ...defaultSitePresentation().sections, ...value.sections } })
const settingsSnapshot = (value: SitePresentationSettings) => JSON.stringify(normalizeSettings(value), (_key, entry: unknown) =>
  entry !== null && typeof entry === 'object' && !Array.isArray(entry)
    ? Object.fromEntries(Object.entries(entry).sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0))
    : entry)
const sameSettings = (left: SitePresentationSettings, right: SitePresentationSettings) => settingsSnapshot(left) === settingsSnapshot(right)
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
    <div className="flex min-h-28 items-center gap-3 rounded-xl border border-dashed border-border bg-secondary/30 p-4" onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); receive(event.dataTransfer.files[0]) }}>
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg border bg-background">
        <img src={src && failedUrl !== src ? src : withBasePath('/favicon.svg')} alt={t(`sitePresentation.${name}`)} className="h-9 w-9 object-contain" onError={() => setFailedUrl(src)} />
      </div>
      <div className="min-w-0 flex-1 space-y-2">
        <input ref={input} id={`presentation-${name}`} aria-label={t(`sitePresentation.${name}`)} type="file" accept="image/png,image/jpeg" disabled={disabled || upload.isPending} onChange={changeFile} className="sr-only" />
        <p className="text-sm font-medium">{value ? t('sitePresentation.currentImage') : t('sitePresentation.defaultImage')}</p><p className="text-xs text-muted-foreground">{t('sitePresentation.assetTypes')}</p><Button type="button" size="sm" variant="outline" disabled={disabled || upload.isPending} onClick={() => input.current?.click()}><Upload aria-hidden className="h-4 w-4" />{upload.isPending ? t('sitePresentation.uploading') : t('sitePresentation.uploadImage')}</Button>
        {value && <Button type="button" variant="ghost" size="sm" disabled={disabled || upload.isPending} onClick={() => onChange(null)}>{t('sitePresentation.removeAsset')}</Button>}
      </div>
    </div>
    {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
  </div>
}

function SettingsSection({ title, help, children }: { title: string; help: string; children: ReactNode }) {
  return <section className="space-y-4 border-t pt-6 first:border-0 first:pt-0">
    <div className="space-y-1"><h3 className="font-semibold">{title}</h3><p className="text-sm text-muted-foreground">{help}</p></div>
    {children}
  </section>
}

export function SitePresentationSettingsPanel({ onDirtyChange, onBusyChange }: { onDirtyChange?: (dirty: boolean) => void; onBusyChange?: (busy: boolean) => void } = {}) {
  const { t, i18n } = useTranslation()
  const client = useQueryClient()
  const query = useQuery({ queryKey: ADMIN_QUERY_KEY, queryFn: sitePresentationApi.getAdmin, meta: { skipGlobalErrorHandler: true } })
  const [draft, setDraft] = useState<Draft | null>(null)
  const [locale, setLocale] = useState<string>(LOCALES.find(language => language === i18n.resolvedLanguage?.split('-')[0]) ?? 'en')
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [conflict, setConflict] = useState(false)
  const [resetOpen, setResetOpen] = useState(false)
  const [tab, setTab] = useState<'identity' | 'home'>('identity')
  const [previewOpen, setPreviewOpen] = useState(false)
  const rawValue = draft?.settings ?? query.data?.settings
  const value = rawValue ? normalizeSettings(rawValue) : undefined
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
  const sectionIds = Object.keys(PRESENTATION_CONTENT) as PresentationSection[]
  const reset = () => {
    if (!value) return
    const defaults = defaultSitePresentation()
    const sections = { ...value.sections }
    for (const section of sectionIds) sections[section] = { ...value.sections[section], enabled: defaults.sections[section].enabled }
    const texts = { ...sections.hero.texts }
    delete texts.title
    delete texts.description
    sections.hero = { ...sections.hero, texts }
    const links = { ...value.links }
    delete links.documentation
    delete links.repository
    edit({ siteName: defaults.siteName, logoUrl: defaults.logoUrl, faviconUrl: defaults.faviconUrl, sections, links })
    setResetOpen(false)
  }
  const editIntroduction = (slot: 'title' | 'description', text: string) => {
    if (!value) return
    const hero = value.sections.hero
    edit({ sections: { ...value.sections, hero: { ...hero, texts: { ...hero.texts, [slot]: { ...hero.texts[slot], [locale]: text } } } } })
  }
  const busy = uploading || save.isPending || query.isFetching
  const callbacks = useRef({ onDirtyChange, onBusyChange })
  callbacks.current = { onDirtyChange, onBusyChange }
  useEffect(() => { callbacks.current.onDirtyChange?.(dirty) }, [dirty])
  useEffect(() => { callbacks.current.onBusyChange?.(busy) }, [busy])
  const discard = async () => {
    setDraft(null); setSaved(false)
    if (conflict) {
      const refreshed = await query.refetch()
      if (refreshed.isError) { setError(t('sitePresentation.loadFailed')); return }
    }
    setConflict(false); setError('')
  }
  return <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
    <section aria-labelledby="site-presentation-title" className="min-w-0 space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1"><h2 id="site-presentation-title" className="text-2xl font-semibold">{t('sitePresentation.title')}</h2><p className="text-sm text-muted-foreground">{t('sitePresentation.intro')}</p></div>
        <DialogTrigger asChild><Button type="button" variant="outline" disabled={!value || busy}><Eye aria-hidden className="h-4 w-4" />{t('sitePresentation.preview')}</Button></DialogTrigger>
      </header>
      {query.isPending && <p role="status">{t('common.loading')}</p>}
      {query.isError && <div role="alert" className="space-y-3"><p>{t('sitePresentation.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('sitePresentation.retry')}</Button></div>}
      {value && <form className="space-y-6" onSubmit={(event) => { event.preventDefault(); if (draft && dirty && !busy && !conflict) save.mutate({ settings: draft.settings, version: draft.version }) }}>
        <div role="group" aria-label={t('sitePresentation.settingsContent')} className="inline-flex max-w-full gap-1 rounded-full border bg-secondary/60 p-1">
          {(['identity', 'home'] as const).map(id => <Button key={id} type="button" variant="ghost" aria-pressed={tab === id} disabled={busy} onClick={() => setTab(id)} className={`rounded-full px-4 ${tab === id ? 'border bg-background text-foreground shadow-sm hover:bg-background' : 'text-muted-foreground'}`}>{t(`sitePresentation.tabs.${id}`)}</Button>)}
        </div>
        <fieldset disabled={busy} className="min-w-0 space-y-6">
          <div hidden={tab !== 'identity'} className="space-y-6">
            <SettingsSection title={t('sitePresentation.brandTitle')} help={t('sitePresentation.brandHelp')}>
              <div className="space-y-2"><Label htmlFor="presentation-name">{t('sitePresentation.siteName')}</Label><Input id="presentation-name" maxLength={80} value={value.siteName ?? ''} placeholder="SkillHub" onChange={(event) => edit({ siteName: event.target.value || null })} /><p className="text-xs text-muted-foreground">{t('sitePresentation.defaultHelp')}</p></div>
              <div className="grid gap-5 sm:grid-cols-2"><AssetUpload name="logoUrl" value={value.logoUrl} disabled={busy} onBusy={setUploading} onChange={(logoUrl) => edit({ logoUrl })} /><AssetUpload name="faviconUrl" value={value.faviconUrl} disabled={busy} onBusy={setUploading} onChange={(faviconUrl) => edit({ faviconUrl })} /></div>
              <p className="text-xs leading-relaxed text-muted-foreground">{t('sitePresentation.assetHelp')}</p>
            </SettingsSection>
            <SettingsSection title={t('sitePresentation.commonLinksTitle')} help={t('sitePresentation.commonLinksHelp')}>
              <div className="grid min-w-0 gap-4 sm:grid-cols-2">{(['documentation', 'repository'] as const).map(slot => <div key={slot} className="min-w-0 space-y-2"><Label htmlFor={`presentation-link-${slot}`}>{t(`sitePresentation.linkLabels.${slot}`)}</Label><Input id={`presentation-link-${slot}`} maxLength={2048} placeholder={PRESENTATION_LINKS[slot]} value={value.links[slot] ?? ''} onChange={(event) => edit({ links: { ...value.links, [slot]: event.target.value } })} /></div>)}</div>
            </SettingsSection>
          </div>
          <div hidden={tab !== 'home'} className="space-y-6">
            <SettingsSection title={t('sitePresentation.introductionTitle')} help={t('sitePresentation.introductionHelp')}>
              <div className="space-y-2"><Label htmlFor="presentation-language">{t('sitePresentation.textLanguage')}</Label><Select value={locale} onValueChange={setLocale}><SelectTrigger id="presentation-language" className="w-full sm:max-w-48"><SelectValue /></SelectTrigger><SelectContent>{LOCALES.map(language => <SelectItem key={language} value={language}>{t(`sitePresentation.language.${language}`)}</SelectItem>)}</SelectContent></Select><p className="text-xs leading-relaxed text-muted-foreground">{t('sitePresentation.languageHelp')}</p></div>
              <div className="space-y-2"><Label htmlFor="presentation-hero-title">{t('sitePresentation.introductionHeading')}</Label><Input id="presentation-hero-title" maxLength={160} value={value.sections.hero.texts.title?.[locale] ?? ''} placeholder={presentationText(defaultSitePresentation(), 'hero', 'title', locale, key => t(key, { lng: locale }))} onChange={event => editIntroduction('title', event.target.value)} /></div>
              <div className="space-y-2"><Label htmlFor="presentation-hero-description">{t('sitePresentation.introductionDescription')}</Label><Textarea id="presentation-hero-description" maxLength={1000} className="min-h-24" value={value.sections.hero.texts.description?.[locale] ?? ''} placeholder={presentationText(defaultSitePresentation(), 'hero', 'description', locale, key => t(key, { lng: locale }))} onChange={event => editIntroduction('description', event.target.value)} /></div>
            </SettingsSection>
            <SettingsSection title={t('sitePresentation.homeContentTitle')} help={t('sitePresentation.homeContentHelp')}>
              <div className="grid overflow-hidden rounded-xl border sm:grid-cols-2">{sectionIds.map((section, index) => <div key={section} className="flex min-w-0 items-center gap-3 border-b px-4 py-4 last:border-b-0 sm:odd:border-r">
                <span aria-hidden className="text-xs tabular-nums text-muted-foreground">{String(index + 1).padStart(2, '0')}</span><span id={`presentation-section-${section}`} className="min-w-0 flex-1 text-sm font-medium">{t(`sitePresentation.regions.${section}`)}</span><span className="text-xs text-muted-foreground">{t(value.sections[section].enabled ? 'sitePresentation.visible' : 'sitePresentation.hidden')}</span><Switch aria-labelledby={`presentation-section-${section}`} checked={value.sections[section].enabled} onCheckedChange={enabled => edit({ sections: { ...value.sections, [section]: { ...value.sections[section], enabled } } })} />
              </div>)}</div>
              <p className="text-xs leading-relaxed text-muted-foreground">{t('sitePresentation.curatedVisibilityHelp')}</p>
            </SettingsSection>
          </div>
        </fieldset>
        {error && <div role="alert" className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"><p>{conflict ? t('sitePresentation.conflict') : error}</p>{conflict && <Button type="button" variant="outline" disabled={busy} onClick={() => void query.refetch()}>{t('sitePresentation.retry')}</Button>}</div>}
        <div className="flex flex-wrap items-center justify-between gap-4 border-t pt-5"><p role="status" className="text-sm text-muted-foreground">{saved ? t('sitePresentation.saved') : dirty ? t('sitePresentation.unsaved') : t('sitePresentation.current')}</p><div className="flex flex-wrap gap-2"><Button type="button" variant="outline" disabled={!dirty || busy} onClick={() => void discard()}>{t('sitePresentation.discard')}</Button><Button type="submit" disabled={!dirty || busy || conflict}>{save.isPending ? t('sitePresentation.saving') : t('sitePresentation.save')}</Button></div></div>
        <div className="flex flex-wrap items-center gap-3"><Button type="button" variant="ghost" disabled={busy} onClick={() => setResetOpen(true)}>{t('sitePresentation.restorePage')}</Button><span className="text-xs text-muted-foreground">{t('sitePresentation.restoreDraftHelp')}</span></div>
        {conflict && <p className="text-sm text-muted-foreground">{t('sitePresentation.conflictHelp')} <Button type="button" variant="ghost" disabled={busy} onClick={() => void discard()}>{t('sitePresentation.discardReload')}</Button></p>}
      </form>}
      <ConfirmDialog open={resetOpen} onOpenChange={setResetOpen} title={t('sitePresentation.restorePageTitle')} description={t('sitePresentation.restorePageHelp')} onConfirm={reset} />
      <ConfirmDialog open={blocker.status === 'blocked'} onOpenChange={(open) => { if (!open && blocker.status === 'blocked') blocker.reset() }} title={t('sitePresentation.leaveTitle')} description={uploading ? t('sitePresentation.uploading') : t('sitePresentation.leaveHelp')} confirmText={t('sitePresentation.leave')} cancelText={t('sitePresentation.keepEditing')} onConfirm={() => { if (!busy && blocker.status === 'blocked') blocker.proceed() }} />
    </section>
    {value && <DialogContent className="flex h-[min(900px,calc(100dvh-2rem))] w-[min(1280px,calc(100vw-2rem))] max-w-none flex-col gap-0 overflow-hidden p-0">
      <DialogHeader className="shrink-0 border-b px-5 py-5 pr-16 sm:px-7 sm:pr-16"><DialogTitle>{t('sitePresentation.previewTitle')}</DialogTitle><DialogDescription>{t('sitePresentation.previewHelp')}</DialogDescription></DialogHeader>
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain bg-secondary/40 p-3 sm:p-5"><SitePresentationPreview settings={value} locale={locale} /></div>
      <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t px-5 py-4"><p className="text-xs text-muted-foreground">{t(`sitePresentation.language.${locale}`)} · {t('sitePresentation.previewReadOnly')}</p><Button type="button" variant="outline" onClick={() => setPreviewOpen(false)}>{t('sitePresentation.keepEditing')}</Button></footer>
    </DialogContent>}
  </Dialog>
}
