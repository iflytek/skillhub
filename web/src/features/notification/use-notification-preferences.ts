import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationApi } from '@/api/client'
import type { NotificationPreferenceItem } from '@/api/types'

const PREFERENCES_QUERY_KEY = ['notifications', 'preferences'] as const

/**
 * Fetches the current user's notification preferences.
 * When no record exists for a category/channel, the backend defaults to enabled=true.
 */
export function useNotificationPreferences() {
  return useQuery({
    queryKey: PREFERENCES_QUERY_KEY,
    queryFn: () => notificationApi.getPreferences(),
    staleTime: 60_000,
  })
}

/**
 * Mutation to update notification preferences with optimistic update.
 */
export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (preferences: NotificationPreferenceItem[]) =>
      notificationApi.updatePreferences(preferences),
    onMutate: async (newPreferences: NotificationPreferenceItem[]) => {
      await queryClient.cancelQueries({ queryKey: PREFERENCES_QUERY_KEY })
      const previous = queryClient.getQueryData<NotificationPreferenceItem[]>(PREFERENCES_QUERY_KEY)
      queryClient.setQueryData(PREFERENCES_QUERY_KEY, newPreferences)
      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(PREFERENCES_QUERY_KEY, context.previous)
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: PREFERENCES_QUERY_KEY })
    },
  })
}
