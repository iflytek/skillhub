import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, fetchJson, getCsrfHeaders } from '@/api/client'
import type { SecurityAuditRecord } from './types'

async function fetchSecurityAudits(
  skillId: number,
  versionId: number
): Promise<SecurityAuditRecord[]> {
  try {
    return await fetchJson(`/api/v1/skills/${skillId}/versions/${versionId}/security-audit`)
  } catch (error) {
    // Treat 404 (no audit exists) as empty — this is the expected state
    // for skills that have not been scanned.
    if (error instanceof ApiError && error.status === 404) {
      return []
    }
    throw error
  }
}

export function useSecurityAudits(
  skillId: number | undefined,
  versionId: number | undefined
) {
  return useQuery({
    queryKey: ['security-audits', skillId, versionId],
    queryFn: () => fetchSecurityAudits(skillId!, versionId!),
    enabled: !!skillId && !!versionId,
    staleTime: 30_000,
    // Most versions have no audit; avoid retrying on expected empty/404.
    retry: false,
  })
}

export function useRetrySecurityScan(skillId: number, versionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => fetchJson(`/api/v1/skills/${skillId}/versions/${versionId}/security-audit/retry`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['security-audits', skillId, versionId] })
      void queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}
