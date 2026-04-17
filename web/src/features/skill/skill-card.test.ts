// @vitest-environment jsdom

import { createElement } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import * as mod from './skill-card'
import type { SkillSummary } from '@/api/types'

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}))

vi.mock('@/features/social/use-star', () => ({
  useStar: () => ({ data: undefined }),
}))

/**
 * skill-card.tsx exports a single React component (SkillCard).
 * All visual logic is in JSX and depends on hooks (useAuth, useStar).
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('skill-card module exports', () => {
  it('exports the SkillCard component', () => {
    expect(mod.SkillCard).toBeDefined()
    expect(typeof mod.SkillCard).toBe('function')
  })

  it('uses semantic card surface tokens instead of hardcoded white background', () => {
    const sampleSkill: SkillSummary = {
      id: 1,
      slug: 'demo',
      displayName: 'Demo Skill',
      summary: 'Demo summary',
      downloadCount: 12,
      starCount: 3,
      ratingCount: 0,
      namespace: 'global',
      updatedAt: '2026-04-17T12:00:00Z',
      canSubmitPromotion: false,
    }
    const onClick = vi.fn()

    render(createElement(mod.SkillCard, { skill: sampleSkill, onClick }))
    const card = screen.getByRole('link')

    expect(card.className).toContain('bg-card')
    expect(card.className).not.toContain('bg-white')

    fireEvent.click(card)
    expect(onClick).toHaveBeenCalledTimes(1)
  })
})
