import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { useStar, useToggleStar } from './use-star'
import { Bookmark } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'

interface StarButtonProps {
  skillId: number
  starCount: number
  onRequireLogin?: () => void
}

/**
 * Toggles the current user's star state for a skill while delegating the login
 * prompt to the surrounding page. The read query is kept separate so pages can
 * decide independently whether star controls should be rendered at all.
 */
export function StarButton({ skillId, starCount, onRequireLogin }: StarButtonProps) {
  const { t } = useTranslation()
  const { data: starStatus, isLoading } = useStar(skillId)
  const toggleMutation = useToggleStar(skillId)
  const { isAuthenticated } = useAuth()

  const handleToggle = () => {
    if (!isAuthenticated) {
      onRequireLogin?.()
      return
    }
    if (starStatus) {
      toggleMutation.mutate(starStatus.starred)
    }
  }

  if (isLoading || !starStatus) {
    return null
  }

  return (
    <Button
      variant={starStatus.starred ? 'default' : 'outline'}
      size="sm"
      className="justify-between"
      onClick={handleToggle}
      disabled={toggleMutation.isPending}
    >
      <Bookmark className={`w-4 h-4 mr-2 ${starStatus.starred ? 'fill-current' : ''}`} />
      {starStatus.starred ? t('starButton.starred') : t('starButton.star')} ({starCount})
    </Button>
  )
}
