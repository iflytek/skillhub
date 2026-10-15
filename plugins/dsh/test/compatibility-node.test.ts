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
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}, 30_000);
