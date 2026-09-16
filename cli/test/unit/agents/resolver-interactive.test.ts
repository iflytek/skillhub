import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, mock, test } from 'bun:test'
import type { AgentCandidate } from '../../../src/agents/types'

interface PromptChoice {
  title: string
  value: AgentCandidate
}

interface PromptOptions {
  choices?: PromptChoice[]
  onRender?: (this: { cursor?: number }) => void
  format?: (selectedTargets: AgentCandidate[]) => AgentCandidate[]
}

const defaultSelectedTargets = (options: PromptOptions): AgentCandidate[] => options.format?.([]) ?? []
let selectPromptTargets = defaultSelectedTargets
let renderedChoices: PromptChoice[] = []

mock.module('prompts', () => ({
  default: (options: PromptOptions) => {
    renderedChoices = options.choices ?? []
    options.onRender?.call({ cursor: 1 })
    return { selected: selectPromptTargets(options) }
  }
}))

afterEach(() => {
  selectPromptTargets = defaultSelectedTargets
  renderedChoices = []
})

const { resolveInstallTargets } = await import('../../../src/agents/resolver')

describe('resolveInstallTargets interactive prompt', () => {
  test('renders AStudio by display name when its directory was detected', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-astudio-resolver-'))
    const nativeRootDir = join(home, '.acode', 'skills')
    const profileRootDir = nativeRootDir.replace(/\\/g, '/')

    try {
      await mkdir(nativeRootDir, { recursive: true })
      await resolveInstallTargets({
        cwd: '/repo',
        home,
        agents: [],
        scope: 'user',
        json: false,
        interactive: true
      })

      expect(renderedChoices[0]?.title).toBe(`AStudio (${profileRootDir})`)
    } finally {
      await rm(home, { recursive: true, force: true })
    }
  })

  test('does not render AStudio when .acode skills is a regular file', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-astudio-file-'))
    const rootDir = join(home, '.acode', 'skills')

    try {
      await mkdir(join(home, '.acode'), { recursive: true })
      await writeFile(rootDir, 'not a directory')
      const targets = await resolveInstallTargets({
        cwd: '/repo',
        home,
        agents: [],
        scope: 'user',
        json: false,
        interactive: true
      })

      expect(renderedChoices.some(choice => choice.value.agent === 'astudio')).toBe(false)
      expect(targets).toEqual([{
        agent: 'generic',
        rootDir: `${home}/.agents/skills`,
        scope: 'user',
        source: 'fallback'
      }])
    } finally {
      await rm(home, { recursive: true, force: true })
    }
  })

  test('uses the highlighted target when Enter submits an empty multiselect', async () => {
    const detected: AgentCandidate[] = [
      { agent: 'codex', rootDir: '/repo/.codex/skills', scope: 'project', source: 'detected' },
      { agent: 'claude-code', rootDir: '/repo/.claude/skills', scope: 'project', source: 'detected' }
    ]
    const highlighted = detected[1]!

    const targets = await resolveInstallTargets({
      cwd: '/repo',
      agents: [],
      json: false,
      interactive: true,
      detected
    })

    expect(targets).toEqual([highlighted])
  })

  test('allows selecting generic alongside detected user targets', async () => {
    selectPromptTargets = options => options.choices?.map(choice => choice.value) ?? []
    const codex: AgentCandidate = {
      agent: 'codex',
      rootDir: '/home/u/.codex/skills',
      scope: 'user',
      source: 'detected'
    }
    const generic: AgentCandidate = {
      agent: 'generic',
      rootDir: '/home/u/.agents/skills',
      scope: 'user',
      source: 'fallback'
    }

    const targets = await resolveInstallTargets({
      cwd: '/repo',
      home: '/home/u',
      agents: [],
      scope: 'user',
      json: false,
      interactive: true,
      detected: [codex]
    })

    expect(targets).toEqual([codex, generic])
  })
})
