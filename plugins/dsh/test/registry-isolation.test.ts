import { expect, test } from 'bun:test';
import SkillHubService from '../src/host/index.js';
import { installedUpdateKeys } from '../src/client/installed-skills.js';
import { groupManagementItems } from '../src/client/management-tool-view.js';

const item = { itemKey: 'old', registry: 'https://old.example', currentSource: false, namespace: 'demo', slug: 'writer', version: '1.0.0', scope: 'user' as const, state: 'present' as const };

test('old source checks and upgrades are refused before remote resolution or filesystem inspection', async () => {
  let requests = 0;
  const service = Object.assign(Object.create(SkillHubService.prototype), {
    lifetime: new AbortController(),
    getContext: () => ({ registryUrl: 'https://new.example' }),
    selectionScope: () => ({}),
    readInstalledItem: async () => ({ item, valid() {}, agent: {}, runtime: { getStatus: () => ({ hostInstanceId: 'host', pendingOperations: 0 }) } }),
    readSource: () => { requests++; throw new Error('must not request'); },
  });
  expect(await service.checkUpdateForVersion('revision', 'old', undefined, new AbortController().signal)).toMatchObject({ state: 'blocked', reason: 'other-source' });
  expect(await service.prepareUpgradeForVersion('revision', 'old', undefined, new AbortController().signal)).toMatchObject({ status: 'blocked', reason: 'other-source' });
  expect(requests).toBe(0);
});

test('same coordinates from different registries remain separate management groups', () => {
  const groups = groupManagementItems([item, { ...item, itemKey: 'new', registry: 'https://new.example' }, { ...item, itemKey: 'project', scope: 'project' }]);
  expect(groups.map(group => group.length)).toEqual([2, 1]);
});

test('other-source details never fetch same-name market content, including disconnected', async () => {
  for (const registryUrl of ['https://new.example', '']) {
    let reads = 0;
    const service = Object.assign(Object.create(SkillHubService.prototype), {
      lifetime: new AbortController(), getContext: () => ({ registryUrl }),
      readInstalledItem: async () => ({ item, valid() {} }),
      getSkillDetail: async () => { reads++; throw new Error('wrong market'); },
    });
    const result = await service.getInstalledDetail('revision', 'old', new AbortController().signal);
    expect(result.content.state).toBe('local-only');
    expect(result.item.registry).toBe(item.registry);
    expect(reads).toBe(0);
  }
});


test('bulk checks exclude other-source records rather than reporting them as failed', () => {
  const current = { ...item, itemKey: 'current', currentSource: true };
  expect(installedUpdateKeys([item, current], {}, false)).toEqual(['current']);
  expect(installedUpdateKeys([item, current], { old: { state: 'failed' } }, true)).toEqual([]);
});
