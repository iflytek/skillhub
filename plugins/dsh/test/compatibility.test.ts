import { expect, test } from 'bun:test';
import { readFile } from 'node:fs/promises';
import { assertHostDependencies, hostDependencyBaseline, supportedDshVersions } from '../src/host/compatibility.js';

test('Host dependency guard matches exact peer versions and installed development artifacts', async () => {
  const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  expect(manifest.peerDependenciesMeta).toEqual({ '@deepseek-ai/dsh-user-questions': { optional: true } });
  expect(manifest.peerDependencies['@deepseek-ai/dsh-user-questions']).toBe(supportedDshVersions.join(' || '));
  expect(Object.fromEntries(Object.entries(manifest.peerDependencies).filter(([name]) => !manifest.peerDependenciesMeta?.[name]?.optional))).toEqual(Object.fromEntries(Object.keys(hostDependencyBaseline).map(name => [name, name === '@deepseek-ai/cordis' ? '4.0.4 || 4.0.5' : supportedDshVersions.join(' || ')])));
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

for (const release of supportedDshVersions) {
  test(`coherent Host dependencies accept ${release} and reject mixed releases`, () => {
    const manifest = (name: string) => ({ name, version: name === '@deepseek-ai/cordis' ? '4.0.5' : release });
    expect(assertHostDependencies(manifest)).toBe(release);
    for (const other of supportedDshVersions.filter(candidate => candidate !== release)) {
      for (const target of Object.keys(hostDependencyBaseline).filter(name => name !== '@deepseek-ai/cordis')) {
        expect(() => assertHostDependencies(name => name === target
          ? { name, version: other } : manifest(name))).toThrow('does not match');
      }
    }
  });
}

for (const release of ['0.2.1-rc.2', '0.2.1-rc.5']) {
  test(`${release} cannot use the retained Cordis core even outside the complete runtime check`, () => {
    expect(() => assertHostDependencies(name => ({ name, version: name === '@deepseek-ai/cordis' ? '4.0.4' : release })))
      .toThrow('does not match');
  });
}

test('unvalidated intermediate RCs and future releases fail closed even as coherent graphs', () => {
  for (const release of ['0.2.1-rc.3', '0.2.1-rc.4', '0.2.2-alpha.3']) {
    expect(() => assertHostDependencies(name => ({ name, version: name === '@deepseek-ai/cordis' ? '4.0.5' : release })))
      .toThrow('does not match');
  }
});
