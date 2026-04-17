import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
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
  SearchBar: ({ placeholder }: { placeholder?: string }) => <div>{placeholder}</div>,
}))

vi.mock('@/shared/components/landing-quick-start', () => ({
  LandingQuickStartSection: () => <section data-testid="quick-start">quick-start</section>,
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: () => null,
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => null,
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
import { LandingPage } from './landing'

describe('LandingPage', () => {
  it('exports a named component function', () => {
    expect(typeof LandingPage).toBe('function')
  })

  it('renders only required sections for the home route', () => {
    const html = renderToStaticMarkup(<LandingPage />)

    expect(html).toContain('landing.hero.searchPlaceholder')
    expect(html).toContain('landing.hero.exploreSkills')
    expect(html).toContain('landing.hero.publishSkill')
    expect(html).toContain('home.popularTitle')
    expect(html).toContain('home.latestTitle')
    expect(html).toContain('quick-start')

    expect(html).not.toContain('SkillHub')
    expect(html).not.toContain('landing.hero.title')
    expect(html).not.toContain('landing.hero.subtitle')
    expect(html).not.toContain('landing.stats.skills')
    expect(html).not.toContain('landing.whySkillHub.title')
  })
})
