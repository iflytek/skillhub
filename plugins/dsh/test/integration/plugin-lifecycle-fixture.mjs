import assert from 'node:assert/strict';
import { realpathSync } from 'node:fs';
import { realpath, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, relative, isAbsolute } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

// Do not inject skillhub: this observer must survive its disposal.
export const inject = ['loader', 'tools', 'clientModules', 'pluginManager'];
export function apply(ctx, config) {
  const root = realpathSync(resolve(config.root));
  const rel = relative(realpathSync(tmpdir()), root);
  assert(rel && !rel.startsWith('..') && !isAbsolute(rel));
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  const events = [];
  if (config.diagnose) {
    for (const global of [false, true]) ctx.on('internal/plugin', fiber => {
      if (fiber.entry?.options.name !== '@astron-team/skillhub-dsh') return;
      events.push({ global, disposed: fiber.uid === null, entryDisabled: fiber.entry.disabled,
        loaderSources: [...ctx.loader.entries()].filter(entry => entry.options.name === '@astron-team/skillhub-dsh')
          .map(entry => ({ disabled: entry.disabled, hasFiber: !!entry.fiber, disposedFiber: entry.fiber?.uid === null })) });
    }, { global });
  }
  const abort = new AbortController();
  const timer = setTimeout(() => {
    void run(ctx, abort.signal, config.bundle === true, events, root, config.referencePackage).then(result => writeFile(join(root, 'plugin-lifecycle-result.json'), JSON.stringify(result, null, 2)),
      error => writeFile(join(root, 'plugin-lifecycle-result.json'), JSON.stringify({ passed: false, error: error.message })));
  }, 1000);
  return () => { clearTimeout(timer); abort.abort(); };
}
async function until(check, signal, label = 'initial') {
  const deadline = Date.now() + 15000;
  while (!check()) {
    assert(Date.now() < deadline, `Plugin lifecycle did not settle: ${label}`);
    await delay(50, undefined, { signal });
  }
}
async function run(ctx, signal, bundle, events, root, referencePackage) {
  await until(() => !!ctx.get('skillhub'), signal);
  const expectedClient = await realpath(join(root, 'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/lib/client.js'));
  assert.equal(await realpath(ctx.clientModules.clientPath('@astron-team/skillhub-dsh')), expectedClient,
    'Client must resolve from the installed package; disable in-package test observers');
  const entries = [...ctx.loader.entries()].filter(entry => entry.options.name === '@astron-team/skillhub-dsh');
  assert.equal(entries.length, 1);
  const entry = entries[0];
  assert.equal(entry.options.id, 'astron-skillhub');
  assert.equal(entry.options.config.registryUrl, 'http://127.0.0.1:43219');
  assert.equal(entry.disabled, false);
  const referenceEntries = referencePackage ? [...ctx.loader.entries()].filter(item => item.options.name === referencePackage) : [];
  if (referencePackage) assert.equal(referenceEntries.length, 1);
  const reference = referenceEntries[0];
  const referenceOptions = reference ? JSON.stringify(reference.options) : null;
  const referenceTools = reference ? ['skillhub_search', 'skillhub_install', 'skillhub_list', 'skillhub_uninstall',
    'skillhub_plugin_search', 'skillhub_plugin_install'].map(name => ({ name, tool: ctx.tools.get(name) })) : [];
  const referenceClient = reference ? ctx.clientModules.clientPath(referencePackage) : null;
  function assertReferenceUnchanged() {
    if (!reference) return;
    assert.equal(reference.options.id, 'skillhub');
    assert.equal(reference.disabled, false);
    assert(reference.fiber, 'Reference Host must remain active');
    assert.equal(JSON.stringify(reference.options), referenceOptions);
    assert.equal([...ctx.loader.entries()].filter(item => item.options.name === referencePackage).length, 1);
    assert(referenceClient, 'Reference Client must be registered');
    assert.equal(ctx.clientModules.clientPath(referencePackage), referenceClient);
    for (const { name, tool } of referenceTools) {
      assert(tool, `Reference tool must be registered: ${name}`);
      assert.equal(ctx.tools.get(name), tool, `Reference tool changed: ${name}`);
    }
  }
  assertReferenceUnchanged();
  const previousDisabled = entry.options.disabled;
  const snapshots = [];
  const tools = ['astron_skillhub_search', 'astron_skillhub_install'];
  for (let cycle = 0; cycle < 2; cycle++) {
    const previous = ctx.get('skillhub');
    const context = previous.getContext();
    assert(tools.every(name => ctx.tools.get(name)));
    try {
      if (bundle) await ctx.pluginManager.setBundleEnabled('@astron-team/skillhub-dsh', false);
      else await entry.update({ disabled: true });
      try {
        await until(() => !ctx.get('skillhub') && tools.every(name => !ctx.tools.get(name)), signal, 'disabled');
      } catch {
        throw new Error(JSON.stringify({ phase: 'disabled', entryDisabled: entry.disabled, hostPresent: !!ctx.get('skillhub'), tools: tools.map(name => ({ name, present: !!ctx.tools.get(name) })) }));
      }
      await assert.rejects(previous.listSkills(context.sourceRevision, '', new AbortController().signal),
        error => error.code === 'skillhub/context-stale');
      await until(() => !ctx.clientModules.clientPath('@astron-team/skillhub-dsh')
        && !ctx.clientModules.graph().entries.some(item => item.id === '@astron-team/skillhub-dsh'), signal, 'client-removed');
      assertReferenceUnchanged();
      snapshots.push({ cycle, disabled: true, hostRemoved: true, toolsRemoved: true, staleServiceRejected: true,
        clientModulePresent: !!ctx.clientModules.clientPath('@astron-team/skillhub-dsh'),
        clientGraphEntryPresent: ctx.clientModules.graph().entries.some(item => item.id === '@astron-team/skillhub-dsh'),
        remainingLoaderSources: [...ctx.loader.entries()].filter(item => item.options.name === '@astron-team/skillhub-dsh')
          .map(item => ({ disabled: item.disabled, hasFiber: !!item.fiber, disposedFiber: item.fiber?.uid === null })) });
    } finally {
      if (bundle) await ctx.pluginManager.setBundleEnabled('@astron-team/skillhub-dsh', true);
      else await entry.update({ disabled: previousDisabled ?? null });
    }
    await until(() => !!ctx.get('skillhub') && tools.every(name => ctx.tools.get(name))
      && !!ctx.clientModules.clientPath('@astron-team/skillhub-dsh'), signal, 'restored');
    assert.equal(await realpath(ctx.clientModules.clientPath('@astron-team/skillhub-dsh')), expectedClient);
    const restored = ctx.get('skillhub');
    assert.notEqual(restored, previous);
    const next = restored.getContext();
    assert.notEqual(next.sourceRevision, context.sourceRevision);
    const list = await restored.listSkills(next.sourceRevision, '', new AbortController().signal);
    assert.equal(list.items.length, 1);
    assert.equal(restored.getCompatibility().dependencyBaseline, 'matched');
    assert.equal([...ctx.loader.entries()].filter(item => item.options.name === '@astron-team/skillhub-dsh').length, 1);
    assertReferenceUnchanged();
    snapshots.push({ cycle, disabled: false, newHostInstance: true, newSourceRevision: true,
      toolsRestored: true, registryReadable: true, dependencyBaseline: 'matched' });
  }
  return { passed: true, loaderId: entry.options.id, referencePackage: referencePackage ?? null, referenceUnchanged: !!reference, mode: bundle ? 'bundle' : 'entry', node: process.versions.node, cycles: 2, installedClientVerified: true, snapshots, events, modelInvoked: false };
}
