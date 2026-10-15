import { expect, test } from 'bun:test';
// @ts-expect-error The development-only Node entry has no TypeScript declarations.
import { safeProbeFailure } from '../scripts/smoke-native-host.mjs';

test('probe diagnostics expose only fixed stage and code from the requested probe', () => {
  const record = { passed: false, probe: 'runtime-baseline', stage: 'host-assertion',
    failureCode: 'PROBE_FAILED', error: 'private-secret', report: '/private/path' };
  expect(safeProbeFailure('private raw line\n' + JSON.stringify(record), 'runtime-baseline'))
    .toEqual({ stage: 'host-assertion', code: 'PROBE_FAILED' });
  expect(safeProbeFailure(JSON.stringify({ ...record, failureCode: 'HOST_RESULT_TIMEOUT' }), 'runtime-baseline'))
    .toEqual({ stage: 'host-assertion', code: 'HOST_RESULT_TIMEOUT' });
  expect(safeProbeFailure(JSON.stringify(record), 'plugin-lifecycle')).toBeUndefined();
  for (const replacement of [{ stage: 'private-secret' }, { failureCode: 'private-secret' }, { passed: true }]) {
    expect(safeProbeFailure(JSON.stringify({ ...record, ...replacement }), 'runtime-baseline')).toBeUndefined();
  }
  for (const input of ['null', '{}', 'raw secret', '[1,2]']) {
    expect(safeProbeFailure(input, 'runtime-baseline')).toBeUndefined();
  }
});
