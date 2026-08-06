import { createElement, type ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const useSearchMock = vi.fn()
const selectRecords: Array<{ value?: string }> = []
const useMyNamespacesPageMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  useSearch: () => useSearchMock(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/publish/upload-zone', () => ({
  UploadZone: () => null,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, ...props }: { children: ReactNode }) => createElement('button', props, children),
}))

vi.mock('@/shared/ui/select', () => ({
  Select: ({ children, value }: { children: unknown; value?: string }) => {
    selectRecords.push({ value })
    return children
  },
  SelectContent: ({ children }: { children: unknown }) => children,
  SelectItem: ({ children }: { children: unknown }) => children,
  SelectTrigger: ({ children }: { children: unknown }) => children,
  SelectValue: () => null,
  normalizeSelectValue: (v: string) => v || null,
}))

vi.mock('@/shared/ui/label', () => ({
  Label: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  usePublishSkill: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespacesPage: (...args: unknown[]) => useMyNamespacesPageMock(...args),
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/api/client', () => ({
  ApiError: class ApiError extends Error {
    serverMessageKey?: string
  },
}))

import { PublishPage } from './publish'

describe('PublishPage', () => {
  beforeEach(() => {
    selectRecords.length = 0
    useMyNamespacesPageMock.mockReset()
    useMyNamespacesPageMock.mockImplementation((params: { slug?: string }) => ({
      data: {
        items: params.slug ? [{
          id: 1,
          slug: params.slug,
          displayName: 'Team AI',
          status: 'ACTIVE',
          type: 'TEAM',
        }] : [],
        total: params.slug ? 1 : 0,
        page: 0,
        size: params.slug ? 1 : 20,
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    }))
    useSearchMock.mockReturnValue({
      namespace: '  team-ai  ',
      visibility: 'private',
    })
  })

  it('prefills namespace and visibility from route search params', () => {
    renderToStaticMarkup(createElement(PublishPage))

    expect(useMyNamespacesPageMock).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      status: 'ACTIVE',
      slug: 'team-ai',
    }, true)
    expect(useMyNamespacesPageMock).toHaveBeenCalledWith({ page: 0, size: 20, status: 'ACTIVE' }, false)
    expect(selectRecords[0]?.value).toBe('PRIVATE')
  })

  it('falls back to public visibility when search params are missing', () => {
    useSearchMock.mockReturnValue({})

    renderToStaticMarkup(createElement(PublishPage))

    expect(useMyNamespacesPageMock).toHaveBeenCalledWith({ page: 0, size: 1, status: 'ACTIVE' }, false)
    expect(selectRecords[0]?.value).toBe('PUBLIC')
  })

  it('marks an archived or unavailable prefilled namespace as invalid', () => {
    useMyNamespacesPageMock.mockReturnValue({
      data: { items: [], total: 0, page: 0, size: 1 },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    })

    const html = renderToStaticMarkup(createElement(PublishPage))

    expect(html).toContain('publish.namespaceUnavailable')
  })

  it('distinguishes a namespace validation request failure from an unavailable namespace', () => {
    useMyNamespacesPageMock.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('network down'),
      refetch: vi.fn(),
    })

    const html = renderToStaticMarkup(createElement(PublishPage))

    expect(html).toContain('publish.namespaceValidationError')
    expect(html).toContain('publish.retryNamespaceValidation')
    expect(html).not.toContain('publish.namespaceUnavailable')
  })

  it('exports a named component function', () => {
    expect(typeof PublishPage).toBe('function')
  })
})
