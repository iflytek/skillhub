import { describe, expect, test } from 'bun:test'
import { CliError } from '../../../src/shared/errors'
import { EXIT } from '../../../src/shared/constants'
import {
  computeStrictIsTTY,
  installCommand,
  resolveEffectiveScope,
  type InstallCommandDeps,
  type InstallCommandOptions
} from '../../../src/commands/install'
import type { AgentCandidate } from '../../../src/agents/types'
import type { ResolveInstallTargetOptions } from '../../../src/agents/resolver'

describe('computeStrictIsTTY', () => {
  test('true when stdin and stdout are TTY and not json', () => {
    expect(computeStrictIsTTY({ stdinIsTTY: true, stdoutIsTTY: true, json: false })).toBe(true)
  })

  test('false when stdin is not TTY', () => {
    expect(computeStrictIsTTY({ stdinIsTTY: false, stdoutIsTTY: true, json: false })).toBe(false)
  })

  test('false when stdout is not TTY', () => {
    expect(computeStrictIsTTY({ stdinIsTTY: true, stdoutIsTTY: false, json: false })).toBe(false)
  })

  test('false when json is true', () => {
    expect(computeStrictIsTTY({ stdinIsTTY: true, stdoutIsTTY: true, json: true })).toBe(false)
  })
})

describe('resolveEffectiveScope', () => {
  function neverPrompt(): Promise<'user' | 'project'> {
    throw new Error('promptScope should not be called')
  }

  test('rejects invalid --scope value', async () => {
    await expect(resolveEffectiveScope(
      { scope: 'team' } as InstallCommandOptions,
      { isTTY: false, promptScope: neverPrompt }
    )).rejects.toThrow('--scope must be "user" or "project"')
  })

  test('rejects --dir with --scope', async () => {
    await expect(resolveEffectiveScope(
      { scope: 'user', dir: '/tmp/x' } as InstallCommandOptions,
      { isTTY: false, promptScope: neverPrompt }
    )).rejects.toThrow('--dir cannot be used with --scope')
  })

  test('rejects --dir with --agent', async () => {
    await expect(resolveEffectiveScope(
      { dir: '/tmp/x', agent: ['codex'] } as InstallCommandOptions,
      { isTTY: false, promptScope: neverPrompt }
    )).rejects.toThrow('--dir cannot be used with --agent')
  })

  test('returns explicit --scope value', async () => {
    const scope = await resolveEffectiveScope(
      { scope: 'user' } as InstallCommandOptions,
      { isTTY: true, promptScope: neverPrompt }
    )
    expect(scope).toBe('user')
  })

  test('--agent without --scope returns undefined (regression protection)', async () => {
    const scope = await resolveEffectiveScope(
      { agent: ['codex'] } as InstallCommandOptions,
      { isTTY: true, promptScope: neverPrompt }
    )
    expect(scope).toBeUndefined()
  })

  test('--dir without --scope returns undefined (regression protection)', async () => {
    const scope = await resolveEffectiveScope(
      { dir: '/tmp/x' } as InstallCommandOptions,
      { isTTY: true, promptScope: neverPrompt }
    )
    expect(scope).toBeUndefined()
  })

  test('non-interactive bare install returns undefined without calling promptScope', async () => {
    const scope = await resolveEffectiveScope(
      {} as InstallCommandOptions,
      { isTTY: false, promptScope: neverPrompt }
    )
    expect(scope).toBeUndefined()
  })

  test('interactive bare install calls promptScope and returns user', async () => {
    let calls = 0
    const scope = await resolveEffectiveScope(
      {} as InstallCommandOptions,
      {
        isTTY: true,
        promptScope: async () => { calls++; return 'user' }
      }
    )
    expect(scope).toBe('user')
    expect(calls).toBe(1)
  })

  test('interactive bare install + promptScope returns project', async () => {
    const scope = await resolveEffectiveScope(
      {} as InstallCommandOptions,
      { isTTY: true, promptScope: async () => 'project' }
    )
    expect(scope).toBe('project')
  })

  test('interactive bare install + promptScope cancel propagates CliError', async () => {
    await expect(resolveEffectiveScope(
      {} as InstallCommandOptions,
      {
        isTTY: true,
        promptScope: async () => { throw new CliError('installation cancelled', 5) }
      }
    )).rejects.toThrow('installation cancelled')
  })

  test('empty agent array does not skip promptScope', async () => {
    let calls = 0
    const scope = await resolveEffectiveScope(
      { agent: [] } as InstallCommandOptions,
      {
        isTTY: true,
        promptScope: async () => { calls++; return 'user' }
      }
    )
    expect(scope).toBe('user')
    expect(calls).toBe(1)
  })
})

describe('installCommand dependency injection', () => {
  function fakeInstallSkill(): NonNullable<InstallCommandDeps['installSkill']> {
    return async () => ({ installed: [{ agent: 'codex', dir: '/home/u/.codex/skills/foo' }] })
  }

  function fakeResolveInstallTargets(): NonNullable<InstallCommandDeps['resolveInstallTargets']> {
    return async () => [{
      agent: 'codex',
      rootDir: '/home/u/.codex/skills',
      scope: 'user',
      source: 'explicit'
    }] as AgentCandidate[]
  }

  test('renders post-commit warnings in human and JSON output', async () => {
    const deps: InstallCommandDeps = {
      isTTY: () => false,
      resolveInstallTargets: fakeResolveInstallTargets(),
      installSkill: async () => ({
        installed: [{ agent: 'codex', dir: '/home/u/.codex/skills/foo' }],
        warnings: ['target lock cleanup failed: simulated release failure']
      })
    }

    const human = await installCommand('@global/foo', { registry: 'http://localhost' }, deps)
    expect(human).toContain('Warning: target lock cleanup failed')
    const json = JSON.parse(await installCommand('@global/foo', {
      registry: 'http://localhost',
      json: true
    }, deps))
    expect(json.warnings).toEqual(['target lock cleanup failed: simulated release failure'])
  })

  test('passes a namespaced coordinate to installSkill', async () => {
    let received: Parameters<NonNullable<InstallCommandDeps['installSkill']>>[0] | undefined
    const deps: InstallCommandDeps = {
      isTTY: () => false,
      resolveInstallTargets: fakeResolveInstallTargets(),
      installSkill: async (options) => {
        received = options
        return { installed: [{ agent: 'codex', dir: '/home/u/.codex/skills/my-skill' }] }
      }
    }

    await installCommand('@team/my-skill', {
      registry: 'http://localhost',
      token: 'sk'
    }, deps)

    expect(received).toMatchObject({
      namespace: 'team',
      slug: 'my-skill'
    })
  })

  test('rejects a conflicting namespace before installing', async () => {
    let installCalls = 0
    let error: unknown
    const deps: InstallCommandDeps = {
      isTTY: () => false,
      resolveInstallTargets: fakeResolveInstallTargets(),
      installSkill: async () => {
        installCalls += 1
        return { installed: [] }
      }
    }

    try {
      await installCommand('@team/my-skill', {
        namespace: 'other',
        registry: 'http://localhost',
        token: 'sk'
      }, deps)
    } catch (caught) {
      error = caught
    }
    expect(error).toBeInstanceOf(CliError)
    expect((error as CliError).exitCode).toBe(EXIT.usage)
    expect(installCalls).toBe(0)
  })

  test('passes prompted scope and strict isTTY into resolveInstallTargets', async () => {
    const calls: { promptScope: number; resolverCalls: ResolveInstallTargetOptions[] } = {
      promptScope: 0,
      resolverCalls: []
    }
    const deps: InstallCommandDeps = {
      isTTY: () => true,
      promptScope: async () => { calls.promptScope++; return 'user' },
      resolveInstallTargets: async (opts) => {
        calls.resolverCalls.push(opts)
        return [{ agent: 'codex', rootDir: '/home/u/.codex/skills', scope: 'user', source: 'explicit' }] as AgentCandidate[]
      },
      installSkill: fakeInstallSkill()
    }

    await installCommand('foo', { registry: 'http://localhost', token: 'sk' }, deps)

    expect(calls.promptScope).toBe(1)
    expect(calls.resolverCalls).toHaveLength(1)
    expect(calls.resolverCalls[0]!.scope).toBe('user')
    expect(calls.resolverCalls[0]!.interactive).toBe(true)
  })

  test('does not call promptScope when --agent is provided', async () => {
    let promptCalls = 0
    let resolverScope: 'user' | 'project' | undefined = 'user'
    const deps: InstallCommandDeps = {
      isTTY: () => true,
      promptScope: async () => { promptCalls++; return 'user' },
      resolveInstallTargets: async (opts) => {
        resolverScope = opts.scope
        return [{ agent: 'codex', rootDir: '/home/u/.codex/skills', scope: 'user', source: 'explicit' }] as AgentCandidate[]
      },
      installSkill: fakeInstallSkill()
    }

    await installCommand('foo', {
      registry: 'http://localhost',
      token: 'sk',
      agent: ['codex']
    }, deps)

    expect(promptCalls).toBe(0)
    expect(resolverScope).toBeUndefined()
  })

  test('passes interactive=false when isTTY returns false', async () => {
    let interactiveFlag: boolean | undefined
    const deps: InstallCommandDeps = {
      isTTY: () => false,
      promptScope: async () => { throw new Error('should not be called') },
      resolveInstallTargets: async (opts) => {
        interactiveFlag = opts.interactive
        return [{ agent: 'generic', rootDir: '/tmp/.agents/skills', scope: 'project', source: 'fallback' }] as AgentCandidate[]
      },
      installSkill: fakeInstallSkill()
    }

    await installCommand('foo', { registry: 'http://localhost', token: 'sk' }, deps)
    expect(interactiveFlag).toBe(false)
  })
})
