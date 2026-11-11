import { expect, test } from 'bun:test';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hostDependencyBaseline } from '../src/host/compatibility.js';

test('Node package resolution rejects mixed, missing, corrupt and inaccessible Host manifests', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-dsh-compatibility-'));
  try {
    const built = await Bun.build({
      entrypoints: [fileURLToPath(new URL('../src/host/compatibility.ts', import.meta.url))],
      target: 'node', format: 'esm', packages: 'external',
    });
    expect(built.success).toBe(true);
    await writeFile(join(root, 'compatibility.mjs'), await built.outputs[0]!.text());
    await writeFile(join(root, 'probe.mjs'), `
import assert from 'node:assert/strict';
import { assertHostDependencies } from './compatibility.mjs';
if (process.argv[2] === 'matched') {
  assertHostDependencies();
} else {
  assert.throws(() => assertHostDependencies(), error => {
    assert.equal(error.message, process.argv[2]);
    assert.equal(error.message.includes(process.cwd()), false);
    return true;
  });
}
console.log('verified');
`);
    const manifestPath = (name: string) => join(root, 'node_modules', name, 'package.json');
    for (const [name, version] of Object.entries(hostDependencyBaseline)) {
      await mkdir(dirname(manifestPath(name)), { recursive: true });
      await writeFile(manifestPath(name), JSON.stringify({ name, version }));
    }
    const probe = async (expected: string) => {
      // A fresh process prevents require's manifest cache from hiding an upgrade or corruption.
      const child = Bun.spawn(['node', 'probe.mjs', expected], {
        cwd: root, stdout: 'pipe', stderr: 'pipe', env: { ...process.env, NODE_PATH: '' },
      });
      const [stdout, stderr, exitCode] = await Promise.all([
        new Response(child.stdout).text(), new Response(child.stderr).text(), child.exited,
      ]);
      expect(exitCode, stderr).toBe(0);
      expect(stdout).toBe('verified\n');
    };
    await probe('matched');
    for (const [name, version] of Object.entries(hostDependencyBaseline)) {
      const path = manifestPath(name);
      await writeFile(path, JSON.stringify({ name, version: `${version}-mixed` }));
      await probe('Host dependency baseline does not match; local writes are disabled.');
      await writeFile(path, '{broken');
      await probe('Host dependency baseline could not be verified; local writes are disabled.');
      await writeFile(path, JSON.stringify({ name, version, exports: { '.': './index.js' } }));
      await probe('Host dependency baseline could not be verified; local writes are disabled.');
      await rm(path);
      await probe('Host dependency baseline could not be verified; local writes are disabled.');
      await writeFile(path, JSON.stringify({ name, version }));
    }
    await probe('matched');
    for (const name of Object.keys(hostDependencyBaseline)) {
      await writeFile(manifestPath(name), JSON.stringify({ name, version: name === '@deepseek-ai/cordis' ? '4.0.5' : '0.2.1-rc.5' }));
    }
    await probe('matched');
    await writeFile(manifestPath('@deepseek-ai/cordis'), JSON.stringify({ name: '@deepseek-ai/cordis', version: '4.0.4' }));
    await probe('Host dependency baseline does not match; local writes are disabled.');
    await writeFile(manifestPath('@deepseek-ai/cordis'), JSON.stringify({ name: '@deepseek-ai/cordis', version: '4.0.5' }));
    for (const name of Object.keys(hostDependencyBaseline).filter(name => name !== '@deepseek-ai/cordis')) {
      await writeFile(manifestPath(name), JSON.stringify({ name, version: '0.2.1-rc.2' }));
      await probe('Host dependency baseline does not match; local writes are disabled.');
      await writeFile(manifestPath(name), JSON.stringify({ name, version: '0.2.1-rc.5' }));
    }
    await probe('matched');
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}, 30_000);

test('real Node validates the new complete runtime graph and rejects cross-release or partial families', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-runtime-compatibility-'));
  try {
    const built = await Bun.build({ entrypoints: [fileURLToPath(new URL('../src/host/runtime-compatibility.ts', import.meta.url))], target: 'node', format: 'esm', packages: 'external' });
    expect(built.success).toBe(true);
    await writeFile(join(root, 'runtime.mjs'), await built.outputs[0]!.text());
    await writeFile(join(root, 'probe.mjs'), `
import assert from 'node:assert/strict';
import { assertRuntimeCompatibility, runtimePackageBaseline, clientPackageBaseline } from './runtime.mjs';
const versions = ['0.2.0-rc.2', '0.2.1-rc.2', '0.2.1-rc.5', '0.2.2-rc.1'];
const family = { '@deepseek-ai/cordis': '4.1.0', '@deepseek-ai/cordis-plugin-loader': '1.1.0', '@deepseek-ai/cordis-plugin-include': '1.1.0', '@deepseek-ai/cordis-plugin-timer': '1.2.0' };
const packages = new Map(Object.entries(runtimePackageBaseline).map(([name, value]) => [name, { name, version: name.startsWith('@deepseek-ai/dsh') ? '0.2.2-rc.1' : family[name] ?? value, manifestPath: name }]));
const clients = new Map(clientPackageBaseline.map(name => [name, { name, version: '0.2.2-rc.1' }]));
const check = () => assertRuntimeCompatibility({ packageOf: name => packages.get(name), clientPath: name => clients.has(name) ? name : undefined }, 'file:///plugin/index.js', (_file, name) => clients.get(name), file => packages.get(file));
if (Number(process.versions.node.split('.')[1]) < 9) {
  assert.throws(check, /local writes are disabled/);
} else {
  assert.equal(check(), '0.2.2-rc.1');
  for (const value of packages.values()) {
    const original = value.version;
    for (const version of value.name.startsWith('@deepseek-ai/dsh') ? versions.slice(0, 3) : ['0.0.0-mixed']) {
      value.version = version; assert.throws(check, /local writes are disabled/); value.version = original;
      assert.equal(check(), '0.2.2-rc.1');
    }
  }
  for (const name of clients.keys()) {
    for (const version of versions.slice(0, 3)) {
      clients.set(name, { name, version }); assert.throws(check, /local writes are disabled/);
      clients.set(name, { name, version: '0.2.2-rc.1' });
    }
  }
}
console.log('verified on Node ' + process.versions.node);
`);
    const child = Bun.spawn(['node', 'probe.mjs'], { cwd: root, stdout: 'pipe', stderr: 'pipe' });
    const [stdout, stderr, code] = await Promise.all([new Response(child.stdout).text(), new Response(child.stderr).text(), child.exited]);
    expect(code, stderr).toBe(0);
    expect(stdout).toContain('verified on Node ');
  } finally { await rm(root, { recursive: true, force: true }); }
});
