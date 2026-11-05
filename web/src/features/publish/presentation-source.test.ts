import { describe, it, expect } from 'vitest'
import { presentationPayload, publishedOverrides } from './presentation-source'
describe('independent presentation choices', () => {
  it.each(['package','custom'] as const)('encodes summary %s independently', short => {
    for (const overview of ['package','custom'] as const) {
      const result = presentationPayload(short, overview, 'S', 'O')
      expect(result.shortDescription).toBe(short === 'custom' ? 'S' : undefined)
      expect(result.overview).toBe(overview === 'custom' ? 'O' : undefined)
      expect(result.resetPresentation).toBe(short === 'package' && overview === 'package')
    }
  })
  it('inherits explicit overrides, never freezes defaults', () => {
    expect(publishedOverrides(JSON.stringify({shortDescriptionOverride:'Custom',frontmatter:{metadata:{overview:'Default'}}}))).toEqual({short:'Custom',overview:undefined})
    expect(publishedOverrides('bad')).toEqual({})
  })
})
