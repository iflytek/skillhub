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


test('observer diagnostics whitelist states and exact dependency booleans', () => {
  const base = { passed: false, probe: 'runtime-baseline', stage: 'host-result', failureCode: 'HOST_RESULT_TIMEOUT' };
  expect(safeProbeFailure(JSON.stringify({ ...base, diagnostics: { observer: { stage: 'validation-failed', validation: 'home-identity', path: 'secret' },
    dependencies: { loader: true, pluginPackages: false, clientModules: true, skillhub: false, secret: 'private' } } }), 'runtime-baseline'))
    .toEqual({ stage: 'host-result', code: 'HOST_RESULT_TIMEOUT', observer: 'validation-failed', validation: 'home-identity',
      dependencies: { loader: true, pluginPackages: false, clientModules: true, skillhub: false } });
  expect(safeProbeFailure(JSON.stringify({ ...base, diagnostics: { observer: { stage: 'private', validation: 'private-path' }, dependencies: { skillhub: 'secret' } } }), 'runtime-baseline'))
    .toEqual({ stage: 'host-result', code: 'HOST_RESULT_TIMEOUT', observer: 'not-observed', dependencies: {} });
});
