import { describe, expect, it, vi } from 'vitest'

// HomePage is a component-only page. We verify it exports correctly
// and renders key sections.

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
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

vi.mock('@/features/search/search-bar', () => ({
  SearchBar: () => null,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => null,
}))

vi.mock('@/shared/components/quick-start', () => ({
  QuickStartSection: () => null,
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSearchSkills: () => ({
    data: { items: [] },
    isLoading: false,
  }),
}))

vi.mock('@/shared/lib/search-query', () => ({
  normalizeSearchQuery: (q: string) => q.trim(),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { HomePage } from './home'

describe('HomePage', () => {
  it('exports a named component function', () => {
    expect(typeof HomePage).toBe('function')
  })

  it('renders the hero section with brand name', () => {
    const html = renderToStaticMarkup(<HomePage />)

    expect(html).toContain('SkillHub')
    expect(html).toContain('home.subtitle')
  })
})
