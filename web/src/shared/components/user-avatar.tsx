import { useEffect, useState } from 'react'
import { cn } from '@/shared/lib/utils'

interface UserAvatarProps {
  /** Avatar image URL. Some OIDC providers (e.g. Microsoft Entra) return a
   *  `picture` claim that points at an authenticated endpoint the browser
   *  cannot fetch directly, so this can fail to load even when set. */
  src?: string | null
  /** Display name used to derive fallback initials and the accessible label. */
  name: string
  /** Applied to both the <img> and the fallback element, so callers keep
   *  full control over size/shape (e.g. `w-8 h-8 rounded-full`). */
  className?: string
  /** Font size/weight for the fallback initials. Defaults to a size that
   *  works for small (~32px) avatars; pass a larger size for bigger ones. */
  textClassName?: string
}

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) {
    return '?'
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase()
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

/**
 * Renders a user's avatar image, falling back to a badge with their
 * initials when there is no image URL or the image fails to load.
 */
export function UserAvatar({ src, name, className, textClassName = 'text-sm font-semibold' }: UserAvatarProps) {
  const [failed, setFailed] = useState(false)

  // Reset the failure flag when the source changes, e.g. after re-login
  // with a different provider or a profile update.
  useEffect(() => {
    setFailed(false)
  }, [src])

  if (!src || failed) {
    return (
      <div
        role="img"
        aria-label={name}
        className={cn(
          'flex items-center justify-center bg-muted text-muted-foreground',
          textClassName,
          className,
        )}
      >
        {getInitials(name)}
      </div>
    )
  }

  return (
    <img
      src={src}
      alt={name}
      loading="lazy"
      className={className}
      onError={() => setFailed(true)}
    />
  )
}
