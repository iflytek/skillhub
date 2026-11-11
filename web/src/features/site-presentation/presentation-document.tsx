import { useEffect } from 'react'
import { presentationAssetUrl } from '@/api/site-presentation'
import { useSitePresentation } from '@/shared/hooks/use-site-presentation'
import { withBasePath } from '@/shared/lib/base-path'

export function PresentationDocument() {
  const { settings } = useSitePresentation()
  useEffect(() => {
    document.title = settings.siteName || 'SkillHub'
    const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
    if (!icon) return
    icon.href = presentationAssetUrl(settings.faviconUrl) ?? withBasePath('/favicon.svg')
    icon.type = settings.faviconUrl ? '' : 'image/svg+xml'
    const fallback = () => { icon.href = withBasePath('/favicon.svg'); icon.type = 'image/svg+xml' }
    icon.addEventListener('error', fallback, { once: true })
    return () => icon.removeEventListener('error', fallback)
  }, [settings.siteName, settings.faviconUrl])
  return null
}
