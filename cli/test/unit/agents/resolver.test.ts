import { describe, expect, test } from 'bun:test'
import { resolveInstallTargets } from '../../../src/agents/resolver'

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
})
