import { describe, expect, it } from 'vitest'
import * as mod from './use-publish-skill'

/**
 * use-publish-skill.ts is a feature-local re-export of the usePublishSkill
 * hook from the shared query layer. There is no custom logic or
 * transformation to test.
 *
 * We verify the re-export contract so import paths used by publish screens
 * break fast if the module shape changes.
 */
describe('use-publish-skill re-export', () => {
  it('re-exports usePublishSkill as a function', () => {
    expect(mod.usePublishSkill).toBeDefined()
    expect(typeof mod.usePublishSkill).toBe('function')
  })
})
