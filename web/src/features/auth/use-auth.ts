import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import type { User } from '@/api/types'

export function useAuth(enabled = true) {
  const { data: user, isLoading, error } = useQuery<User | null>({
    queryKey: ['auth', 'me'],
    queryFn: authApi.getMe,
    retry: false,
    enabled,
    staleTime: 5 * 60 * 1000, // 5 分钟
  })

  return {
    user: user ?? null,
    isLoading,
    isAuthenticated: !!user,
    hasRole: (role: string) => user?.platformRoles?.includes(role) ?? false,
    error,
  }
}
