import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/use-auth'
import { getNotificationQueryKeyScope } from './notification-session'
import { notificationEmailApi } from './notification-email-api'

export function useNotificationEmail() {
  const { user } = useAuth()
  const client = useQueryClient()
  const scope = [...getNotificationQueryKeyScope(user?.userId), 'email']
  const contact = useQuery({ queryKey: [...scope, 'contact'], queryFn: notificationEmailApi.contact, enabled: !!user })
  const preferences = useQuery({ queryKey: [...scope, 'preferences'], queryFn: notificationEmailApi.preferences, enabled: !!user })
  const refresh = () => client.invalidateQueries({ queryKey: scope })
  const requestCode = useMutation({ mutationFn: notificationEmailApi.requestCode, onSuccess: refresh })
  const verify = useMutation({ mutationFn: notificationEmailApi.verify, onSuccess: refresh })
  const update = useMutation({ mutationFn: notificationEmailApi.updatePreferences, onSuccess: refresh })
  return { contact, preferences, requestCode, verify, update }
}
