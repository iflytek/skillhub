import { compare as compareSemver, parse as parseSemver, valid as validSemver } from 'semver'

export type SkillVersionOrder = 'same' | 'remote-newer' | 'remote-older' | 'unknown'

/** Size of the change from an installed version to a registry version. */
export type SkillVersionChange = 'major' | 'minor' | 'patch' | 'prerelease' | 'none' | 'downgrade' | 'unknown'

export function compareSkillVersions(installedVersion: string, remoteVersion: string): SkillVersionOrder {
  if (installedVersion === remoteVersion) return 'same'
  if (!validSemver(installedVersion) || !validSemver(remoteVersion)) return 'unknown'
  const order = compareSemver(remoteVersion, installedVersion)
  if (order === 0) return 'same'
  return order > 0 ? 'remote-newer' : 'remote-older'
}

/**
 * Classifies a version change using the same order as compareSkillVersions.
 * A newer prerelease, or a change limited to prerelease tags, is 'prerelease';
 * otherwise the first differing major/minor/patch component decides.
 */
export function classifySkillVersionChange(installedVersion: string, remoteVersion: string): SkillVersionChange {
  if (installedVersion === remoteVersion) return 'none'
  // Registry-generated GitHub versions contain content hashes, not release chronology.
  // Their ordering needs verified source evidence, as used by repository upgrades.
  const githubVersion = /^0\.0\.0-g[0-9a-f]{40}(?:-[0-9a-f]{16})?$/
  if (githubVersion.test(installedVersion) || githubVersion.test(remoteVersion)) return 'unknown'
  const order = compareSkillVersions(installedVersion, remoteVersion)
  if (order === 'same') return 'none'
  if (order === 'remote-older') return 'downgrade'
  if (order === 'unknown') return 'unknown'

  const installed = parseSemver(installedVersion)
  const remote = parseSemver(remoteVersion)
  if (!installed || !remote) return 'unknown'
  if (remote.prerelease.length > 0) return 'prerelease'
  if (remote.major !== installed.major) return 'major'
  if (remote.minor !== installed.minor) return 'minor'
  if (remote.patch !== installed.patch) return 'patch'
  return 'prerelease'
}
