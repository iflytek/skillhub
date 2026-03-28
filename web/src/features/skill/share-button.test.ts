import { describe, expect, it } from 'vitest'
import { buildShareText } from './share-button'

describe('buildShareText', () => {
  const mockT = (key: string) => {
    if (key === 'skillDetail.share.defaultDescription') {
      return 'A useful skill'
    }
    return key
  }

  it('builds share text for global namespace skill', () => {
    const result = buildShareText('global', 'my-skill', 'Test description', 'https://skill.example.com', mockT)

    expect(result).toContain('my-skill')
    expect(result).toContain('https://skill.example.com/space/global/my-skill')
  })

  it('builds share text for namespaced skill', () => {
    const result = buildShareText('team-alpha', 'my-skill', 'Test description', 'https://skill.example.com', mockT)

    expect(result).toContain('team-alpha/my-skill')
    expect(result).toContain('https://skill.example.com/space/team-alpha/my-skill')
  })

  it('truncates long description to fit 30 char limit', () => {
    const longDesc = 'This is a very long description that exceeds the character limit'
    const result = buildShareText('global', 'skill', longDesc, 'https://skill.example.com', mockT)

    // Total length should respect the 30 char limit for the first line
    const firstLine = result.split('\n')[0]
    expect(firstLine.length).toBeLessThanOrEqual(30)
    expect(firstLine).toContain('…')
  })

  it('uses default description when description is undefined', () => {
    const result = buildShareText('global', 'my-skill', undefined, 'https://skill.example.com', mockT)

    expect(result).toContain('A useful skill')
  })

  it('includes skill URL on second line', () => {
    const result = buildShareText('global', 'my-skill', 'Test', 'https://skill.example.com', mockT)
    const lines = result.split('\n')

    expect(lines).toHaveLength(2)
    expect(lines[1]).toBe('https://skill.example.com/space/global/my-skill')
  })
})
