import { describe, it, expect } from 'bun:test'
import { parseSkillName } from '../../../src/shared/skill-name-parser'

describe('parseSkillName', () => {
  describe('with namespace--slug format', () => {
    it('should parse namespace and slug separated by double dash', () => {
      const result = parseSkillName('astroclaw--api-gateway')
      expect(result).toEqual({
        namespace: 'astroclaw',
        slug: 'api-gateway'
      })
    })

    it('should handle namespace and slug with single dashes', () => {
      const result = parseSkillName('my-org--my-skill-name')
      expect(result).toEqual({
        namespace: 'my-org',
        slug: 'my-skill-name'
      })
    })

    it('should handle multiple double dashes by using first as separator', () => {
      const result = parseSkillName('namespace--slug--with--dashes')
      expect(result).toEqual({
        namespace: 'namespace',
        slug: 'slug--with--dashes'
      })
    })
  })

  describe('with slug only format', () => {
    it('should use default namespace when no separator present', () => {
      const result = parseSkillName('api-gateway')
      expect(result).toEqual({
        namespace: 'global',
        slug: 'api-gateway'
      })
    })

    it('should use custom default namespace when provided', () => {
      const result = parseSkillName('api-gateway', 'myorg')
      expect(result).toEqual({
        namespace: 'myorg',
        slug: 'api-gateway'
      })
    })

    it('should handle slug with single dashes', () => {
      const result = parseSkillName('my-skill-name')
      expect(result).toEqual({
        namespace: 'global',
        slug: 'my-skill-name'
      })
    })
  })

  describe('edge cases', () => {
    it('should handle separator at start', () => {
      const result = parseSkillName('--api-gateway')
      expect(result).toEqual({
        namespace: 'global',
        slug: 'api-gateway'
      })
    })

    it('should handle separator at end', () => {
      const result = parseSkillName('astroclaw--')
      expect(result).toEqual({
        namespace: 'global',
        slug: 'astroclaw'
      })
    })

    it('should handle empty string', () => {
      const result = parseSkillName('')
      expect(result).toEqual({
        namespace: 'global',
        slug: ''
      })
    })

    it('should handle just separator', () => {
      const result = parseSkillName('--')
      expect(result).toEqual({
        namespace: 'global',
        slug: ''
      })
    })
  })
})
