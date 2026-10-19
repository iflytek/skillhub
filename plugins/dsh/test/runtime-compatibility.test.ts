import { expect, test } from 'bun:test';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { assertNodeRuntime, nodeRuntimeRange, assertRuntimeCompatibility, clientPackageBaseline, runtimePackageBaseline } from '../src/host/runtime-compatibility.js';

function fixture() {
  const packages = new Map<string, { name: string; version: string; manifestPath: string }>(Object.entries(runtimePackageBaseline).map(([name, version]) => [name, { name, version, manifestPath: name }]));
  const clients = new Map<string, unknown>(clientPackageBaseline.map(name => [name, { name, version: '0.2.0-rc.2' }]));
  const services = {
    packageOf: (name: string, parentURL: string) => {
      expect(parentURL.startsWith('file:')).toBe(true);
      return packages.get(name);
    },
    clientPath: (name: string) => clients.has(name) ? `/client/${name}/lib/client.js` : undefined,
  };
  const check = () => assertRuntimeCompatibility(services, 'file:///plugin/lib/index.js', (_file, name) => clients.get(name), file => packages.get(file));
  return { packages, clients, services, check };
}

test('runtime baseline covers every declared Client injection', async () => {
  const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  expect(clientPackageBaseline).toEqual(manifest.dsh.client.inject);
  expect(fixture().check).not.toThrow();
});

test('each missing or mixed runtime package blocks writes; replacing resolution is rechecked', () => {
  for (const name of Object.keys(runtimePackageBaseline)) {
    const { packages, check } = fixture();
    const original = packages.get(name)!;
    packages.delete(name);
    expect(check).toThrow('local writes are disabled');
    packages.set(name, { name, version: '0.2.1', manifestPath: name });
    expect(check).toThrow('local writes are disabled');
    packages.set(name, original);
    expect(check).not.toThrow();
  }
});

test('each Client must be active with its exact owning manifest, including after graph changes', () => {
  for (const name of clientPackageBaseline) {
    const { clients, check } = fixture();
    clients.delete(name);
    expect(check).toThrow('local writes are disabled');
    for (const bad of [null, {}, { name, version: '0.2.1', manifestPath: name }, { name: 'other', version: '0.2.0-rc.2' }]) {
      clients.set(name, bad);
      expect(check).toThrow('local writes are disabled');
    }
    clients.set(name, { name, version: '0.2.0-rc.2' });
    expect(check).not.toThrow();
  }
});

test('loader and filesystem diagnostics are not exposed to Client', () => {
  const { services, packages } = fixture();
  expect(() => assertRuntimeCompatibility(services, 'file:///plugin/lib/index.js', () => {
    throw new Error('/private/runtime/path');
  }, file => packages.get(file))).toThrow('Host runtime baseline could not be verified; local writes are disabled.');
  expect(() => assertRuntimeCompatibility({ ...services, packageOf: () => undefined }, 'file:///plugin/lib/index.js'))
    .toThrow('local writes are disabled');
});

test('real Client manifests fail closed on unreadable JSON or absent ownership and recover after repair', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-client-baseline-'));
  try {
    const { services } = fixture();
    for (const name of clientPackageBaseline) {
      await mkdir(join(root, name, 'lib'), { recursive: true });
      await writeFile(join(root, name, 'package.json'), JSON.stringify({ name, version: '0.2.0-rc.2' }));
    }
    const runtime = { ...services, clientPath: (name: string) => join(root, name, 'lib/client.js') };
    const check = () => assertRuntimeCompatibility(runtime, 'file:///plugin/lib/index.js', undefined, file => ({ name: file, version: runtimePackageBaseline[file as keyof typeof runtimePackageBaseline] }));
    expect(check).not.toThrow();
    const name = clientPackageBaseline[0];
    const manifest = join(root, name, 'package.json');
    await writeFile(manifest, '{broken');
    expect(check).toThrow('local writes are disabled');
    await writeFile(manifest, JSON.stringify({ name: 'unrelated', version: '0.2.0-rc.2' }));
    expect(check).toThrow('local writes are disabled');
    await rm(manifest);
    expect(check).toThrow('local writes are disabled');
    await writeFile(manifest, JSON.stringify({ name, version: '0.2.0-rc.2' }));
    expect(check).not.toThrow();
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('Node candidate range rejects old, future-major and malformed runtimes', async () => {
  const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
  expect(manifest.engines.node).toBe(nodeRuntimeRange);
  for (const version of ['24.2.0', '24.2.1', '24.17.0']) expect(() => assertNodeRuntime(version)).not.toThrow();
  for (const version of ['22.18.0', '23.0.0', '24.0.0', '24.1.9', '25.0.0', '26.0.0',
    '24.2.0-rc.1', '24.2', 'v24.2.0', '24.02.0', '', 'not-a-version']) {
    expect(() => assertNodeRuntime(version)).toThrow('local writes are disabled');
  }
});

test('cached Host metadata cannot hide changed, malformed or removed manifests', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-host-baseline-'));
  try {
    const { packages, clients, services } = fixture();
    for (const [name, value] of packages) {
      const directory = join(root, name);
      await mkdir(directory, { recursive: true });
      value.manifestPath = join(directory, 'package.json');
      await writeFile(value.manifestPath, JSON.stringify(value));
    }
    const check = () => assertRuntimeCompatibility(services, 'file:///plugin/lib/index.js', (_file, name) => clients.get(name));
    expect(check).not.toThrow();
    for (const [name, cached] of packages) {
      for (const content of [JSON.stringify({ name, version: '0.2.1' }), '{broken', JSON.stringify({ name: 'other', version: cached.version })]) {
        await writeFile(cached.manifestPath, content);
        expect(check).toThrow('local writes are disabled');
      }
      await rm(cached.manifestPath);
      expect(check).toThrow('local writes are disabled');
      await writeFile(cached.manifestPath, JSON.stringify(cached));
      expect(check).not.toThrow();
    }
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('only complete validated Cordis families are accepted and unknown or mixed pairs block writes', () => {
  const { packages, check } = fixture();
  packages.get('@deepseek-ai/cordis')!.version = '4.0.5';
  expect(check).toThrow('local writes are disabled');
  packages.get('@deepseek-ai/cordis-plugin-loader')!.version = '1.0.6';
  expect(check).toThrow('local writes are disabled');
  packages.get('@deepseek-ai/cordis-plugin-include')!.version = '1.0.10';
  expect(check).toThrow('local writes are disabled');
  packages.get('@deepseek-ai/cordis-plugin-timer')!.version = '1.1.7';
  expect(check).not.toThrow();
  packages.get('@deepseek-ai/cordis-plugin-loader')!.version = '1.0.7';
  expect(check).toThrow('local writes are disabled');
});

test('plugin peer copies cannot mask mismatched or absent Host dependency resolutions', () => {
  const { packages, clients } = fixture();
  for (const target of ['@deepseek-ai/cordis', '@deepseek-ai/cordis-plugin-loader', '@deepseek-ai/dsh-session']) {
    for (const anchor of ['@deepseek-ai/dsh', '@deepseek-ai/dsh-app-boot']) {
      for (const invalid of [undefined, { ...packages.get(target)!, version: '0.0.0-unsupported' }]) {
        const services = {
          packageOf: (name: string, parentURL: string) => name === target && parentURL.endsWith(anchor)
            ? invalid : packages.get(name),
          clientPath: (name: string) => `/client/${name}/lib/client.js`,
        };
        expect(() => assertRuntimeCompatibility(services, 'file:///plugin/lib/index.js',
          (_file, name) => clients.get(name), file => packages.get(file))).toThrow('local writes are disabled');
      }
    }
  }
});
