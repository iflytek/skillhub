import { describe, expect, it } from 'vitest'
import { getMySkillEmptyStateKey, getMySkillFilters } from './my-skill-filters'

describe('getMySkillFilters', () => {
  it('returns lifecycle filters for regular users', () => {
    expect(getMySkillFilters(false)).toEqual(['ALL', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'])
  })

  it('adds hidden filter for super admins', () => {
    expect(getMySkillFilters(true)).toEqual(['ALL', 'PUBLISHED', 'HIDDEN', 'ARCHIVED'])
  })

  it('returns default empty-state copy for all skills', () => {
    expect(getMySkillEmptyStateKey('ALL')).toEqual({
      title: 'mySkills.emptyTitle',
      description: 'mySkills.emptyDescription',
    })
  })

  it('returns filter-specific empty-state copy for scoped filters', () => {
    expect(getMySkillEmptyStateKey('PENDING_REVIEW')).toEqual({
      title: 'mySkills.emptyFilteredTitle',
      description: 'mySkills.emptyFilteredDescription.PENDING_REVIEW',
    })
  })
})
