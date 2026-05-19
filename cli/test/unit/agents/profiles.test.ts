import { describe, expect, test } from 'bun:test'
import { allProfiles, profileMap } from '../../../src/agents/detector'

describe('agent profiles', () => {
  test('has 14 tier 1 profiles', () => {
    expect(allProfiles).toHaveLength(14)
  })

  test('all profiles have unique ids', () => {
    const ids = allProfiles.map(p => p.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  test('profileMap contains all profiles', () => {
    expect(profileMap.size).toBe(14)
    expect(profileMap.has('claude-code')).toBe(true)
    expect(profileMap.has('codex')).toBe(true)
    expect(profileMap.has('cursor')).toBe(true)
    expect(profileMap.has('kilo')).toBe(true)
  })

  test('claude-code profile returns correct roots', () => {
    const profile = profileMap.get('claude-code')!
    expect(profile.projectRoots('/repo')).toEqual(['/repo/.claude/skills'])
    expect(profile.userRoots('/home/user')).toEqual(['/home/user/.claude/skills'])
  })

  test('codex profile returns correct roots', () => {
    const profile = profileMap.get('codex')!
    expect(profile.projectRoots('/repo')).toEqual(['/repo/.codex/skills'])
    expect(profile.userRoots('/home/user')).toEqual(['/home/user/.codex/skills'])
  })

  test('cursor profile returns correct roots', () => {
    const profile = profileMap.get('cursor')!
    expect(profile.projectRoots('/repo')).toEqual(['/repo/.cursor/skills'])
  })
})
