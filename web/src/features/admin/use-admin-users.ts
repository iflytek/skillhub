import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchJson, getCsrfHeaders } from '@/api/client'

export interface AdminUser {
  id: string
  username: string
  email: string
  status: 'ACTIVE' | 'DISABLED'
  platformRoles: string[]
  createdAt: string
}

export interface AdminUsersParams {
  search?: string
  status?: string
  page?: number
  size?: number
}

export interface PagedAdminUsers {
  items: AdminUser[]
  total: number
  page: number
  size: number
}

async function getAdminUsers(params: AdminUsersParams): Promise<PagedAdminUsers> {
  const searchParams = new URLSearchParams()
  if (params.search) searchParams.set('search', params.search)
  if (params.status) searchParams.set('status', params.status)
  searchParams.set('page', String(params.page ?? 0))
  searchParams.set('size', String(params.size ?? 20))

  const url = `/api/v1/admin/users?${searchParams.toString()}`
  return fetchJson<PagedAdminUsers>(url)
}

async function updateUserRole(userId: string, role: string): Promise<void> {
  await fetchJson<void>(`/api/v1/admin/users/${userId}/role`, {
    method: 'PUT',
    headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ role }),
  })
}

async function updateUserStatus(userId: string, status: 'ACTIVE' | 'DISABLED'): Promise<void> {
  await fetchJson<void>(`/api/v1/admin/users/${userId}/status`, {
    method: 'PUT',
    headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ status }),
  })
}

export function useAdminUsers(params: AdminUsersParams) {
  return useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => getAdminUsers(params),
  })
}

export function useUpdateUserRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: string }) =>
      updateUserRole(userId, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}

export function useUpdateUserStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, status }: { userId: string; status: 'ACTIVE' | 'DISABLED' }) =>
      updateUserStatus(userId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}
