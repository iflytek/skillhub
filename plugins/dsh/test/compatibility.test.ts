import { expect, test } from 'bun:test';
import { readFile } from 'node:fs/promises';
import { assertHostDependencies, hostDependencyBaseline } from '../src/host/compatibility.js';

test('Host dependency guard matches exact peer versions and installed development artifacts', async () => {
  const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  expect(manifest.peerDependenciesMeta).toEqual({ '@deepseek-ai/dsh-user-questions': { optional: true } });
  expect(Object.fromEntries(Object.entries(manifest.peerDependencies).filter(([name]) => !manifest.peerDependenciesMeta?.[name]?.optional))).toEqual({ ...hostDependencyBaseline, '@deepseek-ai/cordis': '4.0.4 || 4.0.5' });
  expect(() => assertHostDependencies()).not.toThrow();
});

test('every mismatched, missing or malformed Host package fails closed without exposing loader diagnostics', () => {
  for (const name of Object.keys(hostDependencyBaseline)) {
    for (const invalid of [null, {}, { name, version: '0.2.1-alpha.2' }, { name: 'other', version: '0.2.0-rc.2' }]) {
      expect(() => assertHostDependencies(candidate => candidate === name ? invalid
        : { name: candidate, version: hostDependencyBaseline[candidate as keyof typeof hostDependencyBaseline] }))
        .toThrow('does not match');
    }
  }
  expect(() => assertHostDependencies(() => { throw new Error('/private/host/path'); })).toThrow('could not be verified');
});
