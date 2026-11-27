import { describe, expect, it } from 'vitest'
import { pickCrossSourceTwin } from './cross-source-twin'
import type { SkillSummary } from '@/api/types'

function summary(overrides: Partial<SkillSummary>): SkillSummary {
  return {
    id: 1, namespace: 'global', slug: 'brainstorming', displayName: 'Brainstorming',
    summary: null, visibility: 'PUBLIC', status: 'ACTIVE', downloadCount: 0, starCount: 0,
    ...overrides,
  } as SkillSummary
}

describe('pickCrossSourceTwin', () => {
  it('returns the same slug from another namespace', () => {
    const items = [summary({ id: 2, namespace: 'github' }), summary({ id: 3, namespace: 'other' })]
    expect(pickCrossSourceTwin(items, { slug: 'brainstorming', namespace: 'global' })?.namespace).toBe('github')
  })

  it('ignores results from the current namespace and different slugs', () => {
    const items = [summary({ id: 1, namespace: 'global' }), summary({ id: 4, slug: 'brainstorming-2', namespace: 'github' })]
    expect(pickCrossSourceTwin(items, { slug: 'brainstorming', namespace: 'global' })).toBeUndefined()
  })

  it('does not invent a twin when the search is empty', () => {
    expect(pickCrossSourceTwin([], { slug: 'brainstorming', namespace: 'global' })).toBeUndefined()
  })
})
