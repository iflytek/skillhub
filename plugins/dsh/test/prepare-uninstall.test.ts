import { expect, test } from 'bun:test';
import { mkdtemp, mkdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '@astron-team/skillhub-dsh';
import { RemovalPlans } from '../src/host/upgrade-plans.js';
import type { RemovalBaseline } from '../src/host/generated/native-contract.js';

test('uninstall preparation remains local and distinguishes removal from stale-record cleanup', async () => {
  const cwd = await mkdtemp(join(tmpdir(), 'skillhub-prepare-uninstall-'));
  try {
    await mkdir(join(cwd, '.git'));
    const root = join(cwd, '.dsh/skills');
    const scope = { sessionId: 'session', sourceRevision: 'source', cwd };
    const identity = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1', fingerprint: 'fp' };
    let baseline: RemovalBaseline = { status: 'ready', kind: 'uninstall', inventoryItem: { ...identity, targets: [] },
      target: { scope: 'project', root, path: join(root, 'writer'), metadata: identity, files: { 'SKILL.md': 'hash' } } };
    let mode = 'danger-full-access';
    let pending = 0;
    let state = 'present';
    let inspected = 0;
    const plans = new RemovalPlans();
    const context = { lifetime: new AbortController(), removalPlans: plans, selectionScope: () => scope,
      getContext: () => ({ registryUrl: identity.registry }),
      readSource: () => { throw new Error('Uninstall must not query Registry'); },
      readInstalledItem: async () => ({ item: { ...identity, state, scope: 'project' }, valid() {},
        agent: { session: { id: 'session', header: { cwd } }, ctx: { get: () => ({ resolve: () => ({ mode }) }) } },
        fs: { sandboxMode: 'workspace-write', resolve: async (path: string) => path,
          processPath: (path: string) => path, processPathFromHostPath: (path: string) => path },
        runtime: { getStatus: () => ({ pendingOperations: pending, hostInstanceId: 'host' }),
          inspectRemoval: async () => { inspected++; return baseline; } } }),
    };
    const prepare = () => Reflect.apply(SkillHubService.prototype.prepareUninstall, context, ['source', 'item', new AbortController().signal]);
    const ready = await prepare();
    if (!ready.planId) throw new Error('Expected removal plan');
    expect(ready).toMatchObject({ status: 'ready', kind: 'uninstall', currentVersion: '1', target: { scope: 'project', path: join(root, 'writer') } });
    expect(plans.read(ready.planId, scope)).toEqual(baseline);
    state = 'missing';
    baseline = { status: 'ready', kind: 'remove-stale-record', inventoryItem: { ...identity, targets: [] },
      target: { scope: 'project', root, path: join(root, 'writer') } };
    expect(await prepare()).toMatchObject({ status: 'ready', kind: 'remove-stale-record' });
    baseline = { status: 'blocked', reason: 'shared-target' };
    expect(await prepare()).toMatchObject({ status: 'blocked', reason: 'shared-target', planId: null });
    const before = inspected;
    pending = 1;
    expect(await prepare()).toMatchObject({ status: 'blocked', reason: 'recovery-required' });
    expect(inspected).toBe(before);
    pending = 0; mode = 'read-only';
    await expect(prepare()).rejects.toThrow('read-only');
    expect(inspected).toBe(before);
  } finally { await rm(cwd, { recursive: true, force: true }); }
});
