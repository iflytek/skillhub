import { expect, test } from 'bun:test';
import { RemoteError } from '@deepseek-ai/dsh-typert-protocol';
import { installationFailureCode } from '../src/host/installation-failure.js';
import { parseInstallFailure, parseInstallResult } from '../src/client/install-result.js';

test('known Host errors map to bounded failure codes without carrying diagnostics', () => {
  for (const [code, expected] of [
    ['skillhub/context-stale', 'CONTEXT_STALE'], ['HOST_MISMATCH', 'CONTEXT_STALE'],
    ['REQUEST_KEY_CONFLICT', 'REQUEST_CONFLICT'], ['skillhub/request-conflict', 'REQUEST_CONFLICT'],
    ['skillhub/source-not-configured', 'SOURCE_NOT_CONFIGURED'], ['skillhub/target-unavailable', 'TARGET_UNAVAILABLE'],
    ['unexpected', 'UNCONFIRMED'],
  ] as const) expect(installationFailureCode({ code, message: '/private secret' })).toBe(expected);
  for (const [reason, expected] of [['ANONYMOUS_DENIED', 'SOURCE_DENIED'], ['NOT_FOUND', 'SKILL_UNAVAILABLE'],
    ['RATE_LIMITED', 'RATE_LIMITED'], ['NETWORK_ERROR', 'SOURCE_UNAVAILABLE']] as const) {
    expect(installationFailureCode(new RemoteError('skillhub/registry-error', '/private diagnostic', { reason, retryable: false }))).toBe(expected);
  }
  expect(installationFailureCode(null)).toBe('UNCONFIRMED');
  expect(installationFailureCode(new Error('secret'))).toBe('UNCONFIRMED');
});

test('failure history cannot be treated as acceptance or introduce an arbitrary diagnostic', () => {
  const failure = { schemaVersion: 1, kind: 'skillhub-install', status: 'not-confirmed', toolCallId: 'call', code: 'REQUEST_CONFLICT' } as const;
  expect(parseInstallFailure(failure)).toEqual(failure);
  expect(parseInstallResult(failure)).toBeUndefined();
  for (const value of [{ ...failure, code: '/private' }, { ...failure, message: 'secret' },
    { ...failure, operationId: 'fake' }, { ...failure, schemaVersion: 2 }, null]) {
    expect(parseInstallFailure(value)).toBeUndefined();
  }
});
