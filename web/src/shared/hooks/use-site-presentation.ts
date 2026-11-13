import { createContext, useContext } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PRESENTATION_CONTENT, type PresentationSection } from '@/shared/lib/presentation-content'
import { sitePresentationApi, type SitePresentationSettings } from '@/api/site-presentation'

export const SitePresentationPreviewContext = createContext<SitePresentationSettings | null>(null)

export const SITE_PRESENTATION_QUERY_KEY = ['site-presentation'] as const

export function defaultSitePresentation(): SitePresentationSettings {
  return { siteName: null, logoUrl: null, faviconUrl: null, sections: {
    hero: { enabled: true, texts: {}, items: {} }, curated: { enabled: true, texts: {}, items: {} }, capabilities: { enabled: true, texts: {}, items: {} },
    quickStart: { enabled: true, texts: {}, items: {} }, popular: { enabled: true, texts: {}, items: {} },
    latest: { enabled: true, texts: {}, items: {} }, enterprise: { enabled: true, texts: {}, items: {} },
    cta: { enabled: true, texts: {}, items: {} }, footer: { enabled: true, texts: {}, items: {} },
  }, links: {} }
}

export function localizedPresentationText(values: Record<string, string>, language: string | undefined): string | undefined {
  const locale = language?.toLowerCase().split('-')[0]
  return (locale ? values[locale]?.trim() : undefined) || values.en?.trim() || undefined
}

export function useSitePresentation() {
  const preview = useContext(SitePresentationPreviewContext)
  const query = useQuery({ enabled: preview === null, queryKey: SITE_PRESENTATION_QUERY_KEY, queryFn: sitePresentationApi.get, staleTime: 30_000, retry: false, refetchOnMount: false, meta: { skipGlobalErrorHandler: true } })
  return { ...query, settings: preview ?? query.data?.settings ?? defaultSitePresentation() }
}

/** The allowlist has one authoritative fallback for both the homepage and its settings editor. */
export function presentationText(settings: SitePresentationSettings, section: PresentationSection, slot: string, language: string | undefined, translate: (key: string) => string): string {
  const override = localizedPresentationText(settings.sections[section].texts[slot] ?? {}, language)
  if (override) return override
  const defaults: Record<string, string> = PRESENTATION_CONTENT[section]
  const fallback = defaults[slot]
  if (!fallback) return ''
  if (section === 'enterprise' && slot === 'registryLabel') return `${settings.siteName ?? 'SkillHub'}\nRegistry`
  return fallback.startsWith('=') ? fallback.slice(1) : translate(fallback)
}
