import type { ManagedNamespace, NamespaceRole } from '@/api/types'

const GLOBAL_REVIEW_PLATFORM_ROLES = ['SKILL_ADMIN', 'USER_ADMIN', 'SUPER_ADMIN'] as const

export function buildGlobalReviewsPath() {
  return '/dashboard/reviews'
}

export function buildNamespaceReviewsPath(slug: string) {
  return `/dashboard/namespaces/${encodeURIComponent(slug)}/reviews`
}

export function buildNamespaceReviewDetailPath(slug: string, reviewId: number) {
  return `/dashboard/namespaces/${encodeURIComponent(slug)}/reviews/${reviewId}`
}

export function canAccessGlobalReviewCenter(platformRoles?: readonly string[]) {
  return GLOBAL_REVIEW_PLATFORM_ROLES.some((role) => platformRoles?.includes(role))
}

export function canManageNamespaceReviews(role?: NamespaceRole) {
  return role === 'OWNER' || role === 'ADMIN'
}

export function getPreferredNamespaceReviewEntry(
  namespaces?: readonly ManagedNamespace[],
) {
  if (!namespaces?.length) {
    return null
  }

  const manageableNamespaces = namespaces.filter((namespace) =>
    namespace.type === 'TEAM' && canManageNamespaceReviews(namespace.currentUserRole),
  )
  if (manageableNamespaces.length === 0) {
    return null
  }

  const activeNamespace = manageableNamespaces.find((namespace) => namespace.status === 'ACTIVE')
  return activeNamespace ?? manageableNamespaces[0]
}

export function canAccessReviewCenter(
  platformRoles?: readonly string[],
  namespaces?: readonly ManagedNamespace[],
) {
  return canAccessGlobalReviewCenter(platformRoles) || getPreferredNamespaceReviewEntry(namespaces) !== null
}
