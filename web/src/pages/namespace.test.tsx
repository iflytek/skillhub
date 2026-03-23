import { describe, expect, it, vi } from 'vitest'

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
    data: { items: [] },
    isLoading: false,
  }),
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { NamespacePage } from './namespace'

describe('NamespacePage', () => {
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
})
