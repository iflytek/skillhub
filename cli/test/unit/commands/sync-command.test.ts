import { describe, expect, test } from 'bun:test'
import {
  requireSyncNamespace,
  resolvePullSelection
} from '../../../src/commands/sync'
import type { SyncStatusEntry } from '../../../src/services/sync-service'
import { computeStrictIsTTY } from '../../../src/shared/tty'

function entry(slug: string, status: SyncStatusEntry['status']): SyncStatusEntry {
  return {
    namespace: 'team-a',
    slug,
    status,
    ...(status === 'orphaned' ? {} : { remoteVersion: '1.0.0' }),
    changedFiles: []
  }
}

describe('sync command selection', () => {
  test('requires an explicit non-global namespace', () => {
    expect(() => requireSyncNamespace(undefined)).toThrow('--namespace is required')
    expect(() => requireSyncNamespace('global')).toThrow('global does not support namespace sync')
    expect(requireSyncNamespace('team-a')).toBe('team-a')
  })

  test('uses the same strict TTY rule as interactive install flows', () => {
    expect(computeStrictIsTTY({ stdinIsTTY: true, stdoutIsTTY: true, json: false })).toBe(true)
    expect(computeStrictIsTTY({ stdinIsTTY: false, stdoutIsTTY: true, json: false })).toBe(false)
    expect(computeStrictIsTTY({ stdinIsTTY: true, stdoutIsTTY: false, json: false })).toBe(false)
    expect(computeStrictIsTTY({ stdinIsTTY: true, stdoutIsTTY: true, json: true })).toBe(false)
  })

  test('accepts and deduplicates explicit skill selections without prompting', async () => {
    let prompted = false
    const selected = await resolvePullSelection(
      [entry('first', 'not-installed'), entry('second', 'update-available')],
      ['second', 'first', 'second'],
      {
        interactive: false,
        prune: false,
        prompt: async () => {
          prompted = true
          return []
        }
      }
    )

    expect(selected).toEqual(['second', 'first'])
    expect(prompted).toBe(false)
  })

  test('rejects non-interactive pull without an explicit selection', async () => {
    await expect(resolvePullSelection([entry('demo', 'not-installed')], undefined, {
      interactive: false,
      prune: false,
      prompt: async () => ['demo']
    })).rejects.toThrow('requires at least one --skill')
  })

  test('interactive TTY returns exactly the prompt multi-selection', async () => {
    const selected = await resolvePullSelection(
      [entry('first', 'not-installed'), entry('second', 'update-available')],
      undefined,
      {
        interactive: true,
        prune: false,
        prompt: async candidates => {
          expect(candidates.map(candidate => candidate.slug)).toEqual(['first', 'second'])
          return ['second']
        }
      }
    )

    expect(selected).toEqual(['second'])
  })

  test('interactive cancel or empty selection is a no-op', async () => {
    const selected = await resolvePullSelection([entry('demo', 'not-installed')], undefined, {
      interactive: true,
      prune: false,
      prompt: async () => []
    })

    expect(selected).toEqual([])
  })

  test('prune only makes managed orphan slugs selectable when explicitly enabled', async () => {
    await expect(resolvePullSelection([entry('old', 'orphaned')], ['old'], {
      interactive: false,
      prune: false,
      prompt: async () => []
    })).rejects.toThrow('skill not found in namespace')

    expect(await resolvePullSelection([entry('old', 'orphaned')], ['old'], {
      interactive: false,
      prune: true,
      prompt: async () => []
    })).toEqual(['old'])
  })
})
