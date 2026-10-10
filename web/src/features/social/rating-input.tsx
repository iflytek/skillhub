import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Star } from 'lucide-react'
import { useUserRating, useRate } from './use-rating'
import { useAuth } from '@/features/auth/use-auth'

interface RatingInputProps {
  skillId: number
  onRequireLogin?: () => void
}

export function RatingInput({ skillId, onRequireLogin }: RatingInputProps) {
  const { t } = useTranslation()
  const { data: userRating, isLoading } = useUserRating(skillId)
  const rateMutation = useRate(skillId)
  const { isAuthenticated } = useAuth()
  const [hoveredRating, setHoveredRating] = useState<number | null>(null)

  const currentRating = userRating?.rated ? userRating.score : 0

  const handleRate = (rating: number) => {
    if (!isAuthenticated) {
      onRequireLogin?.()
      return
    }
    rateMutation.mutate(rating)
  }

  if (isLoading) {
    return null
  }

  return (
    <div className="flex items-center gap-2">
      <div className="flex items-center gap-1">
        {[1, 2, 3, 4, 5].map((rating) => {
          const isFilled = rating <= (hoveredRating || currentRating)
          return (
            <button
              key={rating}
              type="button"
              className="p-1 hover:scale-110 transition-transform"
              onMouseEnter={() => setHoveredRating(rating)}
              onMouseLeave={() => setHoveredRating(null)}
              onClick={() => handleRate(rating)}
              disabled={rateMutation.isPending}
            >
              <Star
                className={`w-5 h-5 ${
                  isFilled
                    ? 'fill-yellow-400 text-yellow-400'
                    : 'text-gray-300'
                }`}
              />
            </button>
          )
        })}
      </div>
      {currentRating > 0 && (
        <span className="text-sm text-muted-foreground">
          {t('ratingInput.yourRating', { score: currentRating })}
        </span>
      )}
    </div>
  )
}
