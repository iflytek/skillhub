import { compareSkillVersions } from '../services/skill-version-order'

import type { UpdateIdentity, UpdateAssessment } from './native-contract'
export type { UpdateIdentity, UpdateAssessment } from './native-contract'

/** Read-only version/content comparison. An upgradable result is not a mutation plan or integrity check. */
export function assessSkillUpdate(installed: UpdateIdentity, candidate: UpdateIdentity): UpdateAssessment {
  const base = { currentVersion: installed.version, candidateVersion: candidate.version }
  const order = compareSkillVersions(installed.version, candidate.version)
  if (order === 'remote-older' || order === 'unknown') {
    return { ...base, state: 'blocked', reason: order === 'unknown' ? 'unknown-order' : 'remote-older' }
  }
  if (!installed.fingerprint || !candidate.fingerprint) return { ...base, state: 'blocked', reason: 'missing-fingerprint' }
  if (order === 'remote-newer') return { ...base, state: 'upgradable', reason: 'newer-version' }
  return installed.fingerprint === candidate.fingerprint
    ? { ...base, state: 'current', reason: 'same-content' }
    : { ...base, state: 'blocked', reason: 'same-version-content-changed' }
}
