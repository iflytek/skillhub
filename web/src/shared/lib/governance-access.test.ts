import { describe, expect, it } from 'vitest'
import { canViewGovernanceCenter } from './governance-access'

describe('canViewGovernanceCenter', () => {
  it('allows governance reviewers and admins', () => {
    expect(canViewGovernanceCenter(['SKILL_ADMIN'])).toBe(true)
    expect(canViewGovernanceCenter(['NAMESPACE_ADMIN'])).toBe(true)
    expect(canViewGovernanceCenter(['SUPER_ADMIN'])).toBe(true)
  })

  it('hides governance center for users without governance roles', () => {
    expect(canViewGovernanceCenter(['USER'])).toBe(false)
    expect(canViewGovernanceCenter(['AUDITOR'])).toBe(false)
    expect(canViewGovernanceCenter(undefined)).toBe(false)
  })
})
