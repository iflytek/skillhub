import { useQuery } from '@tanstack/react-query'
import { securityAuditApi } from '@/api/client'

export function useSecurityAudit(skillId: number, versionId: number, enabled = true) {
  return useQuery({
    queryKey: ['security-audit', skillId, versionId],
    queryFn: () => securityAuditApi.get(skillId, versionId),
    enabled: enabled && skillId > 0 && versionId > 0,
    retry: false, // 404 = no audit, don't retry
  })
}
