import { describe, expect, it } from 'vitest'
import * as mod from './use-star'

/**
 * use-star.ts exports useStar and useToggleStar hooks. Both are thin
 * wrappers around useQuery/useMutation with no exported pure helpers,
 * query-key functions, or data transformations.
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('use-star module exports', () => {
  it('exports useStar as a function', () => {
    expect(mod.useStar).toBeDefined()
    expect(typeof mod.useStar).toBe('function')
  })

  it('exports useToggleStar as a function', () => {
    expect(mod.useToggleStar).toBeDefined()
    expect(typeof mod.useToggleStar).toBe('function')
  })
})
