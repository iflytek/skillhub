import { describe, expect, it } from 'vitest'
import { useSkillDetail } from './use-skill-detail'

/**
 * use-skill-detail.ts is a thin re-export barrel:
 *   export { useSkillDetail } from '@/shared/hooks/use-skill-queries'
 *
 * There is no custom logic, query-key factory, or data transformation
 * to unit-test. We verify the re-export resolves so import-path
 * changes are caught early.
 */
describe('use-skill-detail re-export', () => {
  it('re-exports useSkillDetail from shared hooks', () => {
    expect(useSkillDetail).toBeDefined()
    expect(typeof useSkillDetail).toBe('function')
  })
})
