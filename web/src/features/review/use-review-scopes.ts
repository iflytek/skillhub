import { useQuery } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'

/** Server-authorized scopes, including namespaces a platform reviewer has not joined. */
export function useReviewScopes(enabled = true) {
  return useQuery({ queryKey: ['reviews', 'scopes'], queryFn: () => reviewApi.scopes(), enabled, staleTime: 0 })
}
