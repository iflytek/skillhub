import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { useSubscription, useToggleSubscription } from './use-subscription'
import { Bell } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'

interface SubscribeButtonProps {
  skillId: number
  subscriptionCount: number
  onRequireLogin?: () => void
}

export function SubscribeButton({ skillId, subscriptionCount, onRequireLogin }: SubscribeButtonProps) {
  const { t } = useTranslation()
  const { data: subscriptionStatus } = useSubscription(skillId)
  const toggleMutation = useToggleSubscription(skillId)
  const { isAuthenticated } = useAuth()

  const handleToggle = () => {
    if (!isAuthenticated) {
      onRequireLogin?.()
      return
    }
    if (subscriptionStatus) {
      toggleMutation.mutate(subscriptionStatus.subscribed)
    }
  }

  if (!subscriptionStatus) {
    return null
  }

  return (
    <Button
      variant={subscriptionStatus.subscribed ? 'default' : 'outline'}
      size="sm"
      className="justify-between"
      onClick={handleToggle}
      disabled={toggleMutation.isPending}
    >
      <Bell className={`w-4 h-4 mr-2 ${subscriptionStatus.subscribed ? 'fill-current' : ''}`} />
      {subscriptionStatus.subscribed ? t('subscribeButton.subscribed') : t('subscribeButton.subscribe')} ({subscriptionCount})
    </Button>
  )
}
