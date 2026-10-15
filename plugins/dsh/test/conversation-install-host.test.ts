import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';
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
  Reflect.set(ctx.skillhub, 'resolveConversationSelection', (_agent: unknown, reference: string) => {
    if (reference !== 'viewed') throw new Error('stale');
    return { detailToken: 'fixed-detail' };
  });
  Reflect.set(ctx.skillhub, 'installationTargetsFor', async () => ({ targets: [{ scope: 'user', targetToken: 'user-target' }] }));
  Reflect.set(ctx.skillhub, 'prepareInstallation', async (...args: unknown[]) => { preparations.push(args); return {}; });
  try {
    const context = { sessionId: 'owner', cwd: '/project', callId: 'install-call' };
    await ctx.skillhub.installConversation({ selectionRef: 'viewed' }, context, signal);
    expect(preparations).toEqual([['owner', 'fixed-detail', 'user-target', 'conversation', signal]]);
    await expect(ctx.skillhub.installConversation({ selectionRef: 'viewed', scope: 'project' }, context, signal)).rejects.toThrow();
    await expect(ctx.skillhub.installConversation({ selectionRef: 'expired' }, context, signal)).rejects.toThrow('stale');
    expect(preparations).toHaveLength(1);
  } finally { await fiber.dispose(); await remove(); }
});
