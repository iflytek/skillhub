import { useQuery } from '@tanstack/react-query'
import { fetchJson } from '@/api/client'

export interface AuditLog {
  id: number
  action: string
  userId: string
  username?: string
  details?: string
  ipAddress?: string
  timestamp: string
}

export interface AuditLogParams {
  action?: string
  userId?: string
  page?: number
  size?: number
}

export interface PagedAuditLogs {
  items: AuditLog[]
  total: number
  page: number
  size: number
}

async function getAuditLogs(params: AuditLogParams): Promise<PagedAuditLogs> {
  const searchParams = new URLSearchParams()
  if (params.action) searchParams.set('action', params.action)
  if (params.userId) searchParams.set('userId', params.userId)
  searchParams.set('page', String(params.page ?? 0))
  searchParams.set('size', String(params.size ?? 20))

  const url = `/api/v1/admin/audit-logs?${searchParams.toString()}`
  return fetchJson<PagedAuditLogs>(url)
}

export function useAuditLog(params: AuditLogParams) {
  return useQuery({
    queryKey: ['admin', 'audit-logs', params],
    queryFn: () => getAuditLogs(params),
  })
}
