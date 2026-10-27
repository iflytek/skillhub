import { expect, test } from 'bun:test';
import SkillHubService from '@astron-team/skillhub-dsh';

test.each([undefined, 'user', 'project'] as const)('explicit install defaults to user and honors supplied scope: %s', async scope => {
  const requests: unknown[] = [];
  const signal = new AbortController().signal;
  const context = { sessionId: 'session', cwd: '/project', callId: 'install' };
  const service = { installConversation: async (...args: unknown[]) => { requests.push(args); return undefined; } };
  await Reflect.apply(SkillHubService.prototype.installConversationWithChoice, service,
    [{ selectionRef: 'trusted-ref', ...(scope ? { scope } : {}) }, context, signal]);
  expect(requests).toEqual([[{ selectionRef: 'trusted-ref', scope: scope ?? 'user' }, context, signal]]);
});

test('default scope does not allow paths, invented confirmation, or ambiguous coordinates', async () => {
  let calls = 0;
  const service = { installConversation: async () => { calls++; } };
  for (const request of [{ selectionRef: 'ref', path: '/tmp' }, { selectionRef: 'ref', confirmed: true }, { slug: 'writer' }, { selectionRef: 'ref', namespace: 'team', slug: 'writer' }]) {
    await expect(Reflect.apply(SkillHubService.prototype.installConversationWithChoice, service,
      [request, { sessionId: 'session', cwd: '/project', callId: 'install' }, new AbortController().signal])).rejects.toThrow();
  }
  expect(calls).toBe(0);
});
