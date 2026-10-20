import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { parseInstallResult } from '../src/client/install-result.js';
import type { InstallResult, InstallFailure, PendingInstallResult } from '../src/client/install-result.js';

const result = { schemaVersion: 1, kind: 'skillhub-install', status: 'accepted', toolCallId: 'call',
  operationId: randomUUID(), hostInstanceId: randomUUID(), sourceRevision: randomUUID(),
  namespace: 'demo', slug: 'writer', version: '1.2.0', targetLabel: '当前项目', hostLabel: 'DSH Host',
  sourceLabel: 'https://registry.example', createdAt: '2026-10-14T00:00:00Z' } satisfies InstallResult;

test('installation history preserves accepted identity but never accepts instructions or asserted success', () => {
  expect(parseInstallResult(result)).toEqual(result);
  for (const value of [null, { ...result, schemaVersion: 2 }, { ...result, status: 'completed' },
    { ...result, operationId: '../other' }, { ...result, hostInstanceId: '' },
    { ...result, sourceLabel: 'https://user:secret@example.com' },
    { ...result, sourceLabel: 'https://example.com?token=secret' },
    { ...result, sourceLabel: 'javascript:alert(1)' }, { ...result, createdAt: 'yesterday' },
    { ...result, path: '/private' }, { ...result, command: 'install' }]) {
    expect(parseInstallResult(value)).toBeUndefined();
  }
});

test('pending history is strictly disjoint from accepted and failed history', async () => {
  const { parsePendingInstallResult, parseInstallFailure } = await import('../src/client/install-result.js');
  const pending = { schemaVersion: 1, kind: 'skillhub-install', status: 'awaiting-confirmation', toolCallId: 'call',
    namespace: 'team', slug: 'writer', version: '1.0.0', selectionRef: randomUUID(),
    sourceLabel: 'https://registry.example', sourceRevision: randomUUID(), createdAt: '2026-10-20T00:00:00Z' } satisfies PendingInstallResult;
  expect(parsePendingInstallResult(pending)).toEqual(pending);
  expect(parseInstallResult(pending)).toBeUndefined();
  expect(parseInstallFailure(pending)).toBeUndefined();
  expect(parsePendingInstallResult(result)).toBeUndefined();
  const failure = { schemaVersion: 1, kind: 'skillhub-install', status: 'not-confirmed', toolCallId: 'call', code: 'CONTEXT_STALE' } satisfies InstallFailure;
  expect(parseInstallFailure(failure)).toEqual(failure);
  expect(parsePendingInstallResult(failure)).toBeUndefined();
  for (const extra of [{ operationId: randomUUID() }, { detailToken: 'secret' }, { targetLabel: 'user' },
    { path: '/private' }, { fingerprint: 'abc' }, { scope: 'user' }, { selectionRef: '../bad' },
    { sourceLabel: 'https://secret:token@example.com' }]) {
    expect(parsePendingInstallResult({ ...pending, ...extra })).toBeUndefined();
  }
});


test('cancelled history is strict and never claims an accepted operation', async () => {
  const { isCancelledInstallResult } = await import('../src/client/install-result.js');
  const value = { schemaVersion: 1, kind: 'skillhub-install', status: 'cancelled', toolCallId: 'cancel-call' };
  expect(isCancelledInstallResult(value)).toBe(true);
  expect(isCancelledInstallResult({ ...value, operationId: 'pretend-operation' })).toBe(false);
  expect(parseInstallResult(value)).toBeUndefined();
});
