import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';

async function fixture() {
  let afterFetch = () => {};
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    const url = new URL(request.url);
    if (url.pathname.endsWith('/resolve')) afterFetch();
    if (url.pathname === '/api/v1/skills/team/writer') return Response.json({ data: { id: 1, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '' } });
    if (url.pathname.endsWith('/versions') || url.pathname.endsWith('/reviews')) return Response.json({ data: { items: [], total: 0, page: 0, size: 1 } });
    return url.pathname.endsWith('/resolve') ? Response.json({ data: {
      namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint: 'abc', downloadUrl: '/download',
    } }) : new Response('# Writer');
  } });
  const oldRef = randomUUID();
  const agent = { session: { id: 'owner', header: { cwd: '/project' } } };
  let currentAgent = agent;
  let afterRead = () => {};
  const history = { session: { id: 'owner', cwd: '/project' }, inheritedEventCount: 0, events: [
    { type: 'tool/call', data: { callId: 'call', name: 'astron_skillhub_search', turn: 1, step: 1 } },
    { type: 'tool/result', data: { turn: 1, step: 1, message: { toolCallId: 'call', source: { kind: 'tool', callId: 'call' } },
      meta: { schemaVersion: 1, kind: 'skillhub-search', query: 'writer', sourceRevision: randomUUID(),
        sourceLabel: server.url.href.replace(/\/$/, ''), createdAt: new Date().toISOString(), total: 1, returned: 1, limit: 20,
        items: [{ namespace: 'team', slug: 'writer', latestVersion: '1.0.0', summary: '', selectionRef: oldRef }] } } },
  ] };
  const ctx = new Context();
  const removers = [ctx.provide('agents', { get: (id: string) => id === 'owner' ? currentAgent : undefined } as never),
    ctx.provide('sessionQuery', { readSession: async (id: string) => { expect(id).toBe('owner'); afterRead(); return history; } } as never),
    ctx.provide('invocation', { request: { args: { agentId: 'owner' } } } as never)];
  const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
  await fiber;
  return { ctx, oldRef, agent, history, setAfterRead: (action: () => void) => { afterRead = action; },
    setAfterFetch: (action: () => void) => { afterFetch = action; },
    replaceAgent: () => { currentAgent = { session: { ...agent.session } }; },
    async close() { await fiber.dispose(); for (const remove of removers.reverse()) await remove(); await server.stop(true); } };
}

test('persisted search reopens exact detail with fresh unacknowledged reference and never revives old installation authority', async () => {
  const f = await fixture();
  const signal = new AbortController().signal;
  try {
    await expect(f.ctx.skillhub.openConversationDetail(f.oldRef, signal)).rejects.toThrow();
    const detail = await f.ctx.skillhub.reopenConversationDetail('call', f.oldRef, signal);
    expect(detail).toMatchObject({ namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1 });
    expect(detail.selectionRef).not.toBe(f.oldRef);
    const selections = Reflect.get(f.ctx.skillhub, 'selections');
    const scope = { sessionId: 'owner', cwd: '/project', sourceRevision: f.ctx.skillhub.getContext().sourceRevision };
    expect(() => selections.resolve(detail.selectionRef, scope)).toThrow();
    expect(f.ctx.skillhub.acknowledgeConversationDetail(detail.selectionRef, detail.detailToken)).toBe(true);
    expect(selections.resolve(detail.selectionRef, scope).snapshot.version).toBe('1.0.0');
    expect(() => selections.resolve(f.oldRef, scope)).toThrow();
    await expect(f.ctx.skillhub.openConversationDetail(f.oldRef, signal)).rejects.toThrow();
    expect(f.ctx.skillhub.getLocalRuntimeStatus()).toBeNull();
  } finally { await f.close(); }
});

test('history reread rejects replaced agents, changed cwd, canceled requests and forged references', async () => {
  for (const scenario of ['agent', 'cwd', 'abort', 'forged', 'source', 'missing-query', 'query-error', 'revision', 'during-detail']) {
    const f = await fixture();
    const abort = new AbortController();
    try {
      if (scenario === 'query-error') f.setAfterRead(() => { throw new Error('private history storage failure'); });
      if (scenario === 'revision') f.setAfterRead(() => Reflect.set(f.ctx.skillhub, 'sourceRevision', randomUUID()));
      if (scenario === 'during-detail') f.setAfterFetch(f.replaceAgent);
      if (scenario === 'agent') f.setAfterRead(f.replaceAgent);
      if (scenario === 'cwd') f.setAfterRead(() => { f.agent.session.header.cwd = '/changed'; });
      if (scenario === 'abort') f.setAfterRead(() => abort.abort());
      if (scenario === 'source') f.history.events[1]!.data.meta!.sourceLabel = 'https://changed.example';
      if (scenario === 'missing-query') Reflect.set(f.ctx, 'sessionQuery', undefined);
      await expect(f.ctx.skillhub.reopenConversationDetail('call', scenario === 'forged' ? randomUUID() : f.oldRef, abort.signal)).rejects.toThrow();
      expect(Reflect.get(f.ctx.skillhub, 'selections').records.size).toBe(0);
    } finally { await f.close(); }
  }
});
