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
    const lines = result.split('\n')

    expect(lines).toHaveLength(3)
    expect(lines[0]).toBe('my-skill')
    expect(lines[1]).toBe('Test description')
    expect(lines[2]).toBe('https://skill.example.com/space/global/my-skill')
  })

  it('builds share text for namespaced skill', () => {
    const result = buildShareText('team-alpha', 'my-skill', 'Test description', 'https://skill.example.com', mockT)
    const lines = result.split('\n')

    expect(lines).toHaveLength(3)
    expect(lines[0]).toBe('team-alpha/my-skill')
    expect(lines[1]).toBe('Test description')
    expect(lines[2]).toBe('https://skill.example.com/space/team-alpha/my-skill')
  })

  it('includes full description without truncation', () => {
    const longDesc = 'This is a very long description that exceeds the character limit'
    const result = buildShareText('global', 'skill', longDesc, 'https://skill.example.com', mockT)
    const lines = result.split('\n')

    expect(lines[1]).toBe(longDesc)
    expect(lines[1]).not.toContain('…')
  })

  it('uses default description when description is undefined', () => {
    const result = buildShareText('global', 'my-skill', undefined, 'https://skill.example.com', mockT)
    const lines = result.split('\n')

    expect(lines[1]).toBe('A useful skill')
  })

  it('includes skill URL on third line', () => {
    const result = buildShareText('global', 'my-skill', 'Test', 'https://skill.example.com', mockT)
    const lines = result.split('\n')

    expect(lines).toHaveLength(3)
    expect(lines[2]).toBe('https://skill.example.com/space/global/my-skill')
  })
})
