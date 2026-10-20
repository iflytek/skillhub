import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { conversationInstallKey, conversationInstallRequest } from '../src/host/conversation-install-request.js';

test('installation accepts a reference or complete coordinates and preserves omitted scope for visible confirmation', () => {
  expect(conversationInstallRequest.parse({ selectionRef: 'selection' })).toEqual({ selectionRef: 'selection' });
  expect(conversationInstallRequest.parse({ selectionRef: 'selection', scope: 'user' })).toEqual({ selectionRef: 'selection', scope: 'user' });
  expect(conversationInstallRequest.parse({ namespace: 'demo', slug: 'document-helper', scope: 'project' }))
    .toEqual({ namespace: 'demo', slug: 'document-helper', scope: 'project' });
  for (const value of [null, {}, { namespace: 'demo' }, { slug: 'skill' },
    { selectionRef: 'selection', namespace: 'demo', slug: 'skill' },
    { selectionRef: '', namespace: 'demo', slug: 'skill' },
    { selectionRef: 'selection', scope: 'other' }, { selectionRef: 'selection', scope: null },
    { selectionRef: 'x'.repeat(2049) }, { namespace: '..', slug: 'skill' },
    { namespace: 'demo', slug: '../skill' }, { namespace: 'demo', slug: 'a\\b' },
    { namespace: 'demo', slug: 'a\nb' }]) {
    expect(conversationInstallRequest.safeParse(value).success).toBe(false);
  }
  for (const key of ['path', 'targetToken', 'detailToken', 'requestKey', 'hostInstanceId', 'sourceRevision', 'downloadUrl', 'version']) {
    expect(conversationInstallRequest.safeParse({ selectionRef: 'selection', [key]: 'untrusted' }).success).toBe(false);
    expect(conversationInstallRequest.safeParse({ namespace: 'demo', slug: 'skill', [key]: 'untrusted' }).success).toBe(false);
  }
});

test('request identity is repeatable, host scoped and unambiguous across conversation and call boundaries', () => {
  const context = { hostInstanceId: randomUUID(), sessionId: 'session', callId: 'call' };
  const key = conversationInstallKey(context);
  expect(z.uuid().safeParse(key).success).toBe(true);
  expect(conversationInstallKey({ ...context })).toBe(key);
  expect(conversationInstallKey({ ...context, hostInstanceId: context.hostInstanceId.toUpperCase() })).toBe(key);
  for (const change of [{ hostInstanceId: randomUUID() }, { sessionId: 'other' }, { callId: 'other' }]) {
    expect(conversationInstallKey({ ...context, ...change })).not.toBe(key);
  }
  expect(conversationInstallKey({ ...context, sessionId: 'a:b', callId: 'c' }))
    .not.toBe(conversationInstallKey({ ...context, sessionId: 'a', callId: 'b:c' }));
  expect(() => conversationInstallKey({ ...context, callId: '' })).toThrow();
  expect(() => conversationInstallKey({ ...context, sessionId: '' })).toThrow();
  expect(() => conversationInstallKey({ ...context, hostInstanceId: 'invalid' })).toThrow();
});
