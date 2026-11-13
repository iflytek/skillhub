import { useEffect, useMemo, useState, type KeyboardEvent, type MouseEvent } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createInstance } from 'i18next'
import { I18nextProvider, useTranslation } from 'react-i18next'
import type { SitePresentationSettings } from '@/api/site-presentation'
import { presentationAssetUrl } from '@/api/site-presentation'
import { LandingPage } from '@/pages/landing'
import { SitePresentationPreviewContext, presentationText } from '@/shared/hooks/use-site-presentation'
import { BrandMark } from '@/shared/components/brand-mark'
import { withBasePath } from '@/shared/lib/base-path'

/** Read the real homepage and public query data without publishing the local form. */
export function SitePresentationPreview({ settings, locale }: { settings: SitePresentationSettings; locale: string }) {
  const { i18n } = useTranslation()
  const [previewClient] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }))
  useEffect(() => () => previewClient.clear(), [previewClient])
  const previewI18n = useMemo(() => {
    // A fresh instance shares translation data, not the browser detector or language cache.
    const instance = createInstance()
    void instance.init({ ...i18n.options, lng: locale, resources: i18n.services.resourceStore.data, initImmediate: false })
    return instance
  }, [i18n, locale])
  return <QueryClientProvider client={previewClient}><I18nextProvider i18n={previewI18n}><SitePresentationPreviewContext.Provider value={settings}>
    <PreviewPage settings={settings} />
  </SitePresentationPreviewContext.Provider></I18nextProvider></QueryClientProvider>
}

function PreviewPage({ settings }: { settings: SitePresentationSettings }) {
  const { t, i18n } = useTranslation()
  const preventNavigation = (event: MouseEvent<HTMLElement>) => { event.preventDefault(); event.stopPropagation() }
  const preventActivation = (event: KeyboardEvent<HTMLElement>) => {
    if (['Enter', ' '].includes(event.key) && (event.target as HTMLElement).closest('a,button,input,[role="button"],[role="link"]')) {
      event.preventDefault(); event.stopPropagation()
    }
  }
  return <div data-homepage-preview lang={i18n.resolvedLanguage} className="overflow-hidden rounded-lg border bg-background text-foreground">
    <div className="flex items-center gap-2 border-b bg-muted/40 px-4 py-2 text-xs text-muted-foreground"><span aria-hidden className="flex gap-1"><i className="h-1.5 w-1.5 rounded-full bg-muted-foreground/40" /><i className="h-1.5 w-1.5 rounded-full bg-muted-foreground/40" /><i className="h-1.5 w-1.5 rounded-full bg-muted-foreground/40" /></span><img alt="" className="ml-2 h-3.5 w-3.5 object-contain" src={presentationAssetUrl(settings.faviconUrl) ?? withBasePath('/favicon.svg')} /><span>{settings.siteName || 'SkillHub'} · {t('nav.landing')}</span></div>
    <div aria-label={t('sitePresentation.previewReadOnly')} onClickCapture={preventNavigation} onAuxClickCapture={preventNavigation} onKeyDownCapture={preventActivation} onSubmitCapture={event => event.preventDefault()} className="[&_.scroll-fade-up]:!translate-y-0 [&_.scroll-fade-up]:!opacity-100 [&_.animate-fade-up]:animate-none [&_.animate-pulse]:animate-none">
      <header className="flex items-center justify-between gap-4 border-b px-5 py-4 sm:px-8"><div className="flex min-w-0 items-center gap-2"><BrandMark className="h-8 w-8 shrink-0" /><strong className="truncate text-xl">{settings.siteName || 'SkillHub'}</strong></div><div className="flex gap-4 text-xs text-muted-foreground"><span>{t('nav.landing')}</span><span>{t('nav.marketplace')}</span></div></header>
      <LandingPage />
      {settings.sections.footer.enabled && <footer className="space-y-2 border-t px-5 py-6 text-sm text-muted-foreground sm:px-8"><div className="flex items-center gap-2"><BrandMark className="h-6 w-6" /><strong className="text-foreground">{settings.siteName || 'SkillHub'}</strong></div><p>{presentationText(settings, 'footer', 'description', i18n.resolvedLanguage, t)}</p><div className="flex gap-4 text-xs"><span>{presentationText(settings, 'footer', 'documentationLabel', i18n.resolvedLanguage, t)}</span><span>{presentationText(settings, 'footer', 'repositoryLabel', i18n.resolvedLanguage, t)}</span></div></footer>}
    </div>
  </div>
}
