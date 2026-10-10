import { describe, expect, it } from 'vitest'
import { validateBasePath } from './base-path-config'

describe('validateBasePath', () => {
  it.each(['/skillhub/', '/foo.bar/', '/a_b~c/'])('accepts %s', (value) => {
    expect(validateBasePath(value)).toBe(value)
  })

  it('accepts root deployment', () => {
    expect(validateBasePath('/')).toBe('/')
  })

  it.each([
    '//cdn.example/',
    '/foo//bar/',
    '/foo/../bar/',
    '/foo/./bar/',
    '/foo bar/',
    'skillhub/',
    '/skillhub',
  ])('rejects unsafe or malformed value %s', (value) => {
    expect(() => validateBasePath(value)).toThrow(/VITE_BASE_PATH/)
  })

  it.each([
    '/api/',
    '/oauth2/',
    '/login/',
    '/assets/',
    '/registry/',
    '/nginx-health/',
    '/.well-known/',
    '/runtime-config.js/',
    '/api/nested/',
  ])('rejects reserved first segment %s', (value) => {
    expect(() => validateBasePath(value)).toThrow(/reserved by the SkillHub server/)
  })
})
