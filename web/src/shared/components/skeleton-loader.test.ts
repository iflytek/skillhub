import { describe, expect, it } from 'vitest'
import * as mod from './skeleton-loader'

/**
 * skeleton-loader.tsx exports two purely presentational React components:
 * SkeletonCard (single shimmer placeholder) and SkeletonList (grid of SkeletonCards).
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('skeleton-loader module exports', () => {
  it('exports the SkeletonCard component', () => {
    expect(mod.SkeletonCard).toBeTypeOf('function')
  })

  it('exports the SkeletonList component', () => {
    expect(mod.SkeletonList).toBeTypeOf('function')
  })
})
