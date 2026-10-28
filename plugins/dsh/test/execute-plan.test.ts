import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { realpath, mkdtemp, mkdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '@astron-team/skillhub-dsh';
import { TogglePlans, UpgradePlans, RemovalPlans } from '../src/host/upgrade-plans.js';
import type { PreparedNativeUpgrade } from '../src/host/local-runtime.js';

test('executePlan accepts only a bound plan and guards every mutation against current Host policy', async () => {
  const cwd = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-execute-plan-')));
  try {
    await mkdir(join(cwd, '.git'));
    const root = join(cwd, '.dsh/skills');
    const scope = { sessionId: 'session', cwd, sourceRevision: 'source' };
    const identity = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1.0.0' };
    const plans = new UpgradePlans();
    const plan = plans.issue(scope, { baseline: { status: 'ready', inventoryItem: { ...identity, targets: [] },
      targets: [{ root, path: join(root, 'writer'), scope: 'project', metadata: identity, files: {} }] },
      candidate: { namespace: 'demo', slug: 'writer', version: '2.0.0', versionId: 2, fingerprint: 'new', downloadUrl: '/download' } });
    const input = { planId: plan.planId, requestKey: randomUUID() };
    const filesystem = { resolve: async (path: string) => path, processPath: (path: string) => path, processPathFromHostPath: (path: string) => path };
    let mode = 'danger-full-access';
    const agent = { session: { id: 'session', header: { cwd } }, ctx: { get: (name: string) => name === 'fs' ? filesystem : { resolve: () => ({ mode }) } } };
    let prepared: PreparedNativeUpgrade | undefined;
    let replay = false;
    const runtime = { getStatus: () => ({ hostInstanceId: randomUUID() }),
      findPlanOperation: async () => undefined,
      submitUpgrade: async (_input: unknown, prepare: () => Promise<PreparedNativeUpgrade>) => {
        if (!replay) prepared = await prepare();
        return null;
      } };
    const context = { togglePlans: new TogglePlans(), localRuntime: runtime, lifetime: new AbortController(), upgradePlans: plans, removalPlans: new RemovalPlans(),
      ctx: { invocation: { request: { args: { agentId: 'session', input } } }, get: () => ({ get: () => agent }) },
      selectionScope: () => scope, assertSource: () => {}, getContext: () => ({ registryUrl: identity.registry }) };
    Object.setPrototypeOf(context, SkillHubService.prototype);
    const execute = (request: object) => {
      context.ctx.invocation.request.args.input = request as typeof input;
      return Reflect.apply(SkillHubService.prototype.executePlan, context, [request, new AbortController().signal]);
    };
    await expect(execute({ ...input, path: '/forged' })).rejects.toThrow('Invalid mutation request');
    expect(prepared).toBeUndefined();
    expect(await execute(input)).toBeNull();
    if (!prepared) throw new Error('Expected prepared execution');
    expect(prepared.acceptance.targetPaths).toEqual([join(root, 'writer')]);
    expect(prepared.execution.version).toBe('2.0.0');
    await prepared.execution.authorize([join(root, 'writer')], 'replace');
    await expect(prepared.execution.authorize([join(cwd, 'unrelated')], 'replace')).rejects.toThrow('outside the confirmed roots');
    mode = 'read-only';
    await expect(prepared.execution.authorize([join(root, 'writer')], 'replace')).rejects.toThrow();
    mode = 'danger-full-access';
    await expect(execute({ ...input, requestKey: randomUUID() })).rejects.toThrow('another request');
    plans.clear(); replay = true;
    expect(await execute(input)).toBeNull();
  } finally { await rm(cwd, { recursive: true, force: true }); }
});
