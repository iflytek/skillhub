import { expect, test } from 'bun:test';
import { realpath, mkdtemp, mkdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '../src/host/index.js';
import { TogglePlans } from '../src/host/upgrade-plans.js';
import type { ToggleBaseline } from '../src/host/generated/native-contract.js';

test('toggle preparation is local, preserves exact target, and blocks recovery or read-only policies', async () => {
  const cwd = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-prepare-toggle-')));
  try {
    await mkdir(join(cwd, '.git'));
    const root = join(cwd, '.dsh/skills');
    const scope = { sessionId: 'session', sourceRevision: 'source', cwd };
    const identity = { registry: 'https://offline.invalid', namespace: 'demo', slug: 'writer', version: '1', fingerprint: 'fp' };
    let baseline: ToggleBaseline = { status: 'ready', beforeState: 'enabled', afterState: 'disabled', inventoryItem: { ...identity, targets: [] },
      target: { scope: 'project', root, path: join(root, 'writer'), metadata: identity, files: { 'SKILL.md': 'hash' } } };
    let mode = 'danger-full-access'; let pending = 0;
    const plans = new TogglePlans();
    const context = { lifetime: new AbortController(), togglePlans: plans, selectionScope: () => scope,
      getContext: () => ({ registryUrl: identity.registry }),
      readSource: () => { throw new Error('Toggle must not query Registry'); },
      readInstalledItem: async () => ({ item: { ...identity, state: 'present', installationState: 'enabled', scope: 'project', targetPath: join(root, 'writer') }, valid() {},
        agent: { session: { id: 'session', header: { cwd } }, ctx: { get: () => ({ resolve: () => ({ mode }) }) } },
        fs: { sandboxMode: 'workspace-write', resolve: async (path: string) => path,
          processPath: (path: string) => path, processPathFromHostPath: (path: string) => path },
        runtime: { getStatus: () => ({ pendingOperations: pending, hostInstanceId: 'host' }),
          inspectToggle: async () => baseline } }),
    };
    Object.setPrototypeOf(context, SkillHubService.prototype);
    const prepare = () => Reflect.apply(SkillHubService.prototype.prepareToggle, context, ['source', 'item', 'disabled', new AbortController().signal]);
    const ready = await prepare();
    expect(ready).toMatchObject({ status: 'ready', beforeState: 'enabled', afterState: 'disabled', target: { scope: 'project', path: join(root, 'writer') } });
    expect(plans.read(ready.planId!, scope)).toEqual(baseline);
    baseline = { status: 'unchanged', installationState: 'disabled' };
    expect(await prepare()).toMatchObject({ status: 'unchanged', planId: null });
    baseline = { status: 'blocked', reason: 'entry-conflict' };
    expect(await prepare()).toMatchObject({ status: 'blocked', reason: 'entry-conflict', planId: null });
    pending = 1;
    expect(await prepare()).toMatchObject({ status: 'blocked', reason: 'recovery-required' });
    pending = 0; mode = 'read-only';
    await expect(prepare()).rejects.toThrow('read-only');
  } finally { await rm(cwd, { recursive: true, force: true }); }
});

test('disabled installation refuses use before discovery or reading examples', async () => {
  const context = { lifetime: new AbortController(), ctx: { get: () => { throw new Error('No discovery needed'); } },
    readInstalledItem: async () => ({ item: { installationState: 'disabled' }, valid() {}, agent: { session: { header: { cwd: '/tmp' } } } }) };
  // Discovery service may be captured, but must never be invoked for a disabled target.
  context.ctx.get = () => undefined as never;
  expect(await Reflect.apply(SkillHubService.prototype.prepareSkillUse, context, ['source', 'item', new AbortController().signal]))
    .toEqual({ status: 'blocked', reason: 'installation-disabled' });
});
