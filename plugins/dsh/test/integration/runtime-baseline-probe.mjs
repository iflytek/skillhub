// Temporary Host observer; never included in the published plugin.
// Copy outside the package before loading: DSH associates local modules with their owning manifest.
import assert from 'node:assert/strict';
import { realpathSync } from 'node:fs';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { tmpdir } from 'node:os';
import { dirname, isAbsolute, join, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const inject = ['loader', 'pluginPackages', 'clientModules', 'skillhub'];

export function apply(ctx, config) {
  const root = realpathSync(resolve(config.root));
  const rel = relative(realpathSync(tmpdir()), root);
  assert(rel && !rel.startsWith('..') && !isAbsolute(rel));
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  const timer = setTimeout(() => {
    run(ctx, config).then(
      result => writeFile(join(root, config.output), JSON.stringify(result, null, 2)),
      error => writeFile(join(root, config.output), JSON.stringify({ passed: false, error: error.message })),
    ).catch(() => undefined);
  }, 1000);
  return () => clearTimeout(timer);
}

async function ownerOf(file, name) {
  let directory = dirname(file);
  while (directory) {
    try {
      const manifest = JSON.parse(await readFile(join(directory, 'package.json'), 'utf8'));
      if (manifest.name === name) return { name, version: manifest.version };
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    const parent = dirname(directory);
    if (parent === directory) break;
    directory = parent;
  }
  throw new Error(`No owning manifest for ${name}`);
}

async function inspect(ctx, config) {
  const parentURL = pathToFileURL(config.pluginEntry).href;
  const host = config.hostPackages.map(name => {
    const pkg = ctx.pluginPackages.packageOf(name, parentURL);
    return { name, resolved: !!pkg, version: pkg?.version ?? null };
  });
  const client = await Promise.all(config.clientPackages.map(async name => {
    const file = ctx.clientModules.clientPath(name);
    return file ? { ...await ownerOf(file, name), active: true } : { name, active: false };
  }));
  const pluginCompatibility = ctx.skillhub.getCompatibility();
  let rejectedBeforePreparation;
  if (config.expectBlocked === true) {
    assert.equal(pluginCompatibility.localWritesAllowed, false, 'Unverified runtime must reject writes');
    const runtime = Reflect.get(ctx.skillhub, 'localRuntime');
    assert.equal(runtime?.getStatus().state, 'ready');
    const journal = join(config.root, 'home/skillhub/operations');
    const before = (await readdir(journal)).filter(name => name.endsWith('.json')).sort();
    let prepared = false;
    await assert.rejects(runtime.submitInstall({
      hostInstanceId: runtime.getStatus().hostInstanceId,
      ownerId: 'runtime-baseline-probe', requestKey: randomUUID(), mode: 'new', parameters: { probe: true },
    }, async () => { prepared = true; throw new Error('Unexpected preparation'); }), /runtime baseline could not be verified/);
    assert.equal(prepared, false);
    assert.deepEqual((await readdir(journal)).filter(name => name.endsWith('.json')).sort(), before);
    rejectedBeforePreparation = true;
  }
  return { passed: true, node: process.versions.node, host, client, pluginCompatibility, rejectedBeforePreparation };
}

async function waitFor(check, message = 'Client graph did not reach expected state') {
  const deadline = Date.now() + 10000;
  while (!check()) {
    assert(Date.now() < deadline, message);
    await new Promise(resolve => setTimeout(resolve, 25));
  }
}

async function run(ctx, config) {
  // Disk-backed recovery initializes independently of the plugin registration.
  // Observe readiness before testing reload behavior, rather than assuming 1s is enough.
  await waitFor(() => {
    const state = Reflect.get(ctx.skillhub, 'localRuntime')?.getStatus().state;
    assert(!['failed', 'closing', 'disposed'].includes(state), `Local runtime entered ${state} before probe`);
    return state === 'ready';
  }, 'Local runtime did not become ready before probe');
  if (!config.reloadClient) return inspect(ctx, config);
  assert.equal(config.reloadClient, '@deepseek-ai/dsh-client-ui-layout');
  const matches = [...ctx.loader.entries()].filter(entry => entry.options.name === config.reloadClient);
  assert.equal(matches.length, 1);
  const entry = matches[0];
  assert.equal(entry.disabled, false, 'Layout entry must initially be enabled');
  const disabled = entry.options.disabled;
  const initial = await inspect(ctx, config);
  assert.equal(initial.pluginCompatibility.localWritesAllowed, true, 'Initial runtime must match baseline');
  let blocked;
  try {
    await entry.update({ disabled: true });
    await waitFor(() => !ctx.clientModules.clientPath(config.reloadClient));
    blocked = await inspect(ctx, { ...config, expectBlocked: true });
  } finally {
    await entry.update({ disabled: disabled ?? null });
  }
  await waitFor(() => !!ctx.clientModules.clientPath(config.reloadClient));
  const restored = await inspect(ctx, config);
  assert.equal(restored.pluginCompatibility.localWritesAllowed, true, 'Restored layout must allow baseline writes');
  return { passed: true, sameProcess: true, initial, blocked, restored };
}
