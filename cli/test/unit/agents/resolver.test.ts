import { mkdir, mkdtemp, rm, symlink } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, test } from 'bun:test'
import { resolveInstallTargets } from '../../../src/agents/resolver'
import type { AgentCandidate } from '../../../src/agents/types'

describe('resolveInstallTargets', () => {
  test('rejects dir and agent together before filesystem writes', async () => {
    await expect(resolveInstallTargets({
      cwd: '/repo',
      dir: '/tmp/skills',
      agents: ['codex'],
      json: false,
      interactive: false
    })).rejects.toThrow('--dir cannot be used with --agent')
  })

  test('falls back to cwd .agents skills when nothing detected', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      agents: [],
      json: false,
      interactive: false,
      detected: []
    })
    expect(targets).toEqual([{ agent: 'generic', rootDir: '/repo/.agents/skills', scope: 'project', source: 'fallback' }])
  })

  test('uses explicit dir when provided', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      dir: '/tmp/my-skills',
      json: false,
      interactive: false
    })
    expect(targets).toEqual([{ agent: 'custom', rootDir: '/tmp/my-skills', scope: 'user', source: 'explicit' }])
  })

  test('explicit agent resolves the profile user root by default', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: ['codex'],
      json: false,
      interactive: false
    })
    expect(targets).toEqual([{ agent: 'codex', rootDir: '/home/u/.codex/skills', scope: 'user', source: 'explicit' }])
  })

  test('explicit agent without scope labels root by userRoots membership when cwd === home', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/home/u',
      home: '/home/u',
      agents: ['codex'],
      json: false,
      interactive: false
    })
    expect(targets[0]!.scope).toBe('user')
    expect(targets[0]!.rootDir).toBe('/home/u/.codex/skills')
  })

  test('deduplicates repeated explicit agents by target root', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: ['codex', 'codex'],
      json: false,
      interactive: false
    })
    expect(targets).toHaveLength(1)
    expect(targets[0]!.rootDir).toBe('/home/u/.codex/skills')
  })

  test('returns single detected target directly', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      agents: [],
      json: false,
      interactive: false,
      detected: [{ agent: 'codex', rootDir: '/repo/.codex/skills', scope: 'project', source: 'detected' }]
    })
    expect(targets).toHaveLength(1)
    expect(targets[0]!.agent).toBe('codex')
  })

  test('rejects multiple detected targets in non-interactive mode', async () => {
    await expect(resolveInstallTargets({
      cwd: '/repo',
      agents: [],
      json: false,
      interactive: false,
      detected: [
        { agent: 'codex', rootDir: '/repo/.codex/skills', scope: 'project', source: 'detected' },
        { agent: 'claude-code', rootDir: '/repo/.claude/skills', scope: 'project', source: 'detected' }
      ]
    })).rejects.toThrow('multiple install targets detected')
  })

  test('rejects unknown agent', async () => {
    await expect(resolveInstallTargets({
      cwd: '/repo',
      agents: ['unknown-agent'],
      json: false,
      interactive: false
    })).rejects.toThrow('unknown agent: unknown-agent')
  })

  test('rejects dir and scope together', async () => {
    await expect(resolveInstallTargets({
      cwd: '/repo',
      dir: '/tmp/skills',
      scope: 'user',
      json: false,
      interactive: false
    })).rejects.toThrow('--dir cannot be used with --scope')
  })

  test('scope=project + agent codex returns project root with project scope', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: ['codex'],
      scope: 'project',
      json: false,
      interactive: false
    })
    expect(targets).toEqual([{ agent: 'codex', rootDir: '/repo/.codex/skills', scope: 'project', source: 'explicit' }])
  })

  test('rejects project scope for the user-only AStudio profile', async () => {
    await expect(resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: ['astudio'],
      scope: 'project',
      json: false,
      interactive: false
    })).rejects.toThrow('agent astudio does not support project scope')
  })

  test('scope=user + agent codex returns user root with user scope', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: ['codex'],
      scope: 'user',
      json: false,
      interactive: false
    })
    expect(targets).toEqual([{ agent: 'codex', rootDir: '/home/u/.codex/skills', scope: 'user', source: 'explicit' }])
  })

  test('scope=user + cwd === home + agent codex still labels candidate as user', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/home/u',
      home: '/home/u',
      agents: ['codex'],
      scope: 'user',
      json: false,
      interactive: false
    })
    expect(targets[0]!.scope).toBe('user')
  })

  test('scope=user clean env falls back to user agents skills', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/nonexistent-home-' + Math.random().toString(36).slice(2),
      agents: [],
      scope: 'user',
      json: false,
      interactive: false
    })
    expect(targets).toEqual([{
      agent: 'generic',
      rootDir: targets[0]!.rootDir,
      scope: 'user',
      source: 'fallback'
    }])
    expect(targets[0]!.rootDir).toMatch(/\.agents\/skills$/)
    expect(targets[0]!.rootDir.startsWith('/nonexistent-home-')).toBe(true)
  })

  test('scope=project clean env falls back to cwd agents skills', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/nonexistent-repo-' + Math.random().toString(36).slice(2),
      home: '/home/u',
      agents: [],
      scope: 'project',
      json: false,
      interactive: false
    })
    expect(targets).toHaveLength(1)
    expect(targets[0]!.scope).toBe('project')
    expect(targets[0]!.source).toBe('fallback')
    expect(targets[0]!.rootDir).toMatch(/\.agents\/skills$/)
  })

  test('scope filters detected candidates and falls back when filtered empty', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: [],
      scope: 'user',
      json: false,
      interactive: false,
      detected: [
        { agent: 'codex', rootDir: '/repo/.codex/skills', scope: 'project', source: 'detected' }
      ]
    })
    expect(targets).toHaveLength(1)
    expect(targets[0]!.source).toBe('fallback')
    expect(targets[0]!.scope).toBe('user')
  })

  test('scope filters detected candidates keeps matching scope', async () => {
    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: [],
      scope: 'user',
      json: false,
      interactive: false,
      detected: [
        { agent: 'codex', rootDir: '/repo/.codex/skills', scope: 'project', source: 'detected' },
        { agent: 'codex', rootDir: '/home/u/.codex/skills', scope: 'user', source: 'detected' }
      ]
    })
    expect(targets).toHaveLength(1)
    expect(targets[0]!.rootDir).toBe('/home/u/.codex/skills')
    expect(targets[0]!.scope).toBe('user')
  })

  test('deduplicates a symlinked detected target and the generic user target', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-resolver-home-'))
    const genericRoot = join(home, '.agents', 'skills')
    const codexRoot = join(home, '.codex', 'skills')
    const codex: AgentCandidate = {
      agent: 'codex',
      rootDir: codexRoot,
      scope: 'user',
      source: 'detected'
    }

    try {
      await mkdir(genericRoot, { recursive: true })
      await mkdir(join(home, '.codex'), { recursive: true })
      await symlink(genericRoot, codexRoot, process.platform === 'win32' ? 'junction' : 'dir')

      const targets = await resolveInstallTargets({
        cwd: '/repo',
        home,
        agents: [],
        scope: 'user',
        json: false,
        interactive: true,
        detected: [codex]
      })

      expect(targets).toEqual([codex])
    } finally {
      await rm(home, { recursive: true, force: true })
    }
  })
})
