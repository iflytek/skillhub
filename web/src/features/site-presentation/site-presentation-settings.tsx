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
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { withBasePath } from '@/shared/lib/base-path'
import { PRESENTATION_CONTENT, PRESENTATION_LINKS, type PresentationSection } from '@/shared/lib/presentation-content'

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

function SettingsSection({ title, help, children }: { title: string; help: string; children: ReactNode }) {
  return <section className="space-y-4 border-t pt-6 first:border-0 first:pt-0">
    <div className="space-y-1"><h3 className="font-semibold">{title}</h3><p className="text-sm text-muted-foreground">{help}</p></div>
    {children}
  </section>
}

export function SitePresentationSettingsPanel() {
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
  const busy = uploading || save.isPending
  return <Card>
    <CardHeader><CardTitle>{t('sitePresentation.title')}</CardTitle><p className="text-sm text-muted-foreground">{t('sitePresentation.intro')}</p></CardHeader>
    <CardContent>
      {query.isPending && <p role="status">{t('common.loading')}</p>}
      {query.isError && <div role="alert" className="space-y-3"><p>{t('sitePresentation.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('sitePresentation.retry')}</Button></div>}
      {value && <form className="space-y-6" onSubmit={(event) => { event.preventDefault(); if (draft && dirty && !busy) save.mutate({ settings: draft.settings, version: draft.version }) }}>
        <fieldset disabled={busy} className="min-w-0 space-y-6">
          <SettingsSection title={t('sitePresentation.siteInformation')} help={t('sitePresentation.siteInformationHelp')}>
            <div className="space-y-2"><Label htmlFor="presentation-name">{t('sitePresentation.siteName')}</Label><Input id="presentation-name" maxLength={80} value={value.siteName ?? ''} placeholder="SkillHub" onChange={(event) => edit({ siteName: event.target.value || null })} /><p className="text-xs text-muted-foreground">{t('sitePresentation.defaultHelp')}</p></div>
            <div className="grid gap-5 xl:grid-cols-2">
              <AssetUpload name="logoUrl" value={value.logoUrl} disabled={busy} onBusy={setUploading} onChange={(logoUrl) => edit({ logoUrl })} />
              <AssetUpload name="faviconUrl" value={value.faviconUrl} disabled={busy} onBusy={setUploading} onChange={(faviconUrl) => edit({ faviconUrl })} />
            </div>
            <div className="grid min-w-0 gap-4 lg:grid-cols-2">{(['documentation', 'repository'] as const).map(slot => <div key={slot} className="min-w-0 space-y-2"><Label htmlFor={`presentation-link-${slot}`}>{t(`sitePresentation.linkLabels.${slot}`)}</Label><Input id={`presentation-link-${slot}`} maxLength={2048} placeholder={PRESENTATION_LINKS[slot]} value={value.links[slot] ?? ''} onChange={(event) => edit({ links: { ...value.links, [slot]: event.target.value } })} /></div>)}</div>
            <p className="text-xs text-muted-foreground">{t('sitePresentation.commonLinksHelp')}</p>
          </SettingsSection>
          <SettingsSection title={t('sitePresentation.introductionTitle')} help={t('sitePresentation.introductionHelp')}>
            <div className="space-y-2"><Label htmlFor="presentation-language">{t('sitePresentation.textLanguage')}</Label><Select value={locale} onValueChange={setLocale}><SelectTrigger id="presentation-language" className="w-full sm:max-w-64"><SelectValue /></SelectTrigger><SelectContent>{LOCALES.map(language => <SelectItem key={language} value={language}>{t(`sitePresentation.language.${language}`)}</SelectItem>)}</SelectContent></Select><p className="text-xs leading-relaxed text-muted-foreground">{t('sitePresentation.languageHelp')}</p></div>
            <div className="space-y-2"><Label htmlFor="presentation-hero-title">{t('sitePresentation.introductionHeading')}</Label><Input id="presentation-hero-title" maxLength={160} value={value.sections.hero.texts.title?.[locale] ?? ''} placeholder={presentationText(defaultSitePresentation(), 'hero', 'title', locale, key => t(key, { lng: locale }))} onChange={event => editIntroduction('title', event.target.value)} /></div>
            <div className="space-y-2"><Label htmlFor="presentation-hero-description">{t('sitePresentation.introductionDescription')}</Label><Textarea id="presentation-hero-description" maxLength={1000} className="min-h-24" value={value.sections.hero.texts.description?.[locale] ?? ''} placeholder={presentationText(defaultSitePresentation(), 'hero', 'description', locale, key => t(key, { lng: locale }))} onChange={event => editIntroduction('description', event.target.value)} /></div>
          </SettingsSection>
          <SettingsSection title={t('sitePresentation.homeContentTitle')} help={t('sitePresentation.homeContentHelp')}>
            <div className="grid gap-x-6 gap-y-4 sm:grid-cols-2">{sectionIds.map(section => <label key={section} className="flex items-center gap-3 text-sm"><input type="checkbox" checked={value.sections[section].enabled} onChange={event => edit({ sections: { ...value.sections, [section]: { ...value.sections[section], enabled: event.target.checked } } })} />{t(`sitePresentation.regions.${section}`)}</label>)}</div>
          </SettingsSection>
        </fieldset>
        {error && <div role="alert" className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"><p>{conflict ? t('sitePresentation.conflict') : error}</p>{conflict && <Button type="button" variant="outline" onClick={() => void query.refetch()}>{t('sitePresentation.retry')}</Button>}</div>}
        <div className="flex flex-wrap items-center justify-between gap-4 border-t pt-5">
          <p role="status" className="text-sm text-muted-foreground">{saved ? t('sitePresentation.saved') : dirty ? t('sitePresentation.unsaved') : t('sitePresentation.current')}</p>
          <div className="flex flex-wrap items-center gap-3"><Button type="button" variant="ghost" disabled={busy} onClick={() => setResetOpen(true)}>{t('sitePresentation.restorePage')}</Button><Button type="submit" disabled={!dirty || busy || conflict}>{save.isPending ? t('sitePresentation.saving') : t('sitePresentation.save')}</Button></div>
        </div>
        <p className="text-sm text-muted-foreground">{dirty ? t('sitePresentation.saveBeforeViewing') : <a href={withBasePath('/')} target="_blank" rel="noreferrer" className="underline underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">{t('sitePresentation.viewHomepage')}</a>}</p>
        {conflict && <p className="text-sm text-muted-foreground">{t('sitePresentation.conflictHelp')} <Button type="button" variant="ghost" onClick={() => { setDraft(null); setConflict(false); setError(''); void query.refetch() }}>{t('sitePresentation.discardReload')}</Button></p>}
      </form>}
      <ConfirmDialog open={resetOpen} onOpenChange={setResetOpen} title={t('sitePresentation.restorePageTitle')} description={t('sitePresentation.restorePageHelp')} onConfirm={reset} />
      <ConfirmDialog open={blocker.status === 'blocked'} onOpenChange={(open) => { if (!open && blocker.status === 'blocked') blocker.reset() }} title={t('sitePresentation.leaveTitle')} description={t('sitePresentation.leaveHelp')} confirmText={t('sitePresentation.leave')} cancelText={t('sitePresentation.keepEditing')} onConfirm={() => { if (blocker.status === 'blocked') blocker.proceed() }} />

    </CardContent>
  </Card>
}
