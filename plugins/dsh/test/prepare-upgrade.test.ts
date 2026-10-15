import { expect, test } from 'bun:test';
import { realpath, mkdtemp, mkdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '@astron-team/skillhub-dsh';
import { UpgradePlans } from '../src/host/upgrade-plans.js';
import type { UpgradeBaseline } from '../src/host/generated/native-contract.js';

test('prepare upgrade stores a bounded plan only after baseline and version checks', async () => {
  const cwd = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-prepare-upgrade-')));
  try {
    await mkdir(join(cwd, '.git'));
    const root = join(cwd, '.dsh', 'skills');
    const scope = { sessionId: 'session', sourceRevision: 'source', cwd };
    const identity = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0', fingerprint: 'old' };
    let baseline: UpgradeBaseline = { status: 'ready', inventoryItem: { ...identity, targets: [] },
      targets: [{ scope: 'project', root, path: join(root, 'writer'), metadata: identity, files: { 'SKILL.md': 'hash' } }] };
    let resolves = 0;
    let pending = 0;
    const lifetime = new AbortController();
    const plans = new UpgradePlans();
    const context = {
      lifetime, upgradePlans: plans,
      selectionScope: () => scope, getContext: () => ({ registryUrl: identity.registry }),
      readInstalledItem: async () => ({ item: { ...identity, state: 'present' }, valid() {},
        agent: { session: { id: 'session', header: { cwd } }, ctx: { get: () => ({ resolve: () => ({ mode: 'danger-full-access' }) }) } },
        fs: { resolve: async (path: string) => path, processPath: (path: string) => path, processPathFromHostPath: (path: string) => path },
        runtime: { getStatus: () => ({ pendingOperations: pending, hostInstanceId: '3e36f379-2d53-4d80-b72a-4fe3642c5181' }), inspectUpgrade: async () => baseline,
          assessUpdate: async () => ({ state: 'upgradable', reason: 'newer-version' }) } }),
      readSource: async () => { resolves++; return { namespace: 'demo', slug: 'writer', version: '2.0.0', versionId: 2, fingerprint: 'new', downloadUrl: '/download' }; },
    };
    const prepare = () => Reflect.apply(SkillHubService.prototype.prepareUpgrade, context, ['source', 'item', new AbortController().signal]);
    const ready = await prepare();
    expect(ready.status).toBe('ready');
    if (!ready.planId) throw new Error('Expected ready plan reference');
    expect(ready.targets).toEqual([{ scope: 'project', path: join(root, 'writer') }]);
    expect(plans.read(ready.planId, scope).candidate.version).toBe('2.0.0');
    expect(resolves).toBe(1);
    baseline = { status: 'blocked', reason: 'shared-target' };
    expect(await prepare()).toMatchObject({ status: 'blocked', reason: 'shared-target', planId: null });
    expect(resolves).toBe(1);
    pending = 1;
    expect(await prepare()).toMatchObject({ status: 'blocked', reason: 'recovery-required', planId: null });
    expect(resolves).toBe(1);
  } finally { await rm(cwd, { recursive: true, force: true }); }
});
