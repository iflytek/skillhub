import { describe, expect, it } from 'vitest'
import { isDeleteSlugConfirmationValid, resolveDeletedSkillReturnTo } from './skill-delete-flow'

describe('isDeleteSlugConfirmationValid', () => {
  it('requires an exact slug match', () => {
    expect(isDeleteSlugConfirmationValid('demo-skill', 'demo-skill')).toBe(true)
    expect(isDeleteSlugConfirmationValid('demo-skill', 'Demo-Skill')).toBe(false)
    expect(isDeleteSlugConfirmationValid('demo-skill', 'demo')).toBe(false)
  })
})

describe('resolveDeletedSkillReturnTo', () => {
  it('prefers a safe in-app return path', () => {
    expect(resolveDeletedSkillReturnTo('/dashboard/skills')).toBe('/dashboard/skills')
  })

  it('falls back to search when return path is unsafe or missing', () => {
    expect(resolveDeletedSkillReturnTo('https://example.com')).toBe('/search')
    expect(resolveDeletedSkillReturnTo(undefined)).toBe('/search')
  })
})
