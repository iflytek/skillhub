import { describe, expect, test } from 'bun:test'
import { classifySkillVersionChange, compareSkillVersions } from '../../../src/services/skill-version-order'

describe('classifySkillVersionChange', () => {
  test.each([
    ['1.2.3', '1.2.4', 'patch'],
    ['1.2.3', '1.3.0', 'minor'],
    ['1.2.3', '1.3.5', 'minor'],
    ['1.2.3', '2.0.0', 'major'],
    ['0.1.0', '0.2.0', 'minor'],
    ['1.2.3', '1.2.4-beta.1', 'prerelease'],
    ['1.2.3', '2.0.0-rc.1', 'prerelease'],
    ['1.0.0-beta.1', '1.0.0-beta.2', 'prerelease'],
    ['1.0.0-rc.1', '1.0.0', 'prerelease'],
    ['1.0.0-rc.1', '1.0.1', 'patch'],
    ['1.0.0-rc.1', '2.0.0', 'major']
  ] as const)('%s -> %s is %s', (installed, remote, expected) => {
    expect(classifySkillVersionChange(installed, remote)).toBe(expected)
  })

  test('equal versions report no change', () => {
    expect(classifySkillVersionChange('1.2.3', '1.2.3')).toBe('none')
    expect(classifySkillVersionChange('1.2.3+build.1', '1.2.3+build.2')).toBe('none')
    expect(classifySkillVersionChange('latest', 'latest')).toBe('none')
  })

  test('an older remote version is a downgrade, never an update class', () => {
    expect(classifySkillVersionChange('1.2.3', '1.2.2')).toBe('downgrade')
    expect(classifySkillVersionChange('2.0.0', '1.9.9')).toBe('downgrade')
    expect(classifySkillVersionChange('1.0.0', '1.0.0-rc.1')).toBe('downgrade')
  })

  test('non-semver versions are unknown', () => {
    expect(classifySkillVersionChange('1.0', '1.1')).toBe('unknown')
    expect(classifySkillVersionChange('1.0.0', 'nightly')).toBe('unknown')
    expect(classifySkillVersionChange('v1', '1.0.0')).toBe('unknown')
  })

  test('agrees with the version order used by upgrade', () => {
    const pairs: Array<[string, string]> = [['1.0.0', '1.0.1'], ['1.0.1', '1.0.0'], ['1.0.0', '1.0.0'], ['x', 'y']]
    for (const [installed, remote] of pairs) {
      const order = compareSkillVersions(installed, remote)
      const change = classifySkillVersionChange(installed, remote)
      if (order === 'same') expect(change).toBe('none')
      else if (order === 'remote-older') expect(change).toBe('downgrade')
      else if (order === 'unknown') expect(change).toBe('unknown')
      else expect(['major', 'minor', 'patch', 'prerelease']).toContain(change)
    }
  })
})
