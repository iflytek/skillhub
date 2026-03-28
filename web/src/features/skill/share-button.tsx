import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Share2, Check } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { copyToClipboard } from '@/shared/lib/clipboard'
import { getBaseUrl } from './install-command'

interface ShareButtonProps {
  namespace: string
  slug: string
  description?: string
}

/**
 * Build share text for a skill (max 30 characters as per requirement)
 */
export function buildShareText(
  namespace: string,
  slug: string,
  description: string | undefined,
  baseUrl: string,
  t: (key: string) => string,
): string {
  const skillUrl = `${baseUrl}/space/${namespace}/${slug}`
  const displayName = namespace === 'global' ? slug : `${namespace}/${slug}`

  // Truncate description to fit within 30 char limit
  const maxDescLength = 30 - displayName.length - 3 // 3 for " - "
  let shortDesc = description || t('skillDetail.share.defaultDescription')

  if (shortDesc.length > maxDescLength) {
    shortDesc = shortDesc.slice(0, maxDescLength - 1) + '…'
  }

  return `${displayName} - ${shortDesc}\n${skillUrl}`
}

export function ShareButton({ namespace, slug, description }: ShareButtonProps) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  const handleShare = async () => {
    try {
      const baseUrl = getBaseUrl()
      const shareText = buildShareText(namespace, slug, description, baseUrl, t)

      await copyToClipboard(shareText)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('Failed to copy share text:', err)
    }
  }

  return (
    <Button
      type="button"
      variant="outline"
      size="sm"
      onClick={handleShare}
      title={copied ? t('skillDetail.share.copied') : t('skillDetail.share.button')}
      aria-label={copied ? t('skillDetail.share.copied') : t('skillDetail.share.button')}
      className="gap-2"
    >
      {copied ? <Check className="h-4 w-4" /> : <Share2 className="h-4 w-4" />}
      {copied ? t('skillDetail.share.copied') : t('skillDetail.share.button')}
    </Button>
  )
}
