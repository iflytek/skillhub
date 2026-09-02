import { compare as compareSemver, valid as validSemver } from 'semver'

export type SkillVersionOrder = 'same' | 'remote-newer' | 'remote-older' | 'unknown'

export function compareSkillVersions(installedVersion: string, remoteVersion: string): SkillVersionOrder {
  if (installedVersion === remoteVersion) return 'same'
  if (!validSemver(installedVersion) || !validSemver(remoteVersion)) return 'unknown'
  const order = compareSemver(remoteVersion, installedVersion)
  if (order === 0) return 'same'
  return order > 0 ? 'remote-newer' : 'remote-older'
}
