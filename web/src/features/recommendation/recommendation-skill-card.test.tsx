// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { RecommendedSkill } from '@/api/recommendations'
import { RecommendationSkillCard } from './recommendation-skill-card'
vi.mock('react-i18next', async original => ({ ...await original<typeof import('react-i18next')>(), useTranslation: () => ({ t: (key: string) => key }) }))
const skill: RecommendedSkill = { id: 1, displayName: 'Meeting summary', slug: 'meeting', namespace: 'global', summary: 'Turn meeting notes into factual actions.', labels: [{ slug: 'office', displayName: 'Office' }], downloadCount: 10, starCount: 2, ratingCount: 0, updatedAt: '', canSubmitPromotion: false, ownerDisplayName: 'Publisher' }
afterEach(cleanup)
describe('editorial recommendation cards', () => {
  it('keeps the real first-letter avatar, labels and keyboard skill entry', () => {
    const onClick = vi.fn()
    render(<RecommendationSkillCard skill={skill} onClick={onClick} />)
    const card = screen.getByRole('link', { name: skill.displayName })
    expect(screen.getByText('M')).toBeTruthy()
    expect(screen.getByText('Office')).toBeTruthy()
    expect(screen.getByText(skill.summary!)).toBeTruthy()
    card.focus()
    fireEvent.keyDown(card, { key: 'Enter' }); fireEvent.keyDown(card, { key: ' ' })
    expect(onClick).toHaveBeenCalledTimes(2)
  })
  it('recovers a failed skill image without losing its link or editorial identity', () => {
    const { container } = render(<RecommendationSkillCard skill={{ ...skill, avatarUrl: '/api/v1/skills/avatar' }} onClick={vi.fn()} />)
    fireEvent.error(container.querySelector('img')!)
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('M')).toBeTruthy()
    expect(screen.getByRole('link', { name: skill.displayName })).toBeTruthy()
  })
})
