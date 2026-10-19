import type { InstalledSkillList, SearchInstallationStatus } from './types.js';

/** Strip local identifiers and paths from a current-source inventory projection. */
export function summarizeInstallation(
  identity: { namespace: string; slug: string }, inventory: InstalledSkillList | undefined,
  checkedAt = new Date().toISOString(),
): SearchInstallationStatus {
  return {
    checkedAt,
    complete: !!inventory?.complete && inventory.unavailable.length === 0,
    unavailable: inventory ? [...inventory.unavailable] : ['user', 'project'],
    records: inventory?.items.filter(item => item.namespace === identity.namespace && item.slug === identity.slug)
      .map(({ scope, version, state }) => ({ scope, version, state })) ?? [],
  };
}
