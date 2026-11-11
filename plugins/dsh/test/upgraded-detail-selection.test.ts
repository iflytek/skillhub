import { expect, test } from 'bun:test';
import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { upgradedInstallation, refreshedInstalledSelection, refreshUpgradedCaches } from '../src/client/installed-skills.js';
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


test('upgrade return refreshes the mounted management inventory and only invalidates this skill references', async () => {
  const client = new QueryClient();
  const key = ['skillhub-installed', 'session', 'revision', 'management-locations', previous.registry, previous.namespace, previous.slug];
  const unrelated = ['skillhub-installed', 'session', 'revision', 'management-locations', 'https://other.example', previous.namespace, previous.slug];
  const oldDetail = ['skillhub-installed-detail', 'session', 'revision', previous.itemKey];
  const summary = ['skillhub-local-summary', 'session', 'revision', previous.itemKey];
  const otherSummary = ['skillhub-local-summary', 'session', 'revision', 'other-key'];
  client.setQueryData(key, snapshot([previous])); client.setQueryData(unrelated, snapshot([previous]));
  for (const cache of [oldDetail, summary, otherSummary]) client.setQueryData(cache, { old: true });
  const observer = new QueryObserver<InstalledSkillList>(client, { queryKey: key, enabled: false });
  let observed: InstalledSkillList | undefined;
  const unsubscribe = observer.subscribe(result => { observed = result.data; });
  try {
    expect(await refreshUpgradedCaches(client, 'session', 'revision', previous, snapshot([updated]), () => true)).toBe(true);
    expect(observed?.items[0]).toEqual(updated); expect(observed?.items[0]?.itemKey).toBe('new-key');
    expect(client.getQueryState(oldDetail)?.isInvalidated).toBe(true); expect(client.getQueryState(summary)?.isInvalidated).toBe(true);
    expect(client.getQueryState(otherSummary)?.isInvalidated).toBe(false);
    expect(client.getQueryData<InstalledSkillList>(unrelated)?.items[0]?.version).toBe('1.2.0');
  } finally { unsubscribe(); client.clear(); }
});

test('a late old management read cannot overwrite upgraded records and leaving aborts cache application', async () => {
  const client = new QueryClient();
  const key = ['skillhub-installed', 'session', 'revision', 'management-locations', previous.registry, previous.namespace, previous.slug];
  const gate = Promise.withResolvers<InstalledSkillList>();
  const stale = client.fetchQuery({ queryKey: key, queryFn: () => gate.promise }).catch(() => undefined);
  await refreshUpgradedCaches(client, 'session', 'revision', previous, snapshot([updated]), () => true);
  gate.resolve(snapshot([previous])); await stale;
  expect(client.getQueryData<InstalledSkillList>(key)?.items[0]).toEqual(updated);
  expect(await refreshUpgradedCaches(client, 'session', 'revision', previous, snapshot([previous]), () => false)).toBe(false);
  expect(client.getQueryData<InstalledSkillList>(key)?.items[0]).toEqual(updated);
  client.clear();
});


test('static detail follows the fresh enabled or disabled reference at the same version and location', () => {
  const enabled = { ...previous, installationState: 'enabled' as const, locationKey: 'same-location' };
  const disabled = { ...enabled, installationState: 'disabled' as const, itemKey: 'disabled-key' };
  expect(refreshedInstalledSelection(snapshot([disabled]), enabled)).toBe(disabled);
  const reenabled = { ...enabled, itemKey: 'reenabled-key' };
  expect(refreshedInstalledSelection(snapshot([reenabled]), disabled)).toBe(reenabled);
});

test('static detail cannot follow another version, location, source, scope or an unverified list', () => {
  const selected = { ...previous, locationKey: 'selected-location' };
  for (const candidate of [updated, { ...previous, registry: 'https://other.example' }, { ...previous, scope: 'user' as const }, { ...previous, locationKey: 'another-location' }, { ...previous, state: 'missing' as const }]) {
    expect(refreshedInstalledSelection(snapshot([candidate]), selected)).toBeUndefined();
  }
  expect(refreshedInstalledSelection({ ...snapshot([selected]), complete: false }, selected)).toBeUndefined();
  expect(refreshedInstalledSelection(snapshot([selected, { ...selected, itemKey: 'ambiguous' }]), selected)).toBeUndefined();
});
