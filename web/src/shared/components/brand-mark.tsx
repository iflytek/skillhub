import { withBasePath } from '@/shared/lib/base-path'
import { useState } from 'react'
import { presentationAssetUrl } from '@/api/site-presentation'
import { useSitePresentation } from '@/shared/hooks/use-site-presentation'
import { cn } from '@/shared/lib/utils'

interface BrandMarkProps {
  className?: string
  imageClassName?: string
  alt?: string
}

/**
 * 统一展示 SkillHub 项目头像，复用 public/favicon.svg 避免首页、页脚重复绘制字母占位图标。
 */
export function BrandMark({ className, imageClassName, alt }: BrandMarkProps) {
  const { settings } = useSitePresentation()
  const configured = presentationAssetUrl(settings.logoUrl)
  const [failedUrl, setFailedUrl] = useState<string>()
  const src = configured && configured !== failedUrl ? configured : withBasePath('/favicon.svg')
  return (
    <span className={cn('inline-flex items-center justify-center overflow-hidden rounded-xl', className)}>
      <img
        src={src}
        onError={() => setFailedUrl(configured)}
        alt={alt ?? settings.siteName ?? 'SkillHub'}
        className={cn('h-full w-full object-contain', imageClassName)}
      />
    </span>
  )
}
