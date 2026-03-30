import { useTranslation } from 'react-i18next'
import { Share2, Check } from 'lucide-react'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { getBaseUrl } from './install-command'

interface ShareButtonProps {
  namespace: string
  slug: string
  description?: string
}

/**
 * Build share text for a skill with full description
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
  const fullDesc = description || t('skillDetail.share.defaultDescription')

  return `${displayName}\n${fullDesc}\n${skillUrl}`
}

export function ShareButton({ namespace, slug, description }: ShareButtonProps) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()

  const handleShare = async () => {
    try {
      const baseUrl = getBaseUrl()
      const shareText = buildShareText(namespace, slug, description, baseUrl, t)
      await copy(shareText)
    } catch (err) {
      console.error('Failed to copy share text:', err)
    }
  }

  return (
    <button
      type="button"
      onClick={handleShare}
      className="relative w-full overflow-hidden rounded-xl border border-border/60 bg-muted/50 px-4 py-3 transition-colors hover:bg-muted/70 active:bg-muted/80"
    >
      <div className="flex items-center justify-center gap-2">
        {copied ? <Check className="h-4 w-4" /> : <Share2 className="h-4 w-4" />}
        <span className="text-[13px] leading-relaxed text-foreground sm:text-sm">
          {copied ? t('skillDetail.share.copied') : t('skillDetail.share.button')}
        </span>
      </div>
    </button>
  )
}
