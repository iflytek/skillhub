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
    await ctx.skillhub.installConversation({ selectionRef: 'expired' }, context, signal);
    await ctx.skillhub.installConversation({ selectionRef: 'expired' }, context, signal);
    await ctx.skillhub.installConversation({ selectionRef: 'changed', scope: 'project' }, context, signal);
    expect(requests).toHaveLength(3);
    expect(requests[0]).toEqual(requests[1]);
    expect(requests[2]!.requestKey).toBe(requests[0]!.requestKey);
    expect(requests[0]!.parameters).toEqual({ selectionRef: 'expired', scope: 'user', cwd: '/project', initiatedFrom: 'conversation' });
    expect(requests[2]!.parameters).not.toEqual(requests[0]!.parameters);
    await expect(ctx.skillhub.installConversation({ selectionRef: 'expired' }, { ...context, cwd: '/other' }, signal)).rejects.toThrow();
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
    await ctx.skillhub.installConversation({ selectionRef: viewed }, context, signal);
    expect(preparations).toEqual([['owner', 'fixed-detail', 'user-target', 'conversation', signal]]);
    await expect(ctx.skillhub.installConversation({ selectionRef: viewed, scope: 'project' }, context, signal)).rejects.toThrow();
    await expect(ctx.skillhub.installConversation({ selectionRef: 'expired' }, context, signal)).rejects.toThrow();
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
    await expect(ctx.skillhub.installConversation({ selectionRef: ref }, { sessionId: 'owner', cwd: '/project', callId: 'install' }, abort.signal)).rejects.toThrow();
    expect(targets).toBe(0);
    expect(() => selections.resolve(ref, scope)).toThrow();
  } finally { await fiber.dispose(); await remove(); }
});
