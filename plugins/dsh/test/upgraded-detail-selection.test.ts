import { expect, test } from 'bun:test';
import { upgradedInstallation } from '../src/client/installed-skills.js';
import type { InstalledSkillList } from '../src/host/types.js';
const previous: InstalledSkillList['items'][number] = { itemKey: 'old-key', registry: 'https://source.example', namespace: 'team', slug: 'writer', scope: 'project', version: '1.2.0', state: 'present', installationState: 'disabled' };
const updated = { ...previous, itemKey: 'new-key', version: '1.3.0' };
const snapshot = (items: InstalledSkillList['items']): InstalledSkillList => ({ items, complete: true, unavailable: [], issues: [] });
test('upgrade return resolves the actual new record and preserves the selected install scope', () => {
  const other = { ...updated, itemKey: 'user-key', scope: 'user' as const };
  expect(upgradedInstallation(snapshot([other, updated]), previous, '1.3.0')).toBe(updated);
  expect(upgradedInstallation(snapshot([updated]), previous, '1.3.0')?.installationState).toBe('disabled');
});
test('upgrade return never borrows a same-name record from another source, scope or version', () => {
  for (const item of [{ ...updated, registry: 'https://other.example' }, { ...updated, scope: 'user' as const }, previous, { ...updated, state: 'missing' as const }]) {
    expect(upgradedInstallation(snapshot([item]), previous, '1.3.0')).toBeUndefined();
  }
});
test('upgrade return does not fabricate details for incomplete or ambiguous inventories', () => {
  expect(upgradedInstallation({ ...snapshot([updated]), complete: false }, previous, '1.3.0')).toBeUndefined();
  expect(upgradedInstallation(snapshot([updated, { ...updated, itemKey: 'duplicate' }]), previous, '1.3.0')).toBeUndefined();
  expect(upgradedInstallation(snapshot([]), previous, '1.3.0')).toBeUndefined();
});
