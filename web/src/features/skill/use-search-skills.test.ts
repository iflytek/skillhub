import { describe, expect, it } from 'vitest'
import { useSearchSkills } from './use-search-skills'

/**
 * use-search-skills.ts is a thin re-export barrel:
 *   export { useSearchSkills } from '@/shared/hooks/use-skill-queries'
 *
 * There is no custom logic, query-key factory, or data transformation
 * to unit-test. We verify the re-export resolves so import-path
 * changes are caught early.
 */
describe('use-search-skills re-export', () => {
  it('re-exports useSearchSkills from shared hooks', () => {
    expect(useSearchSkills).toBeDefined()
    expect(typeof useSearchSkills).toBe('function')
  })
})
