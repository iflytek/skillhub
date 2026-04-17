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
  SearchBar: () => <div data-testid="search-bar">search-bar</div>,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => null,
}))

vi.mock('@/shared/components/quick-start', () => ({
  QuickStartSection: () => <section data-testid="quick-start">quick-start</section>,
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

import { renderToStaticMarkup } from 'react-dom/server'
import { HomePage } from './home'

describe('HomePage', () => {
  it('exports a named component function', () => {
    expect(typeof HomePage).toBe('function')
  })

  it('renders only the required home blocks', () => {
    const html = renderToStaticMarkup(<HomePage />)

    expect(html).toContain('search-bar')
    expect(html).toContain('home.browseSkills')
    expect(html).toContain('home.publishSkill')
    expect(html).toContain('home.popularTitle')
    expect(html).toContain('home.latestTitle')
    expect(html).toContain('quick-start')

    expect(html).not.toContain('SkillHub')
    expect(html).not.toContain('home.subtitle')
    expect(html).not.toContain('home.description')
    expect(html).not.toContain('home.popularDescription')
    expect(html).not.toContain('home.latestDescription')
  })
})
