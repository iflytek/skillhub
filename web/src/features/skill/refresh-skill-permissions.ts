import type { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/shared/lib/api-error'

/** Re-read server permissions after a rejected write; never retry the write itself. */
export async function refreshSkillPermissionsAfterForbidden(queryClient: QueryClient, error: unknown) {
  if (!(error instanceof ApiError) || error.status !== 403) return
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['skills'] }),
    queryClient.invalidateQueries({ queryKey: ['skill-avatar'] }),
  ])
}
