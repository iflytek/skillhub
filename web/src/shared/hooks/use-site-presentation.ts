import { useQuery } from '@tanstack/react-query'
import { sitePresentationApi, type SitePresentationSettings } from '@/api/site-presentation'

export const SITE_PRESENTATION_QUERY_KEY = ['site-presentation'] as const

export function defaultSitePresentation(): SitePresentationSettings {
  return { siteName: null, logoUrl: null, faviconUrl: null, homepageMode: 'LANDING', heroTitle: {}, heroDescription: {}, showPopular: true, showLatest: true, showQuickStart: true, footerVisible: true }
}

export function localizedPresentationText(values: Record<string, string>, language: string | undefined): string | undefined {
  const locale = language?.toLowerCase().split('-')[0]
  return (locale ? values[locale]?.trim() : undefined) || values.en?.trim() || undefined
}

export function useSitePresentation() {
  const query = useQuery({ queryKey: SITE_PRESENTATION_QUERY_KEY, queryFn: sitePresentationApi.get, staleTime: 30_000, retry: false, refetchOnMount: false, meta: { skipGlobalErrorHandler: true } })
  return { ...query, settings: query.data?.settings ?? defaultSitePresentation() }
}
