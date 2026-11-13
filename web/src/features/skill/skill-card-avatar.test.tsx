// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSummary } from '@/api/types'
import { SkillCard } from './skill-card'

vi.mock('@/api/client', () => ({ buildApiUrl: (url: string) => url }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ isAuthenticated: false }) }))
vi.mock('@/features/social/use-star', () => ({ useStarredIdSet: () => ({ starredIds: new Set<number>() }) }))
vi.mock('react-i18next', async original => ({
  ...await original<typeof import('react-i18next')>(),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}))

const skill: SkillSummary = {
  id: 1, namespace: 'team', slug: 'weather', displayName: 'Weather',
  downloadCount: 0, starCount: 0, ratingCount: 0,
  updatedAt: '2026-11-11T00:00:00Z', canSubmitPromotion: false,
}
afterEach(cleanup)

describe('SkillCard identity across discovery pages', () => {
  it('defaults to the skill initial and keeps its coordinate color when renamed', () => {
    const view = render(<SkillCard skill={skill} />)
    const palette = screen.getByText('W').className
    view.rerender(<SkillCard skill={{ ...skill, displayName: 'Forecast' }} />)
    expect(screen.getByText('F').className).toBe(palette)
    expect(screen.getByText('F').className).not.toContain('bg-primary/10')
  })

  it('assigns varied colors without page-specific avatar props', () => {
    const skills = Array.from({ length: 16 }, (_, index) => ({ ...skill, id: index, slug: `weather-${index}`, displayName: String.fromCharCode(65 + index) }))
    render(<>{skills.map(item => <SkillCard key={item.id} skill={item} />)}</>)
    const colors = skills.map(item => screen.getAllByText(item.displayName).find(node => node.getAttribute('aria-hidden') === 'true')!.className)
    expect(new Set(colors).size).toBeGreaterThanOrEqual(6)
  })
})
