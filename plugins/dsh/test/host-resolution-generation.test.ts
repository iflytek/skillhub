import { expect, test } from 'bun:test';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

test('official Node resolution rejects existing mapping replacement and preserves loaded modules', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-resolution-generation-'));
  try {
    const child = Bun.spawn(['node', '--input-type=module', '-e', `
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { Context } from '@deepseek-ai/cordis';
import { PluginPackages } from '@deepseek-ai/dsh-app-boot';
const root = process.env.DSH_TEST_ROOT;
const profilesDir = join(root, 'profiles');
const profileDir = join(profilesDir, 'test');
await mkdir(profileDir, { recursive: true });
async function entry(name, generation) {
  const packageDir = join(root, generation, 'node_modules', name);
  await mkdir(packageDir, { recursive: true });
  await writeFile(join(packageDir, 'package.json'), JSON.stringify({ name, version: '1.0.0', main: 'index.cjs' }));
  await writeFile(join(packageDir, 'index.cjs'), 'module.exports = { generation: ' + JSON.stringify(generation) + ' };');
  return { name, packageDir, version: '1.0.0', declarer: join(root, generation, 'package.json'), scope: 'installation' };
}
const original = await entry('skillhub-fixture-runtime', 'original');
const replacement = await entry(original.name, 'replacement');
const additional = await entry('skillhub-fixture-extra', 'additional');
const resolution = { profilesDir, profileDir, localPackageNames: [], linkedRoots: [], entries: [original] };
const ctx = new Context();
const fiber = ctx.plugin(PluginPackages, { resolution });
try {
  await fiber;
  const parent = pathToFileURL(join(profileDir, 'entry.js')).href;
  const require = createRequire(parent);
  const importerPath = join(profileDir, 'importer.mjs');
  await writeFile(importerPath, 'export const load = specifier => import(specifier);');
  const importer = await import(pathToFileURL(importerPath).href);
  const esmLoaded = await importer.load(original.name);
  assert.equal(esmLoaded.default.generation, 'original');
  const loaded = require(original.name);
  assert.equal(loaded.generation, 'original');
  const metadata = ctx.pluginPackages.packageOf(original.name, parent);
  assert.equal(metadata.dir, original.packageDir);
  assert.throws(() => ctx.pluginPackages.replace({ ...resolution, entries: [replacement] }), /profile resolution/);
  assert.equal(ctx.pluginPackages.packageOf(original.name, parent), metadata);
  assert.equal(require(original.name), loaded);
  assert.equal(await importer.load(original.name), esmLoaded);
  assert.throws(() => ctx.pluginPackages.replace({ ...resolution, entries: [] }), /profile resolution/);
  ctx.pluginPackages.replace({ ...resolution, entries: [original, additional] });
  assert.notEqual(ctx.pluginPackages.packageOf(original.name, parent), metadata);
  assert.equal(ctx.pluginPackages.packageOf(original.name, parent).dir, original.packageDir);
  assert.equal(require(original.name), loaded);
  assert.equal(await importer.load(original.name), esmLoaded);
  assert.equal(require(additional.name).generation, 'additional');
  assert.equal((await importer.load(additional.name)).default.generation, 'additional');
  console.log('resolution generation verified');
} finally { await fiber.dispose(); }
`], { cwd: fileURLToPath(new URL('..', import.meta.url)), env: { ...process.env, DSH_TEST_ROOT: root }, stdout: 'pipe', stderr: 'pipe' });
    const stderr = await new Response(child.stderr).text();
    expect(await child.exited, stderr).toBe(0);
    expect(await new Response(child.stdout).text()).toBe('resolution generation verified\n');
  } finally { await rm(root, { recursive: true, force: true }); }
}, 20_000);
