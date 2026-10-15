import { expect, test } from 'bun:test'
import { assessSkillUpdate } from '../../../src/integrations/update-assessment'

test('update assessment preserves CLI version ordering and never treats changed same-version content as an upgrade', () => {
  const installed = { version: '1.2.0', fingerprint: 'original' }
  for (const [candidate, state, reason] of [
    [{ version: '1.3.0', fingerprint: 'new' }, 'upgradable', 'newer-version'],
    [{ version: '1.2.0', fingerprint: 'original' }, 'current', 'same-content'],
    [{ version: '1.2.0+build', fingerprint: 'new' }, 'blocked', 'same-version-content-changed'],
    [{ version: '1.2.0', fingerprint: 'new' }, 'blocked', 'same-version-content-changed'],
    [{ version: '1.1.0', fingerprint: 'old' }, 'blocked', 'remote-older'],
    [{ version: 'release-next', fingerprint: 'new' }, 'blocked', 'unknown-order'],
    [{ version: '1.3.0' }, 'blocked', 'missing-fingerprint'],
  ] as const) {
    expect(assessSkillUpdate(installed, candidate)).toEqual({ currentVersion: '1.2.0', candidateVersion: candidate.version, state, reason })
  }
  expect(assessSkillUpdate({ version: '1.2.0' }, { version: '1.3.0', fingerprint: 'new' }).reason).toBe('missing-fingerprint')
  expect(assessSkillUpdate({ version: 'release', fingerprint: 'same' }, { version: 'release', fingerprint: 'same' }).state).toBe('current')
  expect(assessSkillUpdate({ version: '2.0.0-rc.1', fingerprint: 'old' }, { version: '2.0.0', fingerprint: 'new' }).state).toBe('upgradable')
})
