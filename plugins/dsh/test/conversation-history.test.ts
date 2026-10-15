import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import type { SessionLogSnapshot } from '@deepseek-ai/dsh-session-query';
import { historicalSearchCandidate } from '../src/host/conversation-history.js';

const ref = randomUUID();
const scope = { sessionId: 'owner', cwd: '/project', sourceRevision: randomUUID() };
const source = 'https://registry.example';
function history() {
  return {
    session: { id: 'owner', cwd: '/project' }, inheritedEventCount: 0,
    events: [
      { type: 'tool/call', data: { callId: 'call', name: 'astron_skillhub_search', turn: 1, step: 1 } },
      { type: 'tool/result', data: { turn: 1, step: 1,
        message: { toolCallId: 'call', source: { kind: 'tool', callId: 'call' }, isError: false },
        meta: { schemaVersion: 1, kind: 'skillhub-search', query: 'writer', sourceRevision: randomUUID(),
          sourceLabel: source, createdAt: new Date().toISOString(), total: 1, returned: 1, limit: 20,
          items: [{ namespace: 'team', slug: 'writer', latestVersion: '1.0.0', summary: '', selectionRef: ref }] } } },
    ],
  };
}
function read(value = history(), reference = ref) {
  return historicalSearchCandidate(value as unknown as SessionLogSnapshot, scope, source, 'call', reference);
}
test('history binds persisted candidate and exact version while allowing a new process revision', () => {
  expect(read()).toEqual({ namespace: 'team', slug: 'writer', version: '1.0.0' });
  expect(read(history(), randomUUID())).toBeUndefined();
});
test('history rejects foreign, failed, inherited, ambiguous, reordered and unsupported results', () => {
  const changes: ((value: ReturnType<typeof history>) => void)[] = [
    h => { h.session.id = 'other'; }, h => { h.session.cwd = '/other'; },
    h => { h.inheritedEventCount = 2; }, h => { h.events[0]!.data.name = 'other'; },
    h => { h.events[1]!.data.message!.isError = true; },
    h => { h.events[1]!.data.message!.source.callId = 'other'; },
    h => { h.events[1]!.data.message!.toolCallId = 'other'; },
    h => { h.events[1]!.data.turn = 2; }, h => { h.events[1]!.data.step = 2; },
    h => { h.events[1]!.data.meta!.schemaVersion = 2; },
    h => { h.events[1]!.data.meta!.sourceLabel = 'https://other.example'; },
    h => { h.events.push(h.events[0]!); }, h => { h.events.push(h.events[1]!); },
    h => { h.events.reverse(); },
    h => { const meta = h.events[1]!.data.meta!; meta.items.push(meta.items[0]!); meta.returned = meta.total = 2; },
  ];
  for (const change of changes) { const value = history(); change(value); expect(read(value)).toBeUndefined(); }
});
