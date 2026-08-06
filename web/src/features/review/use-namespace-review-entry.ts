import { useMyNamespacesPage } from '@/shared/hooks/use-namespace-queries'
import { getPreferredNamespaceReviewEntry } from './review-paths'

const REVIEW_ROLES = ['OWNER', 'ADMIN'] as const

/**
 * Resolves a review namespace with at most two one-row requests: an ACTIVE
 * namespace first, then any manageable namespace as a read-only fallback.
 */
export function useNamespaceReviewEntry(hasGlobalReviewAccess: boolean) {
  const activeQuery = useMyNamespacesPage({
    page: 0,
    size: 1,
    status: 'ACTIVE',
    type: 'TEAM',
    roles: [...REVIEW_ROLES],
  }, !hasGlobalReviewAccess)
  const activeEntry = getPreferredNamespaceReviewEntry(activeQuery.data?.items)
  const fallbackEnabled = !hasGlobalReviewAccess
    && !activeQuery.isLoading
    && !activeQuery.error
    && activeQuery.data !== undefined
    && activeEntry === null
  const fallbackQuery = useMyNamespacesPage({
    page: 0,
    size: 1,
    type: 'TEAM',
    roles: [...REVIEW_ROLES],
  }, fallbackEnabled)
  const fallbackEntry = fallbackEnabled
    ? getPreferredNamespaceReviewEntry(fallbackQuery.data?.items)
    : null

  return {
    namespaceReviewEntry: activeEntry ?? fallbackEntry,
    isLoadingNamespaces: !hasGlobalReviewAccess
      && (activeQuery.isLoading || (fallbackEnabled && fallbackQuery.isLoading)),
    hasNamespaceQueryError: Boolean(activeQuery.error || (fallbackEnabled && fallbackQuery.error)),
    retryNamespaceQueries: () => fallbackEnabled
      ? fallbackQuery.refetch()
      : activeQuery.refetch(),
  }
}
