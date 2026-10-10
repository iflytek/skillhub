import { describe, expect, test } from 'bun:test'
import { parseSkillName, resolveSkillName } from '../../../src/shared/skill-name-parser'
import { EXIT } from '../../../src/shared/constants'
import { CliError } from '../../../src/shared/errors'

function expectUsageError(callback: () => unknown): void {
  let error: unknown
  try {
    callback()
  } catch (caught) {
    error = caught
  }
  expect(error).toBeInstanceOf(CliError)
  expect((error as CliError).exitCode).toBe(EXIT.usage)
}

describe('parseSkillName', () => {
  test.each([
    ['my-skill', { namespace: 'global', slug: 'my-skill' }],
    ['team/my-skill', { namespace: 'team', slug: 'my-skill' }],
    ['@team/my-skill', { namespace: 'team', slug: 'my-skill' }],
    ['team--my-skill', { namespace: 'team', slug: 'my-skill' }]
  ])('parses %s', (skillName, expected) => {
    expect(parseSkillName(skillName)).toEqual(expected)
  })

  test('preserves double dashes after the coordinate separator', () => {
    expect(parseSkillName('namespace--slug--with--dashes')).toEqual({
      namespace: 'namespace',
      slug: 'slug--with--dashes'
    })
  })

  test('preserves the custom default namespace for a bare slug', () => {
    expect(parseSkillName('api-gateway', 'myorg')).toEqual({
      namespace: 'myorg',
      slug: 'api-gateway'
    })
  })

  test.each([
    '',
    '@team',
    'team/',
    '/my-skill',
    '--my-skill',
    'team--',
    'team/my-skill/extra',
    '@team/my-skill/extra',
    'team--my-skill/extra'
  ])('rejects malformed coordinate %p', (skillName) => {
    expectUsageError(() => parseSkillName(skillName))
  })
})

describe('resolveSkillName', () => {
  test('uses global for a bare slug without an explicit namespace', () => {
    expect(resolveSkillName('my-skill')).toEqual({
      namespace: 'global',
      slug: 'my-skill'
    })
  })

  test('uses an explicit namespace for a bare slug', () => {
    expect(resolveSkillName('my-skill', 'team')).toEqual({
      namespace: 'team',
      slug: 'my-skill'
    })
  })

  test.each([
    'team/my-skill',
    '@team/my-skill',
    'team--my-skill'
  ])('accepts matching --namespace for %s', (skillName) => {
    expect(resolveSkillName(skillName, 'team')).toEqual({
      namespace: 'team',
      slug: 'my-skill'
    })
  })

  test('rejects a coordinate that conflicts with --namespace', () => {
    expectUsageError(() => resolveSkillName('@team/my-skill', 'other'))
  })
})
