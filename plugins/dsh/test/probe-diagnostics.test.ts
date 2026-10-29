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

test('availability diagnostics retain only fixed checkpoint labels, never raw failure data', () => {
  const checkpoint = { scenario: 'user-winner', checkpoint: 'assertion', assertion: 'discovery' };
  const base = { passed: false, probe: 'skill-availability', stage: 'host-assertion', failureCode: 'HOST_ASSERTION_FAILED',
    error: '/private/AVAILABILITY_PRIVATE_SENTINEL', diagnostics: { availability: { ...checkpoint, error: 'secret', path: '/private' } } };
  const safe = safeProbeFailure(JSON.stringify(base), 'skill-availability');
  expect(safe.availability).toEqual(checkpoint);
  expect(JSON.stringify(safe)).not.toMatch(/private|secret|SENTINEL/);
  for (const key of ['scenario', 'checkpoint', 'assertion']) {
    expect(safeProbeFailure(JSON.stringify({ ...base, diagnostics: { availability: { ...checkpoint, [key]: '/private/secret' } } }), 'skill-availability').availability).toBeUndefined();
  }
  expect(safeProbeFailure(JSON.stringify({ ...base, probe: 'plugin-lifecycle' }), 'plugin-lifecycle').availability).toBeUndefined();
});

test('both toggle probes expose only bounded fixture locations and fixed labels', () => {
  for (const probe of ['skill-toggle', 'skill-toggle-watch-off']) {
    const base = { passed: false, probe, stage: 'host-assertion', failureCode: 'HOST_ASSERTION_FAILED',
      diagnostics: { toggle: { scenario: 'disabled-upgrade', assertion: 'equal', error: 'private-secret' },
        location: { fixture: 'skill-toggle-fixture.mjs', line: 152, stack: '/private/secret' } } };
    const safe = safeProbeFailure(JSON.stringify(base), probe);
    expect(safe.toggle).toEqual({ scenario: 'disabled-upgrade', assertion: 'equal' });
    expect(safe.location).toEqual({ fixture: 'skill-toggle-fixture.mjs', line: 152 });
    expect(JSON.stringify(safe)).not.toMatch(/private|secret|stack/);
    for (const line of [0, -1, 10001, 1.5, '152', null]) {
      expect(safeProbeFailure(JSON.stringify({ ...base, diagnostics: { location: { fixture: 'skill-toggle-fixture.mjs', line } } }), probe).location).toBeUndefined();
    }
    expect(safeProbeFailure(JSON.stringify({ ...base, diagnostics: { location: { fixture: '/private/skill-toggle-fixture.mjs', line: 3 } } }), probe).location).toBeUndefined();
    for (const key of ['scenario', 'assertion']) {
      expect(safeProbeFailure(JSON.stringify({ ...base, diagnostics: { toggle: { ...base.diagnostics.toggle, [key]: 'private-secret' } } }), probe).toggle).toBeUndefined();
    }
    expect(safeProbeFailure(JSON.stringify({ ...base, probe: 'runtime-baseline' }), 'runtime-baseline').location).toBeUndefined();
  }
});
