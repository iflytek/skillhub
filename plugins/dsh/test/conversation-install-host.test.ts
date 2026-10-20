import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';
import type { ConversationSelections } from '../src/host/conversation-selections.js';
import type { NativeOperationRequest } from '../src/host/generated/native-contract.js';

test('conversation retries reach durable acceptance before expired reference resolution', async () => {
  const ctx = new Context();
  const agent = { session: { id: 'owner', header: { cwd: '/project' } } };
  const remove = ctx.provide('agents', { get: (id: string) => id === 'owner' ? agent : undefined } as never);
  const fiber = ctx.plugin(SkillHubService);
  await fiber;
  const requests: NativeOperationRequest[] = [];
  const hostInstanceId = randomUUID();
  Reflect.set(ctx.skillhub, 'localRuntime', {
    getStatus: () => ({ state: 'ready', hostInstanceId }),
    // Existing durable requests return without invoking preparation, even with no configured source.
    submitInstall: async (request: NativeOperationRequest) => { requests.push(request); return undefined; },
  });
  try {
    const context = { sessionId: 'owner', cwd: '/project', callId: 'install-call' };
    const signal = new AbortController().signal;
    await ctx.skillhub.installConversation({ selectionRef: 'expired', scope: 'user' }, context, signal);
    await ctx.skillhub.installConversation({ selectionRef: 'expired', scope: 'user' }, context, signal);
    await ctx.skillhub.installConversation({ selectionRef: 'changed', scope: 'project' }, context, signal);
    expect(requests).toHaveLength(3);
    expect(requests[0]).toEqual(requests[1]);
    expect(requests[2]!.requestKey).toBe(requests[0]!.requestKey);
    expect(requests[0]!.parameters).toEqual({ selectionRef: 'expired', scope: 'user', cwd: '/project', initiatedFrom: 'conversation' });
    expect(requests[2]!.parameters).not.toEqual(requests[0]!.parameters);
    await expect(ctx.skillhub.installConversation({ selectionRef: 'expired', scope: 'user' }, { ...context, cwd: '/other' }, signal)).rejects.toThrow();
    await expect(ctx.skillhub.installConversation({ selectionRef: 'expired', path: '/other' }, context, signal)).rejects.toThrow();
    expect(requests).toHaveLength(3);
  } finally { await fiber.dispose(); await remove(); }
});

test('new conversation install preserves fixed detail and never falls back from unavailable project scope', async () => {
  const ctx = new Context();
  const agent = { session: { id: 'owner', header: { cwd: '/project' } } };
  const remove = ctx.provide('agents', { get: () => agent } as never);
  const fiber = ctx.plugin(SkillHubService, { registryUrl: 'https://registry.example' });
  await fiber;
  const signal = new AbortController().signal;
  const preparations: unknown[][] = [];
  Reflect.set(ctx.skillhub, 'localRuntime', {
    getStatus: () => ({ state: 'ready', hostInstanceId: randomUUID() }),
    submitInstall: async (_request: unknown, prepare: () => Promise<unknown>) => { await prepare(); return undefined; },
  });
  const selections: ConversationSelections = Reflect.get(ctx.skillhub, 'selections');
  const scope = { sessionId: 'owner', cwd: '/project', sourceRevision: ctx.skillhub.getContext().sourceRevision };
  const snapshot = { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint: 'original' };
  const viewed = selections.add({ ...scope, callId: 'search' }, snapshot);
  const fixed = selections.stage(viewed, scope, 'fixed-detail', snapshot);
  selections.acknowledge(fixed, scope, 'fixed-detail');
  Reflect.get(ctx.skillhub, 'details').set('fixed-detail', { ...snapshot, sourceRevision: scope.sourceRevision });
  Reflect.set(ctx.skillhub, 'installationTargetsFor', async () => ({ targets: [{ scope: 'user', targetToken: 'user-target' }] }));
  Reflect.set(ctx.skillhub, 'prepareInstallation', async (...args: unknown[]) => { preparations.push(args); return {}; });
  try {
    const context = { sessionId: 'owner', cwd: '/project', callId: 'install-call' };
    await ctx.skillhub.installConversation({ selectionRef: viewed, scope: 'user' }, context, signal);
    expect(preparations).toEqual([['owner', 'fixed-detail', 'user-target', 'conversation', signal]]);
    await expect(ctx.skillhub.installConversation({ selectionRef: viewed, scope: 'project' }, context, signal)).rejects.toThrow();
    await expect(ctx.skillhub.installConversation({ selectionRef: 'expired', scope: 'user' }, context, signal)).rejects.toThrow();
    expect(preparations).toHaveLength(1);
  } finally { await fiber.dispose(); await remove(); }
});

test('unviewed search installs its exact version even when latest changes, then reuses verified snapshot', async () => {
  const versions: string[] = [];
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    const url = new URL(request.url);
    if (url.pathname.endsWith('/resolve')) {
      const version = url.searchParams.get('version') ?? '2.0.0';
      versions.push(version);
      return Response.json({ data: { namespace: 'team', slug: 'writer', version, versionId: 1, fingerprint: 'abc', downloadUrl: '/download' } });
    }
    if (url.pathname === '/api/v1/skills/team/writer') return Response.json({ data: { id: 1, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '' } });
    if (url.pathname.endsWith('/versions') || url.pathname.endsWith('/reviews')) return Response.json({ data: { items: [], total: 0, page: 0, size: 1 } });
    return new Response('# Writer');
  } });
  const ctx = new Context();
  const agent = { session: { id: 'owner', header: { cwd: '/project' } } };
  const remove = ctx.provide('agents', { get: () => agent } as never);
  const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
  await fiber;
  const selections: ConversationSelections = Reflect.get(ctx.skillhub, 'selections');
  const scope = { sessionId: 'owner', cwd: '/project', sourceRevision: ctx.skillhub.getContext().sourceRevision };
  const ref = selections.add({ ...scope, callId: 'search' }, { namespace: 'team', slug: 'writer', version: '1.0.0' });
  const prepared: unknown[] = [];
  Reflect.set(ctx.skillhub, 'localRuntime', {
    getStatus: () => ({ state: 'ready', hostInstanceId: randomUUID() }),
    submitInstall: async (_request: unknown, prepare: () => Promise<unknown>) => { await prepare(); return undefined; },
  });
  Reflect.set(ctx.skillhub, 'installationTargetsFor', async () => ({ targets: [{ scope: 'project', targetToken: 'target' }] }));
  Reflect.set(ctx.skillhub, 'prepareInstallation', async (_agent: string, detailToken: string) => {
    prepared.push(Reflect.get(ctx.skillhub, 'details').get(detailToken));
    return {};
  });
  try {
    const context = { sessionId: 'owner', cwd: '/project', callId: 'install' };
    const signal = new AbortController().signal;
    await ctx.skillhub.installConversation({ selectionRef: ref, scope: 'project' }, context, signal);
    await ctx.skillhub.installConversation({ selectionRef: ref, scope: 'project' }, { ...context, callId: 'another' }, signal);
    expect(versions).toEqual(['1.0.0']);
    expect(prepared).toHaveLength(2);
    expect(prepared[0]).toMatchObject({ version: '1.0.0', versionId: 1, fingerprint: 'abc' });
    expect(prepared[1]).toEqual(prepared[0]);
    expect(() => selections.resolve(ref, scope)).toThrow();
    Reflect.get(ctx.skillhub, 'details').clear();
    await expect(ctx.skillhub.installConversation({ selectionRef: ref, scope: 'project' }, context, signal)).rejects.toThrow();
    expect(versions).toEqual(['1.0.0']);
    expect(prepared).toHaveLength(2);
  } finally { await fiber.dispose(); await remove(); await server.stop(true); }
});

test.each(['cwd', 'agent', 'source', 'abort'])('direct installation rejects %s drift during version verification', async change => {
  const ctx = new Context();
  const agent = { session: { id: 'owner', header: { cwd: '/project' } } };
  let current = agent;
  const remove = ctx.provide('agents', { get: () => current } as never);
  const fiber = ctx.plugin(SkillHubService, { registryUrl: 'https://registry.example' });
  await fiber;
  const selections: ConversationSelections = Reflect.get(ctx.skillhub, 'selections');
  const scope = { sessionId: 'owner', cwd: '/project', sourceRevision: ctx.skillhub.getContext().sourceRevision };
  const snapshot = { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint: 'abc' };
  const ref = selections.add({ ...scope, callId: 'search' }, snapshot);
  const abort = new AbortController();
  let targets = 0;
  Reflect.set(ctx.skillhub, 'localRuntime', {
    getStatus: () => ({ state: 'ready', hostInstanceId: randomUUID() }),
    submitInstall: async (_request: unknown, prepare: () => Promise<unknown>) => { await prepare(); return undefined; },
  });
  Reflect.set(ctx.skillhub, 'getSkillDetail', async () => {
    if (change === 'cwd') agent.session.header.cwd = '/other';
    if (change === 'agent') current = { session: { ...agent.session } };
    if (change === 'source') Reflect.set(ctx.skillhub, 'sourceRevision', randomUUID());
    if (change === 'abort') abort.abort();
    Reflect.get(ctx.skillhub, 'details').set('detail', { ...snapshot, sourceRevision: scope.sourceRevision });
    return { detailToken: 'detail' };
  });
  Reflect.set(ctx.skillhub, 'installationTargetsFor', async () => { targets++; throw new Error('unexpected target resolution'); });
  try {
    await expect(ctx.skillhub.installConversation({ selectionRef: ref, scope: 'user' }, { sessionId: 'owner', cwd: '/project', callId: 'install' }, abort.signal)).rejects.toThrow();
    expect(targets).toBe(0);
    expect(() => selections.resolve(ref, scope)).toThrow();
  } finally { await fiber.dispose(); await remove(); }
});

async function pendingFixture() {
  const ctx = new Context();
  const agent = { session: { id: 'owner', header: { cwd: '/project' } } };
  let current = agent;
  const remove = ctx.provide('agents', { get: () => current } as never);
  const removeInvocation = ctx.provide('invocation', { request: { args: { agentId: 'owner' } } } as never);
  const fiber = ctx.plugin(SkillHubService, { registryUrl: 'https://registry.example' });
  await fiber;
  const hostInstanceId = randomUUID();
  let mutations = 0;
  const runtime = { getStatus: () => ({ state: 'ready', hostInstanceId }),
    submitInstall: async () => { mutations++; throw new Error('No transaction is allowed before UI confirmation'); } };
  Reflect.set(ctx.skillhub, 'localRuntime', runtime);
  Reflect.set(ctx.skillhub, 'prepareInstallation', async () => { mutations++; throw new Error('No preparation before UI confirmation'); });
  Reflect.set(ctx.skillhub, 'installationTargetsFor', async () => { mutations++; throw new Error('No target before UI confirmation'); });
  Reflect.set(ctx.skillhub, 'getSkillDetail', async () => { mutations++; throw new Error('No detail or fingerprint lock before UI confirmation'); });
  const selections: ConversationSelections = Reflect.get(ctx.skillhub, 'selections');
  const scope = { sessionId: 'owner', cwd: '/project', sourceRevision: ctx.skillhub.getContext().sourceRevision };
  const ref = selections.add({ ...scope, callId: 'search' }, { namespace: 'team', slug: 'writer', version: '1.0.0' });
  return { ctx, agent, scope, ref, selections, runtime, mutations: () => mutations,
    replaceAgent: () => { current = { session: { ...agent.session } }; },
    close: async () => { await fiber.dispose(); await removeInvocation(); await remove(); } };
}
const pendingContext = { sessionId: 'owner', cwd: '/project', callId: 'install-choice' };

test('search selection without scope returns an exact pending choice, preserves idempotency, and never prepares or submits', async () => {
  const f = await pendingFixture();
  const signal = new AbortController().signal;
  try {
    const pending = await f.ctx.skillhub.installConversation({ selectionRef: f.ref }, pendingContext, signal);
    expect(pending).toMatchObject({ status: 'awaiting-confirmation', namespace: 'team', slug: 'writer', version: '1.0.0',
      sourceRevision: f.scope.sourceRevision });
    expect(pending && 'selectionRef' in pending ? pending.selectionRef : '').not.toBe(f.ref);
    if (pending && 'selectionRef' in pending) {
      await expect(f.ctx.skillhub.installConversation({ selectionRef: pending.selectionRef, scope: 'user' },
        { ...pendingContext, callId: 'bypass' }, signal)).rejects.toThrow('confirm in the installation card');
    }
    expect(pending).not.toHaveProperty('operationId');
    expect(pending).not.toHaveProperty('fingerprint');
    expect(pending).not.toHaveProperty('cwd');
    expect(pending).not.toHaveProperty('detailToken');
    expect(await f.ctx.skillhub.installConversation({ selectionRef: f.ref }, pendingContext, signal)).toEqual(pending);
    await expect(f.ctx.skillhub.installConversation({ selectionRef: f.ref, scope: 'user' }, pendingContext, signal)).rejects.toThrow('parameters changed');
    await expect(f.ctx.skillhub.installConversation({ namespace: 'other', slug: 'skill' }, pendingContext, signal)).rejects.toThrow('parameters changed');
    expect(() => f.selections.resolve(f.ref, f.scope)).toThrow();
    expect(f.mutations()).toBe(0);
  } finally { await f.close(); }
});

test.each(['source', 'cwd', 'agent', 'runtime', 'clear', 'abort'])('pending choice cannot reopen installation authority after %s changes', async change => {
  const f = await pendingFixture();
  const abort = new AbortController();
  try {
    const pending = await f.ctx.skillhub.installConversation({ selectionRef: f.ref }, pendingContext, abort.signal);
    if (change === 'source') Reflect.set(f.ctx.skillhub, 'sourceRevision', randomUUID());
    if (change === 'cwd') f.agent.session.header.cwd = '/changed';
    if (change === 'agent') f.replaceAgent();
    if (change === 'runtime') Reflect.set(f.ctx.skillhub, 'localRuntime', undefined);
    if (change === 'clear') f.selections.clear();
    if (change === 'abort') abort.abort();
    if (change !== 'abort' && pending && 'selectionRef' in pending) {
      await expect(f.ctx.skillhub.openConversationDetail(pending.selectionRef, abort.signal)).rejects.toThrow();
    }
    // No retry with an old candidate is allowed to submit an installation.
    await expect(f.ctx.skillhub.installConversation({ selectionRef: f.ref }, pendingContext, abort.signal)).rejects.toThrow();
    expect(f.mutations()).toBe(0);
  } finally { await f.close(); }
});

test('new explicit coordinates without scope resolve a candidate version once, not a prepared install snapshot', async () => {
  const f = await pendingFixture();
  let resolves = 0;
  Reflect.set(f.ctx.skillhub, 'readSource', async (_revision: string, _signal: AbortSignal,
    read: (adapter: { resolve: () => Promise<{ version: string }> }, signal: AbortSignal) => Promise<unknown>) =>
    read({ resolve: async () => { resolves++; return { version: '2.1.0' }; } }, _signal));
  try {
    const request = { namespace: 'team', slug: 'writer' };
    const first = await f.ctx.skillhub.installConversation(request, pendingContext, new AbortController().signal);
    expect(first).toMatchObject({ status: 'awaiting-confirmation', namespace: 'team', slug: 'writer', version: '2.1.0' });
    expect(first && 'selectionRef' in first ? f.selections.read(first.selectionRef, f.scope).version : null).toBe('2.1.0');
    expect(await f.ctx.skillhub.installConversation(request, pendingContext, new AbortController().signal)).toEqual(first);
    expect(resolves).toBe(1);
    expect(f.mutations()).toBe(0);
  } finally { await f.close(); }
});

test.each(['source', 'cwd', 'agent', 'runtime', 'abort'])('pending coordinate resolution fails closed on asynchronous %s drift', async change => {
  const f = await pendingFixture();
  const abort = new AbortController();
  Reflect.set(f.ctx.skillhub, 'readSource', async () => {
    if (change === 'source') Reflect.set(f.ctx.skillhub, 'sourceRevision', randomUUID());
    if (change === 'cwd') f.agent.session.header.cwd = '/changed';
    if (change === 'agent') f.replaceAgent();
    if (change === 'runtime') Reflect.set(f.ctx.skillhub, 'localRuntime', undefined);
    if (change === 'abort') abort.abort();
    return { version: '2.1.0' };
  });
  try {
    await expect(f.ctx.skillhub.installConversation({ namespace: 'team', slug: 'writer' }, pendingContext, abort.signal)).rejects.toThrow();
    expect(f.mutations()).toBe(0);
  } finally { await f.close(); }
});
