import { Button } from '@/shared/ui/button'
import { useStar, useToggleStar } from './use-star'
import { Star } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'

interface StarButtonProps {
  skillId: number
  starCount: number
  onRequireLogin?: () => void
}

export function StarButton({ skillId, starCount, onRequireLogin }: StarButtonProps) {
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
      onClick={handleToggle}
      disabled={toggleMutation.isPending}
    >
      <Star className={`w-4 h-4 mr-2 ${starStatus.starred ? 'fill-current' : ''}`} />
      {starStatus.starred ? '已收藏' : '收藏'} ({starCount})
    </Button>
  )
}
