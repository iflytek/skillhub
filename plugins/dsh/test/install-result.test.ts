import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { parseInstallResult } from '../src/client/install-result.js';
import type { InstallResult } from '../src/client/install-result.js';

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
