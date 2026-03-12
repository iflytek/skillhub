import { useState } from 'react'
import { Star } from 'lucide-react'
import { useUserRating, useRate } from './use-rating'

interface RatingInputProps {
  skillId: number
}

export function RatingInput({ skillId }: RatingInputProps) {
  const { data: userRating, isLoading } = useUserRating(skillId)
  const rateMutation = useRate(skillId)
  const [hoveredRating, setHoveredRating] = useState<number | null>(null)

  const currentRating = userRating?.rating || 0

  const handleRate = (rating: number) => {
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
          你的评分: {currentRating} 星
        </span>
      )}
    </div>
  )
}
