import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { User } from '@/api/types'

/**
 * Auth session hook used throughout the app.
 *
 * It polls and refetches the current user aggressively enough to keep role-based UI and protected
 * routes aligned with the latest server session state.
 */
export function getAuthQueryOptions(enabled = true) {
  return {
    queryKey: ['auth', 'me'] as const,
    queryFn: authApi.getMe,
    retry: false,
    enabled,
    staleTime: 0,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    refetchInterval: 60_000,
  }
}

export function useAuth(enabled = true) {
  const { data: user, isLoading, error } = useQuery<User | null>(getAuthQueryOptions(enabled))

  return {
    user: user ?? null,
    isLoading,
    isAuthenticated: !!user,
    hasRole: (role: string) => user?.platformRoles?.includes(role) ?? false,
    error,
  }
}
