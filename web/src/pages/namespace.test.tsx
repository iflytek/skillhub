import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const buttonRecords: Array<{ label: string }> = []

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({ namespace: 'global' }),
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

vi.mock('@/features/namespace/namespace-header', () => ({
  NamespaceHeader: () => null,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children?: ReactNode }) => {
    const label = Array.isArray(children) ? children.join('') : String(children ?? '')
    buttonRecords.push({ label })
    return <button>{children}</button>
  },
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => null,
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: ({ title }: { title: string }) => <div>{title}</div>,
}))

const useNamespaceDetailMock = vi.fn()
vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useNamespaceDetail: () => useNamespaceDetailMock(),
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSearchSkills: () => ({
    data: {
      items: [
        {
          id: 1,
          displayName: 'Demo Skill',
          summary: 'summary',
          namespace: 'global',
          slug: 'demo',
          downloadCount: 1,
          starCount: 1,
          ratingCount: 0,
          updatedAt: '2026-03-20T00:00:00Z',
          canSubmitPromotion: false,
          publishedVersion: { id: 10, version: '1.0.0', status: 'PUBLISHED' },
        },
      ],
      total: 1,
      page: 0,
      size: 20,
    },
    isLoading: false,
  }),
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { NamespacePage } from './namespace'

describe('NamespacePage', () => {
  beforeEach(() => {
    buttonRecords.length = 0
    useNamespaceDetailMock.mockReturnValue({
      data: { id: 1, slug: 'global', displayName: 'Global', type: 'GLOBAL', status: 'ACTIVE' },
      isLoading: false,
    })
  })

  it('exports a named component function', () => {
    expect(typeof NamespacePage).toBe('function')
  })

  it('renders the not-found state when namespace data is missing', () => {
    useNamespaceDetailMock.mockReturnValue({
      data: null,
      isLoading: false,
    })

    const html = renderToStaticMarkup(<NamespacePage />)
    expect(html).toContain('namespace.notFound')
  })

  it('does not render namespace distribution controls when skills are available', () => {
    const html = renderToStaticMarkup(<NamespacePage />)

    expect(buttonRecords).toHaveLength(0)
    expect(html).not.toContain('type="checkbox"')
  })
})
