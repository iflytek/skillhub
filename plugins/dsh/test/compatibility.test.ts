import { expect, test } from 'bun:test';
import { readFile } from 'node:fs/promises';
import { admitsDshVersion, assertHostDependencies, dshRuntimeRange, hostDependencyBaseline, minimumDshVersion, supportsCordis, testedDshVersions } from '../src/host/compatibility.js';

const favoritesSdk = '@deepseek-ai/dsh-atomic-write';

test('Host declaration is a minimum range and development dependencies remain fixed', async () => {
  const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  expect(manifest.engines.dsh).toBe(dshRuntimeRange);
  for (const [name, range] of Object.entries(manifest.peerDependencies)) {
    expect(range).toBe(name === '@deepseek-ai/cordis' ? '>=4.0.4' : dshRuntimeRange);
  }
  expect(manifest.devDependencies['@deepseek-ai/dsh-agent']).toBe('0.2.1-rc.2');
  expect(manifest.devDependencies['@deepseek-ai/dsh-typert-generator']).toBe('0.2.0-rc.2');
  expect(() => assertHostDependencies()).not.toThrow();
});

test('favorites SDK is declared separately without becoming an inventory gate', async () => {
  const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  expect(Object.hasOwn(hostDependencyBaseline, favoritesSdk)).toBe(false);
  expect(manifest.peerDependencies[favoritesSdk]).toBe(dshRuntimeRange);
  expect(manifest.devDependencies[favoritesSdk]).toBe('0.2.1-rc.2');
});

test('future RC, prerelease and stable releases are admitted without claiming acceptance', () => {
  for (const release of [...testedDshVersions, '0.2.2-rc.4', '0.2.2-rc.5', '0.2.3-alpha.1', '0.3.0', '1.0.0', '0.2.2-rc.3+desktop.1']) {
    expect(admitsDshVersion(release)).toBe(true);
    expect(assertHostDependencies(name => ({ name, version: name === '@deepseek-ai/cordis' ? '4.2.0' : release }))).toBe(release);
  }
  expect(testedDshVersions).not.toContain('0.2.2-rc.4');
});

test('invalid, missing and older dependency manifests remain rejected without loader diagnostics', () => {
  for (const name of Object.keys(hostDependencyBaseline)) {
    for (const invalid of [null, {}, { name, version: 'invalid' }, { name, version: '0.1.9' }, { name: 'other', version: minimumDshVersion }]) {
      expect(() => assertHostDependencies(candidate => candidate === name ? invalid
        : { name: candidate, version: hostDependencyBaseline[candidate as keyof typeof hostDependencyBaseline] })).toThrow('does not match');
    }
  }
  for (const version of ['0.2.0-rc.1', '0.2.0-alpha.9', '0.1.99', 'v0.2.2', '0.02.2', '0.2.2 ', ' 0.2.2', '0.2.2\n', '', undefined]) expect(admitsDshVersion(version)).toBe(false);
  expect(() => assertHostDependencies(() => { throw new Error('/private/host/path'); })).toThrow('could not be verified');
});

test('Cordis minimum is independent of an exact patch or DSH RC', () => {
  for (const release of ['0.2.0-rc.2', '0.2.2-rc.3', '0.2.2-rc.5', '1.0.0']) {
    for (const core of ['4.0.4', '4.0.5', '4.1.0', '4.1.1', '4.2.0', '5.0.0', '4.1.0+desktop.1']) expect(supportsCordis(release, core)).toBe(true);
    for (const core of ['4.0.3', '3.9.0', 'invalid']) expect(supportsCordis(release, core)).toBe(false);
  }
});
