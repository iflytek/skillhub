import { expect, test } from 'bun:test';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { testedDshVersions } from '../src/host/compatibility.js';
import { assertNodeRuntime, nodeRuntimeRange, assertRuntimeCompatibility, clientPackageBaseline, runtimePackageBaseline } from '../src/host/runtime-compatibility.js';

type FixturePackage = { name: string; version: string; manifestPath: string; dependencies?: Record<string, string> };
function fixture(release = '0.2.0-rc.2', root = '/fixture/packages') {
  const packages = new Map<string, FixturePackage>(Object.entries(runtimePackageBaseline).map(([name, version]) => [name, {
    name, version: name.startsWith('@deepseek-ai/dsh') ? release : version, manifestPath: join(root, name, 'package.json'),
  }]));
  for (const name of clientPackageBaseline) packages.set(name, { name, version: release, manifestPath: join(root, name, 'package.json') });
  const rootManifest = packages.get('@deepseek-ai/dsh')!;
  rootManifest.dependencies = Object.fromEntries([...packages].filter(([name]) => name !== rootManifest.name).map(([name, item]) => [name, item.version]));
  const clients = new Map(clientPackageBaseline.map(name => [name, { name, version: release }]));
  const services = {
    packageOf: (name: string) => packages.get(name),
    clientPath: (name: string) => clients.has(name as typeof clientPackageBaseline[number]) ? join(root, name, 'lib/client.js') : undefined,
    hasAgentLookup: () => true,
  };
  const readManifest = (path: string) => [...packages.values()].find(item => item.manifestPath === path);
  const check = () => assertRuntimeCompatibility(services, 'file:///plugin/lib/index.js', (_file, name) => clients.get(name as typeof clientPackageBaseline[number]), readManifest);
  return { packages, clients, services, readManifest, check };
}

function hasNewNodeFloor() { const [major, minor] = process.versions.node.split('.').map(Number); return major! > 24 || major === 24 && minor! >= 9; }

test('runtime Client requirements match injected modules and missing capabilities reject writes', async () => {
  const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  expect(clientPackageBaseline).toEqual(manifest.dsh.client.inject);
  const { services, readManifest } = fixture();
  expect(() => assertRuntimeCompatibility({ ...services, hasAgentLookup: () => false }, 'file:///plugin/index.js', () => ({}), readManifest)).toThrow('local writes are disabled');
  expect(fixture().check()).toBe('0.2.0-rc.2');
});

for (const release of [...testedDshVersions, '0.2.2-rc.4', '0.2.2-rc.5', '0.2.3-alpha.1', '1.0.0', '0.2.2-rc.3+desktop.1']) {
  test(`coherent ${release} graph is not rejected merely because it is unlisted`, () => {
    const { check } = fixture(release);
    if (!hasNewNodeFloor() && !release.startsWith('0.2.0') && !release.startsWith('0.2.1')) expect(check).toThrow('local writes are disabled');
    else expect(check()).toBe(release);
  });
}

test('required packages remain present, parseable and above minimum; manifest errors stay private', () => {
  const { packages, check } = fixture();
  for (const [name, value] of [...packages]) {
    packages.delete(name); expect(check).toThrow('local writes are disabled'); packages.set(name, value);
    const old = value.version; value.version = 'invalid'; expect(check).toThrow('local writes are disabled'); value.version = old;
    expect(check()).toBe('0.2.0-rc.2');
  }
  const { services } = fixture();
  expect(() => assertRuntimeCompatibility(services, 'file:///plugin/index.js', () => { throw new Error('/private/path'); })).toThrow('Host runtime baseline could not be verified; local writes are disabled.');
});

test('Host anchors cannot resolve nested plugin peer copies, even at the same version', () => {
  const { services, readManifest, packages, clients } = fixture();
  const name = '@deepseek-ai/dsh-fs';
  const check = () => assertRuntimeCompatibility({ ...services, packageOf: (candidate, parent) => {
    const value = services.packageOf(candidate);
    return candidate === name && parent !== 'file:///plugin/index.js' ? { ...value!, manifestPath: '/nested/package.json' } : value;
  } }, 'file:///plugin/index.js', (_file, name) => clients.get(name as typeof clientPackageBaseline[number]), path => path === '/nested/package.json' ? packages.get(name) : readManifest(path));
  expect(check).toThrow('local writes are disabled');
});

test('declared dependencies detect mixed packages without demanding every module equal the Host version', () => {
  const { packages, check } = fixture();
  const name = '@deepseek-ai/dsh-fs';
  packages.get(name)!.version = '0.2.1-rc.5';
  expect(check).toThrow('local writes are disabled');
  packages.get('@deepseek-ai/dsh')!.dependencies![name] = '0.2.1-rc.5';
  expect(check()).toBe('0.2.0-rc.2');
  packages.get('@deepseek-ai/cordis-plugin-loader')!.version = '1.1.1';
  expect(check).toThrow('local writes are disabled');
  packages.get('@deepseek-ai/dsh')!.dependencies!['@deepseek-ai/cordis-plugin-loader'] = '^1.1.0';
  expect(check()).toBe('0.2.0-rc.2');
});

test('Client ownership, versions and active entry paths are checked against actual Host resolution', () => {
  const { services, readManifest, clients, check } = fixture();
  const name = clientPackageBaseline[0];
  clients.set(name, { name, version: '0.2.1-rc.5' }); expect(check).toThrow('local writes are disabled');
  clients.set(name, { name, version: '0.2.0-rc.2' }); expect(check()).toBe('0.2.0-rc.2');
  expect(() => assertRuntimeCompatibility({ ...services, clientPath: () => '/foreign/lib/client.js' }, 'file:///plugin/index.js', (_file, name) => clients.get(name as typeof clientPackageBaseline[number]), readManifest)).toThrow('local writes are disabled');
});

test('current manifests are read again after accepted operations', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-runtime-manifests-'));
  try {
    const { packages, services } = fixture('0.2.0-rc.2', root);
    for (const item of packages.values()) { await mkdir(dirname(item.manifestPath), { recursive: true }); await writeFile(item.manifestPath, JSON.stringify(item)); await mkdir(join(dirname(item.manifestPath), 'lib'), { recursive: true }); await writeFile(join(dirname(item.manifestPath), 'lib/client.js'), ''); }
    const check = () => assertRuntimeCompatibility(services, 'file:///plugin/index.js');
    expect(check()).toBe('0.2.0-rc.2');
    const item = packages.get('@deepseek-ai/dsh-fs')!;
    for (const contents of ['{broken', JSON.stringify({ name: item.name, version: '0.2.1-rc.5' }), JSON.stringify({ name: 'other', version: item.version })]) {
      await writeFile(item.manifestPath, contents); expect(check).toThrow('local writes are disabled');
      await writeFile(item.manifestPath, JSON.stringify(item)); expect(check()).toBe('0.2.0-rc.2');
    }
    await rm(item.manifestPath); expect(check).toThrow('local writes are disabled');
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('Node uses the DSH family floor without a future-major upper bound', async () => {
  const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  expect(manifest.engines.node).toBe(nodeRuntimeRange);
  for (const version of ['24.2.0', '24.9.0', '25.0.0', '26.0.0']) expect(() => assertNodeRuntime(version, '0.2.0-rc.2')).not.toThrow();
  for (const release of ['0.2.2-rc.3', '0.2.2-rc.4', '0.2.3-alpha.1', '1.0.0', '0.2.2-rc.3+desktop.1']) {
    for (const version of ['24.9.0', '25.0.0', '26.0.0', '24.9.0+desktop.1']) expect(() => assertNodeRuntime(version, release)).not.toThrow();
    for (const version of ['24.2.0', '24.8.9', '22.18.0']) expect(() => assertNodeRuntime(version, release)).toThrow('local writes are disabled');
  }
  for (const version of ['24.1.9', '24.2.0-rc.1', '25.0.0-rc.1', '26.0.0-nightly.1', 'v24.9.0', '24.9.0 ', ' 24.9.0', '', 'not-a-version']) expect(() => assertNodeRuntime(version)).toThrow('local writes are disabled');
});
