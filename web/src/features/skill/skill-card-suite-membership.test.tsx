// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SkillCard } from './skill-card'

vi.mock('@/api/client', () => ({ buildApiUrl: (url: string) => url }))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => ({ isAuthenticated: false }) }))
vi.mock('@/features/social/use-star', () => ({ useStarredIdSet: () => ({ starredIds: new Set<number>() }) }))
vi.mock('react-i18next', async (importOriginal) => ({
  ...await importOriginal<typeof import('react-i18next')>(),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
}))

const baseSkill = {
  id: 1,
  slug: 'workflow-helper',
  displayName: 'Workflow Helper',
  summary: 'A skill that can also be used independently.',
  namespace: 'team-a',
  downloadCount: 0,
  starCount: 0,
  ratingCount: 0,
  updatedAt: '2026-10-26T00:00:00Z',
  canSubmitPromotion: false,
}

afterEach(cleanup)

describe('SkillCard Suite membership hint', () => {
  it('shows one passive hint when a visible Suite references the skill', () => {
    const skill = { ...baseSkill, hasVisibleSuites: true }
    render(<SkillCard skill={skill} />)

    const badge = screen.getByText('skillCard.relatedSuites')
    expect(badge.getAttribute('title')).toBe('skillCard.relatedSuitesHint')
    expect(badge.closest('a, button')).toBeNull()
    expect(badge.hasAttribute('tabindex')).toBe(false)
    expect(screen.getAllByText('skillCard.relatedSuites')).toHaveLength(1)
  })

  it.each([false, undefined])('does not infer membership when the response flag is %s', (hasVisibleSuites) => {
    const skill = { ...baseSkill, hasVisibleSuites }
    render(<SkillCard skill={skill} />)

    expect(screen.queryByText('skillCard.relatedSuites')).toBeNull()
  })

  it('keeps the existing card navigation for clicking the hint, Enter and Space', () => {
    const onClick = vi.fn()
    const skill = { ...baseSkill, hasVisibleSuites: true }
    render(<SkillCard skill={skill} onClick={onClick} />)

    fireEvent.click(screen.getByText('skillCard.relatedSuites'))
    const card = screen.getByRole('link')
    fireEvent.keyDown(card, { key: 'Enter' })
    fireEvent.keyDown(card, { key: ' ' })
    expect(onClick).toHaveBeenCalledTimes(3)
    fireEvent.keyDown(card, { key: 'ArrowDown' })
    expect(onClick).toHaveBeenCalledTimes(3)
    expect(card.querySelectorAll('a, button')).toHaveLength(0)
  })

  it('retains long names and namespaces with a secondary wrapping hint', () => {
    const skill = {
      ...baseSkill,
      displayName: 'A'.repeat(180),
      namespace: 'team-'.repeat(25),
      hasVisibleSuites: true,
    }
    render(<SkillCard skill={skill} />)

    expect(screen.getByTitle(skill.displayName).textContent).toBe(skill.displayName)
    expect(screen.getByTitle(`@${skill.namespace}`).textContent).toBe(`@${skill.namespace}`)
    expect(screen.getByText('skillCard.relatedSuites').className).toContain('shrink-0')
  })
})
