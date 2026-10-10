import { describe, expect, it } from 'vitest'
import { useSkillVersions } from './use-skill-versions'

/**
 * use-skill-versions.ts is a thin re-export barrel:
 *   export { useSkillVersions } from '@/shared/hooks/use-skill-queries'
 *
 * There is no custom logic, query-key factory, or data transformation
 * to unit-test. We verify the re-export resolves so import-path
 * changes are caught early.
 */
describe('use-skill-versions re-export', () => {
  it('re-exports useSkillVersions from shared hooks', () => {
    expect(useSkillVersions).toBeDefined()
    expect(typeof useSkillVersions).toBe('function')
  })
})
