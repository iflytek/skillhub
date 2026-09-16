import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { allProfiles, profileMap } from '../../../src/agents/detector'

describe('agent profiles', () => {
  test('has 15 tier 1 profiles', () => {
    expect(allProfiles).toHaveLength(15)
  })

  test('all profiles have unique ids', () => {
    const ids = allProfiles.map(p => p.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  test('profileMap contains all profiles', () => {
    expect(profileMap.size).toBe(15)
    expect(profileMap.has('astudio')).toBe(true)
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

  test('AStudio exposes only its fixed user-level directory on Linux, macOS, and Windows', () => {
    const profile = profileMap.get('astudio')!

    expect(profile.displayName).toBe('AStudio')
    expect(profile.projectRoots('/repo')).toEqual([])
    expect(profile.userRoots('/home/alice')).toEqual(['/home/alice/.acode/skills'])
    expect(profile.userRoots('/Users/alice')).toEqual(['/Users/alice/.acode/skills'])
    expect(profile.userRoots('C:\\Users\\alice')).toEqual(['C:/Users/alice/.acode/skills'])
  })

  test('AStudio is detected only when the user .acode skills directory exists', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-astudio-home-'))
    const profile = profileMap.get('astudio')!
    const nativeRootDir = join(home, '.acode', 'skills')
    const profileRootDir = nativeRootDir.replace(/\\/g, '/')

    try {
      expect(await profile.detectInstalled('/repo', home)).toEqual([])

      await mkdir(join(home, '.acode'), { recursive: true })
      await writeFile(nativeRootDir, 'not a directory')
      expect(await profile.detectInstalled('/repo', home)).toEqual([])

      await rm(nativeRootDir)
      await mkdir(nativeRootDir, { recursive: true })

      expect(await profile.detectInstalled('/repo', home)).toEqual([{
        agent: 'astudio',
        rootDir: profileRootDir,
        scope: 'user',
        source: 'detected'
      }])
    } finally {
      await rm(home, { recursive: true, force: true })
    }
  })
})
