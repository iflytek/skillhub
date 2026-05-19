import { describe, expect, it } from 'vitest'
import { normalizePublishPrefill } from './publish-prefill'

describe('normalizePublishPrefill', () => {
  it('keeps namespace and normalizes visibility for valid route search params', () => {
    expect(normalizePublishPrefill({
      namespace: 'team-ai',
      visibility: 'private',
    })).toEqual({
      namespace: 'team-ai',
      visibility: 'PRIVATE',
    })
  })

  it('falls back to PUBLIC when visibility is missing or invalid', () => {
    expect(normalizePublishPrefill({
      namespace: 'team-ai',
      visibility: 'internal',
    })).toEqual({
      namespace: 'team-ai',
      visibility: 'PUBLIC',
    })
  })

  it('trims namespace input from search params', () => {
    expect(normalizePublishPrefill({
      namespace: '  team-ml  ',
    })).toEqual({
      namespace: 'team-ml',
      visibility: 'PUBLIC',
    })
  })
})
