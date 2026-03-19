import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { AuditLogItem } from '@/api/types'

/**
 * Admin audit-log query hook with server-side filtering and pagination parameters.
 */
export interface AuditLogParams {
  action?: string
  userId?: string
  requestId?: string
  ipAddress?: string
  resourceType?: string
  resourceId?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

export interface PagedAuditLogs {
  items: AuditLogItem[]
  total: number
  page: number
  size: number
}

async function getAuditLogs(params: AuditLogParams): Promise<PagedAuditLogs> {
  return adminApi.getAuditLogs(params)
}

export function useAuditLog(params: AuditLogParams) {
  return useQuery({
    queryKey: ['admin', 'audit-logs', params],
    queryFn: () => getAuditLogs(params),
  })
}
