import { describe, expect, it } from 'vitest'
import { useSkillFiles } from './use-skill-files'

/**
 * use-skill-files.ts is a thin re-export barrel:
 *   export { useSkillFiles } from '@/shared/hooks/use-skill-queries'
 *
 * There is no custom logic, query-key factory, or data transformation
 * to unit-test. We verify the re-export resolves so import-path
 * changes are caught early.
 */
describe('use-skill-files re-export', () => {
  it('re-exports useSkillFiles from shared hooks', () => {
    expect(useSkillFiles).toBeDefined()
    expect(typeof useSkillFiles).toBe('function')
  })
})
