import { expect, test } from 'bun:test';
import { operationDiagnostic } from '../src/host/operation-diagnostic.js';
import type { NativeOperation } from '../src/host/generated/native-contract.js';

const operation = { operationId: 'operation', kind: 'install' } as NativeOperation;
test('execution diagnostics expose only operation identity and allowlisted categories', () => {
  for (const [error, expected] of [
    [Object.assign(new Error('/private/token=secret'), { code: 'EPERM', path: '/private' }), 'EPERM'],
    [{ code: 'ECONNRESET', url: 'https://secret@example.test' }, 'ECONNRESET'],
    [{ exitCode: 6, details: { token: 'secret' } }, 'VALIDATION'],
    [{ code: 'secret', exitCode: 4 }, 'FILESYSTEM'],
    [{ code: 'secret', exitCode: 99 }, 'UNCLASSIFIED'],
    [new Error('secret'), 'UNCLASSIFIED'], [null, 'UNCLASSIFIED'],
  ] as const) {
    expect(operationDiagnostic(operation, error)).toEqual({ operationId: 'operation', kind: 'install', code: expected });
  }
});
