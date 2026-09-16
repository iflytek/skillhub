import { withBasePath } from '@/shared/lib/base-path'
import { cn } from '@/shared/lib/utils'

interface BrandMarkProps {
  className?: string
  imageClassName?: string
  alt?: string
}

/**
 * 统一展示 SkillHub 项目头像，复用 public/favicon.svg 避免首页、页脚重复绘制字母占位图标。
 */
export function BrandMark({ className, imageClassName, alt = 'SkillHub' }: BrandMarkProps) {
  return (
    <span className={cn('inline-flex items-center justify-center overflow-hidden rounded-xl', className)}>
      <img
        src={withBasePath('/favicon.svg')}
        alt={alt}
        className={cn('h-full w-full object-contain', imageClassName)}
      />
    </span>
  )
}
