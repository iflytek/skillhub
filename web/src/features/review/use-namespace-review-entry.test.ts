import type { ManagedNamespace } from '@/api/types'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const useMyNamespacesPageMock = vi.hoisted(() => vi.fn())

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespacesPage: (...args: unknown[]) => useMyNamespacesPageMock(...args),
}))

import { useNamespaceReviewEntry } from './use-namespace-review-entry'

function namespace(slug: string, status: ManagedNamespace['status']): ManagedNamespace {
  return {
    id: slug.length,
    slug,
    displayName: slug,
    type: 'TEAM',
    status,
    immutable: false,
    canFreeze: false,
    canUnfreeze: false,
    canArchive: false,
    canRestore: false,
    canDelete: false,
    currentUserRole: 'ADMIN',
    createdAt: '',
  }
}

describe('useNamespaceReviewEntry', () => {
  beforeEach(() => {
    useMyNamespacesPageMock.mockReset()
  })

  it('uses one bounded ACTIVE query when an active review namespace exists', () => {
    const active = namespace('zeta-active', 'ACTIVE')
    const global = { ...namespace('global', 'ACTIVE'), type: 'GLOBAL' as const }
    useMyNamespacesPageMock.mockImplementation((params: { status?: string; type?: string }) => ({
      data: {
        items: params.type !== 'TEAM'
          ? [global]
          : params.status === 'ACTIVE'
            ? [active]
            : [namespace('alpha-archived', 'ARCHIVED')],
        total: 1,
        page: 0,
        size: 1,
      },
      isLoading: false,
      error: null,
    }))

    const result = useNamespaceReviewEntry(false)

    expect(useMyNamespacesPageMock).toHaveBeenNthCalledWith(1, {
      page: 0,
      size: 1,
      status: 'ACTIVE',
      type: 'TEAM',
      roles: ['OWNER', 'ADMIN'],
    }, true)
    expect(useMyNamespacesPageMock).toHaveBeenNthCalledWith(2, {
      page: 0,
      size: 1,
      type: 'TEAM',
      roles: ['OWNER', 'ADMIN'],
    }, false)
    expect(result.namespaceReviewEntry?.slug).toBe('zeta-active')
  })

  it('falls back to one bounded any-status query when no ACTIVE namespace exists', () => {
    const archived = namespace('alpha-archived', 'ARCHIVED')
    useMyNamespacesPageMock.mockImplementation((params: { status?: string }) => ({
      data: {
        items: params.status === 'ACTIVE' ? [] : [archived],
        total: params.status === 'ACTIVE' ? 0 : 1,
        page: 0,
        size: 1,
      },
      isLoading: false,
      error: null,
    }))

    const result = useNamespaceReviewEntry(false)

    expect(useMyNamespacesPageMock).toHaveBeenNthCalledWith(2, {
      page: 0,
      size: 1,
      type: 'TEAM',
      roles: ['OWNER', 'ADMIN'],
    }, true)
    expect(result.namespaceReviewEntry?.slug).toBe('alpha-archived')
  })

  it('disables both namespace queries for global reviewers', () => {
    useMyNamespacesPageMock.mockReturnValue({ data: undefined, isLoading: false, error: null })

    const result = useNamespaceReviewEntry(true)

    expect(useMyNamespacesPageMock.mock.calls.every((call) => call[1] === false)).toBe(true)
    expect(result.namespaceReviewEntry).toBeNull()
  })
})
