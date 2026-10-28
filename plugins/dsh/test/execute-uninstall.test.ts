import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { realpath, mkdir, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import SkillHubService from '@astron-team/skillhub-dsh';
import { TogglePlans, RemovalPlans } from '../src/host/upgrade-plans.js';
import type { PreparedNativeRemoval } from '../src/host/local-runtime.js';
import type { NativeOperation, RemovalBaseline } from '../src/host/generated/native-contract.js';

test.each(['uninstall', 'remove-stale-record'] as const)('executePlan routes %s and restricts every mutation to its accepted identity', async kind => {
  const cwd = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-execute-uninstall-')));
  try {
    await mkdir(join(cwd, '.git'));
    const root = join(cwd, '.dsh/skills');
    const scope = { sessionId: 'session', cwd, sourceRevision: 'source' };
    const identity = { registry: 'https://registry.example', namespace: 'demo', slug: 'writer', version: '1' };
    const target = { scope: 'project' as const, root, path: join(root, 'writer') };
    const inventoryItem = { ...identity, fingerprint: 'fp', targets: [{ agent: 'dsh', rootDir: root,
      installDir: target.path, installedAt: '2026-10-14', version: identity.version, fingerprint: 'fp' }] };
    const baseline: Extract<RemovalBaseline, { status: 'ready' }> = kind === 'uninstall'
      ? { status: 'ready', kind, inventoryItem, target: { ...target, metadata: identity, files: { 'SKILL.md': 'hash' } } }
      : { status: 'ready', kind, inventoryItem, target };
    const plans = new RemovalPlans();
    const plan = plans.issue(scope, baseline);
    const input = { planId: plan.planId, requestKey: randomUUID() };
    const hostInstanceId = randomUUID();
    const receipt: NativeOperation = { operationId: randomUUID(), kind, revision: 0, namespace: 'demo', slug: 'writer',
      version: '1', targetLabel: 'Project', phase: 'accepted', files: 'pending', discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0,
      context: { hostInstanceId, hostLabel: 'Host', sourceIdentity: identity.registry, sourceLabel: 'Registry', sourceRevision: 'source',
        initiatedFrom: 'plaza', createdAt: '2026-10-14T00:00:00Z', updatedAt: '2026-10-14T00:00:00Z' } };
    let mode = 'danger-full-access';
    const filesystem = { sandboxMode: 'workspace-write', resolve: async (path: string) => path,
      processPath: (path: string) => path, processPathFromHostPath: (path: string) => path };
    const agent = { session: { id: 'session', header: { cwd } }, ctx: { get: (name: string) => name === 'fs'
      ? filesystem : { resolve: () => ({ mode }) } } };
    let prepared: PreparedNativeRemoval | undefined;
    let replay = false;
    const runtime = { getStatus: () => ({ hostInstanceId }), findPlanOperation: async () => replay ? receipt : undefined,
      submitRemoval: async (_request: unknown, prepare: () => Promise<PreparedNativeRemoval>) => { prepared = await prepare(); return receipt; },
      submitUpgrade: async () => { throw new Error('Removal must not execute an upgrade'); } };
    const context = { togglePlans: new TogglePlans(), localRuntime: runtime, lifetime: new AbortController(), removalPlans: plans,
      selectionScope: () => scope, assertSource() {}, getContext: () => ({ registryUrl: identity.registry }),
      ctx: { invocation: { request: { args: { agentId: 'session', input } } }, get: () => ({ get: () => agent }) } };
    Object.setPrototypeOf(context, SkillHubService.prototype);
    const execute = (request: typeof input) => {
      context.ctx.invocation.request.args.input = request;
      return Reflect.apply(SkillHubService.prototype.executePlan, context, [request, new AbortController().signal]);
    };
    expect(await execute(input)).toEqual(receipt);
    if (!prepared) throw new Error('Expected prepared removal');
    expect(prepared.acceptance.kind).toBe(kind);
    expect(prepared.acceptance.targetPaths).toEqual([target.path]);
    const quarantine = join(dirname(root), '.skillhub-trash', receipt.operationId);
    await prepared.execution.authorize([target.path, quarantine], 'replace', receipt.operationId);
    await expect(prepared.execution.authorize([join(dirname(quarantine), randomUUID())], 'cleanup', receipt.operationId)).rejects.toThrow('escaped');
    await expect(prepared.execution.authorize([join(root, 'other')], 'replace', receipt.operationId)).rejects.toThrow('escaped');
    mode = 'read-only';
    await expect(prepared.execution.authorize([target.path], 'replace', receipt.operationId)).rejects.toThrow('read-only');
    mode = 'danger-full-access';
    await expect(execute({ ...input, requestKey: randomUUID() })).rejects.toThrow('another request');
    agent.session.header.cwd = join(cwd, 'changed');
    await expect(prepared.execution.authorize([target.path], 'replace', receipt.operationId)).rejects.toThrow('session changed');
    plans.clear(); replay = true;
    expect(await execute(input)).toEqual(receipt);
  } finally { await rm(cwd, { recursive: true, force: true }); }
});
