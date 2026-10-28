import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import SkillHubService from '@astron-team/skillhub-dsh';
import { managementRequests } from '../src/host/management-tool.js';

function harness(answer?: { selected: string[]; custom?: string }, providerError?: string) {
  const item = { itemKey: 'key', namespace: 'demo', slug: 'writer', version: '1.0.0', scope: 'user', state: 'present' };
  const agent = { session: { id: 'session', header: { cwd: '/project' } } };
  let writes = 0;
  let asks = 0;
  let reads = 0;
  let revision = 'source';
  const operation = { operationId: randomUUID(), context: { hostInstanceId: randomUUID() } };
  const runtime = { getStatus: () => ({ hostInstanceId: operation.context.hostInstanceId }), findPlanOperation: async () => undefined };
  const service = {
    managementRequests: new Map(), managementConfirmations: new Map(), localRuntime: runtime,
    ctx: { invocation: { request: { args: { agentId: 'session' } } }, get: (name: string) => name === 'agents' ? { get: () => agent }
      : name === 'userQuestions' && (answer || providerError) ? { ask: async () => { asks++; if (providerError) throw { code: providerError }; return { answers: [{ id: 'skillhub-management', ...answer }] }; } } : undefined },
    getContext: () => ({ sourceRevision: revision, registryUrl: 'https://registry.example' }),
    assertSource: (source: string) => { if (source !== revision) throw new Error('stale'); },
    listInstalledForAgent: async (_id: string, source: string) => { if (source !== revision) throw new Error('stale'); return { items: [item], complete: true, issues: [], unavailable: [] }; },
    prepareUpgradeForVersion: async () => ({ status: 'ready', planId: 'plan', candidateVersion: '2.0.0', reason: 'newer-version' }),
    prepareToggleForAgent: async () => ({ status: 'ready', planId: 'toggle-plan', reason: 'disabled' }),
    prepareUninstallForAgent: async () => ({ status: 'ready', planId: 'plan', reason: 'uninstall' }),
    readInstalledItem: async () => { reads++; return {}; },
    executePlanForAgent: async (_id: string, input: { requestKey: string }) => {
      expect(input.requestKey).toMatch(/^[0-9a-f-]{14}4/); writes++; return operation;
    },
  };
  Object.setPrototypeOf(service, SkillHubService.prototype);
  const context = { sessionId: 'session', cwd: '/project', callId: 'call' };
  const run = (action = 'upgrade', extra = {}) => Reflect.apply(SkillHubService.prototype.manageConversation, service,
    [action, { sourceRevision: 'source', itemKey: 'key', ...extra }, context, new AbortController().signal]);
  const confirm = (reference: string, confirmed: boolean) => Reflect.apply(SkillHubService.prototype.confirmConversationManagement, service,
    [reference, confirmed, new AbortController().signal]);
  return { run, confirm, service, context, agent, writes: () => writes, asks: () => asks, reads: () => reads, changeSource: () => { revision = 'new'; } };
}

test('model requests reject fabricated confirmation, paths and arbitrary plan IDs', () => {
  for (const extra of [{ confirmed: true }, { planId: randomUUID() }, { path: '/elsewhere' }, { scope: 'user' }]) {
    expect(managementRequests.upgrade.safeParse({ sourceRevision: 'source', itemKey: 'key', ...extra }).success).toBe(false);
  }
});

test('explicitly identified installation still needs human confirmation; duplicate answers submit once', async () => {
  const h = harness();
  const pending = await h.run();
  expect(pending.status).toBe('awaiting-confirmation'); expect(h.writes()).toBe(0);
  const [first, second] = await Promise.all([h.confirm(pending.confirmationRef, true), h.confirm(pending.confirmationRef, true)]);
  expect(first).toEqual(second); expect(first.status).toBe('accepted'); expect(h.writes()).toBe(1);
});

test('native question receives exact target and only exact human option confirms', async () => {
  const yes = harness({ selected: ['确认更新'] });
  expect((await yes.run()).status).toBe('accepted'); expect(yes.asks()).toBe(1); expect(yes.writes()).toBe(1);
  expect((await yes.run()).status).toBe('accepted'); expect(yes.writes()).toBe(1);
  for (const answer of [{ selected: ['取消'] }, { selected: ['确认更新'], custom: 'force' }, { selected: ['确认更新', '取消'] }, { selected: [] }]) {
    const no = harness(answer); expect((await no.run()).status).toBe('cancelled'); expect(no.writes()).toBe(0);
  }
});

test('no-provider falls back; cancellation and changed context cannot mutate', async () => {
  const h = harness(undefined, 'NO_PROVIDER'); const result = await h.run('uninstall');
  expect(result.status).toBe('awaiting-confirmation'); expect(h.writes()).toBe(0);
  expect((await h.confirm(result.confirmationRef, false)).status).toBe('cancelled');
  expect((await h.confirm(result.confirmationRef, true)).status).toBe('cancelled'); expect(h.writes()).toBe(0);
  const changed = harness(); const pending = await changed.run(); changed.changeSource();
  await expect(changed.confirm(pending.confirmationRef, true)).rejects.toThrow('stale'); expect(changed.writes()).toBe(0);
  const cwd = harness(); const before = await cwd.run(); cwd.agent.session.header.cwd = '/other';
  await expect(cwd.confirm(before.confirmationRef, true)).rejects.toThrow('expired'); expect(cwd.writes()).toBe(0);
});

test('same call cannot swap target parameters; stale item never prepares or writes', async () => {
  const h = harness(); await h.run();
  await expect(h.run('upgrade', { version: '3.0.0' })).rejects.toThrow('Request changed');
  const stale = harness(); await expect(stale.run('upgrade', { itemKey: 'old' })).rejects.toThrow('selection changed');
  expect(stale.writes()).toBe(0);
});

test('response loss resolves durable original operation without submitting a replacement', async () => {
  const h = harness(); const pending = await h.run();
  const stored = { operationId: randomUUID(), phase: 'accepted', context: { hostInstanceId: randomUUID() } };
  let submitted = 0; let originalKey = '';
  Reflect.set(h.service, 'executePlanForAgent', async (_id: string, request: { requestKey: string }) => {
    submitted++; originalKey = request.requestKey; throw new Error('response lost');
  });
  Reflect.set(h.service.localRuntime, 'findPlanOperation', async (request: { requestKey: string; ownerId: string }) => {
    expect(request.requestKey).toBe(originalKey); expect(request.ownerId).toBe('session'); return stored;
  });
  const result = await h.confirm(pending.confirmationRef, true);
  expect(result.operation).toMatchObject(stored); expect(submitted).toBe(1);
  expect((await h.confirm(pending.confirmationRef, true)).operation).toMatchObject(stored); expect(submitted).toBe(1);
});

test('source changes during native human confirmation never submit', async () => {
  const h = harness({ selected: ['确认更新'] });
  const get = h.service.ctx.get;
  h.service.ctx.get = ((name: string) => name === 'userQuestions' ? { ask: async () => {
    h.changeSource(); return { answers: [{ id: 'skillhub-management', selected: ['确认更新'] }] };
  } } : get(name)) as typeof get;
  await expect(h.run()).rejects.toThrow('stale'); expect(h.writes()).toBe(0);
});

for (const [action, label] of [['enable', '确认启用'], ['disable', '确认停用']]) {
  test(`${action} requires exact real approval and does not replay duplicate confirmation`, async () => {
    const h = harness();
    const pending = await h.run(action);
    expect(pending.action).toBe(action); expect(pending.status).toBe('awaiting-confirmation'); expect(h.writes()).toBe(0);
    const [a, b] = await Promise.all([h.confirm(pending.confirmationRef, true), h.confirm(pending.confirmationRef, true)]);
    expect(a).toEqual(b); expect(a.status).toBe('accepted'); expect(h.writes()).toBe(1);
    const cancelled = harness({ selected: ['取消'] });
    expect((await cancelled.run(action)).status).toBe('cancelled'); expect(cancelled.writes()).toBe(0);
    const native = harness({ selected: [label] });
    expect((await native.run(action)).status).toBe('accepted'); expect(native.writes()).toBe(1);
    const stale = harness(); await expect(stale.run(action, { itemKey: 'old' })).rejects.toThrow('selection changed');
    expect(stale.writes()).toBe(0);
  });
}

test('toggle requests accept exact references only and never accept desired paths or authorization flags', () => {
  for (const schema of [managementRequests.enable, managementRequests.disable]) {
    expect(schema.safeParse({ sourceRevision: 'source', itemKey: 'key' }).success).toBe(true);
    for (const extra of [{ confirmed: true }, { path: '/elsewhere' }, { version: '2' }, { scope: 'user' }]) {
      expect(schema.safeParse({ sourceRevision: 'source', itemKey: 'key', ...extra }).success).toBe(false);
    }
  }
});
