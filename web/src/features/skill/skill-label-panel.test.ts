import { describe, expect, it } from 'vitest'
import * as mod from './skill-label-panel'

/**
 * skill-label-panel.tsx exports the SkillLabelPanel React component.
 * It contains several module-private pure helpers (canManageLabelType,
 * resolveDisplayName, toCandidateLabel, sortByPresentation) that encode
 * real business logic but cannot be imported for direct testing.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('skill-label-panel module exports', () => {
  it('exports the SkillLabelPanel component', () => {
    expect(mod.SkillLabelPanel).toBeDefined()
    expect(typeof mod.SkillLabelPanel).toBe('function')
  })
})
