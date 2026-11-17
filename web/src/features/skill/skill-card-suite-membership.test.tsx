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


describe('market skill cards', () => {
  it('keeps the restored card metadata and existing keyboard navigation', () => {
    const onClick = vi.fn()
    const skill = {
      ...baseSkill,
      displayName: '技能'.repeat(80),
      namespace: 'team-'.repeat(25),
      source: 'platform',
      ownerDisplayName: 'Former card owner row',
      labels: [{ slug: 'writing', displayName: 'Writing' }],
      hasVisibleSuites: true,
    }
    render(<SkillCard variant="market" skill={skill} labels={skill.labels} onClick={onClick} />)

    expect(screen.getByTitle(skill.displayName).textContent).toBe(skill.displayName)
    expect(screen.getByTitle(`@${skill.namespace}`).textContent).toBe(`@${skill.namespace}`)
    expect(screen.getByRole('list').textContent).toBe('Writing')
    expect(screen.getByText('skillCard.source.platform')).toBeDefined()
    expect(screen.getByText('skillCard.relatedSuites')).toBeDefined()
    expect(screen.queryByText(skill.ownerDisplayName)).toBeNull()
    expect(screen.getByTitle(new Date(skill.updatedAt).toLocaleString('en'))).toBeDefined()
    fireEvent.click(screen.getByText('Writing'))
    fireEvent.keyDown(screen.getByRole('link'), { key: 'Enter' })
    fireEvent.keyDown(screen.getByRole('link'), { key: ' ' })
    expect(onClick).toHaveBeenCalledTimes(3)
    expect(screen.getByRole('link').querySelectorAll('button, a')).toHaveLength(0)
  })

  it.each(['platform', 'github'])('shows the %s source alongside the update metadata', (source) => {
    render(<SkillCard variant="market" skill={{ ...baseSkill, source }} />)
    const sourceLabel = screen.getByText(`skillCard.source.${source}`)
    expect(sourceLabel.getAttribute('title')).toBe(`search.source.${source}Hint`)
    expect(sourceLabel.parentElement?.textContent).not.toContain('@team-a')
    expect(sourceLabel.parentElement?.querySelector('svg.lucide-clock')).not.toBeNull()
  })

  it('does not invent labels or provenance when optional fields are absent', () => {
    render(<SkillCard variant="market" skill={baseSkill} />)
    expect(screen.queryByRole('list')).toBeNull()
    expect(screen.queryByText('skillCard.source.platform')).toBeNull()
    expect(screen.queryByText('skillCard.source.github')).toBeNull()
  })

  it('respects an explicit empty label override', () => {
    const skill = { ...baseSkill, labels: [{ slug: 'writing', displayName: 'Writing' }] }
    render(<SkillCard variant="market" skill={skill} labels={[]} />)
    expect(screen.queryByText('Writing')).toBeNull()
  })
})
