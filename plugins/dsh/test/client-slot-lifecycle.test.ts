import { expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { runInNewContext } from 'node:vm';
import * as clientPlugin from '../src/client/index.js';

// Exercise the published slot lifecycle without mounting a React DOM tree.
async function loadSlotRegistry() {
  const require = createRequire(import.meta.url);
  type Registry = Context['slots'] & { entriesOfSlot(name: string): { component: unknown }[] };
  type Entry = { factory: (require: (id: string) => unknown) => { SlotRegistry: new (ctx: Context) => Registry } };
  const entries: Entry[] = [];
  const manifest = require.resolve('@deepseek-ai/dsh-client-ui-renderer/package.json');
  runInNewContext(await readFile(join(dirname(manifest), 'lib/client.js'), 'utf8'), {
    window: { __ModuleLoader__: { load: (entry: Entry) => entries.push(entry) } },
    queueMicrotask,
  });
  expect(entries).toHaveLength(1);
  return entries[0]!.factory(id => {
    // DOM rendering is deliberately outside this service-lifecycle test.
    if (id === 'react-dom' || id === 'react-dom/client') return {};
    return require(id);
  }).SlotRegistry;
}

test('official slot declaration loss preserves catalog and independently removes tool/config contributions', async () => {
  const SlotRegistry = await loadSlotRegistry();
  const ctx = new Context();
  const renderer = ctx.plugin({ apply: (ctx: Context) => { new SlotRegistry(ctx); } });
  await renderer;
  const slots = ctx.slots as InstanceType<typeof SlotRegistry>;
  const empty = () => null;
  const root = slots.register({ name: 'root', children: {
    main: { kind: 'keyed', scope: 'session-maybe' },
    'sidebar.panellist': { kind: 'list', scope: 'root' },
    'test.tools': { kind: 'single', scope: 'root' },
    'test.config': { kind: 'single', scope: 'root' },
  } } as never, empty);
  const providers = [
    ctx.provide('remote', { $mount: async () => async () => {} } as never),
    ctx.provide('remote.skillhub', {} as never),
    ctx.provide('sessions', { scope: () => undefined } as never),
    ctx.provide('layout', { selectPanel: () => {} } as never),
  ];
  providers.push(ctx.provide('uiSession', {} as never));
  const plugin = ctx.plugin(clientPlugin);
  const flush = async () => { await new Promise(resolve => setTimeout(resolve, 25)); };
  try {
    await plugin; await flush();
    const catalog = slots.entriesOfSlot('main')[0]?.component;
    expect(catalog).toBeDefined();
    expect(slots.entriesOfSlot('sidebar.panellist')).toHaveLength(1);
    expect(slots.entriesOfSlot('tool.call.toolview')).toHaveLength(0);
    expect(slots.entriesOfSlot('plugins.bundle.config')).toHaveLength(0);
    for (let cycle = 0; cycle < 2; cycle++) {
      const tools = slots.register({ name: 'test.tools', children: {
        'tool.call.toolview': { kind: 'keyed', scope: 'session' },
      } } as never, empty);
      expect(slots.entriesOfSlot('tool.call.toolview')).toHaveLength(2);
      tools(); await flush();
      expect(slots.entriesOfSlot('tool.call.toolview')).toHaveLength(0);
      expect(slots.entriesOfSlot('main')[0]?.component).toBe(catalog);
      const config = slots.register({ name: 'test.config', children: {
        'plugins.bundle.config': { kind: 'keyed', scope: 'root' },
      } } as never, empty);
      expect(slots.entriesOfSlot('plugins.bundle.config')).toHaveLength(0);
      const forms = ctx.provide('configForms', { get: () => ({}) } as never);
      await flush();
      expect(slots.entriesOfSlot('plugins.bundle.config')).toHaveLength(1);
      await forms(); await flush();
      expect(slots.entriesOfSlot('plugins.bundle.config')).toHaveLength(0);
      expect(slots.entriesOfSlot('main')[0]?.component).toBe(catalog);
      config();
    }
    await plugin.dispose();
    expect(slots.entriesOfSlot('main')).toHaveLength(0);
    expect(slots.entriesOfSlot('sidebar.panellist')).toHaveLength(0);
    // Disposing the plugin must also cancel waits for declarations not yet present.
    const toolsAfterDispose = slots.register({ name: 'test.tools', children: {
      'tool.call.toolview': { kind: 'keyed', scope: 'session' },
    } } as never, empty);
    expect(slots.entriesOfSlot('tool.call.toolview')).toHaveLength(0);
    toolsAfterDispose();
  } finally {
    await plugin.dispose();
    for (const remove of providers.reverse()) await remove();
    root(); await renderer.dispose();
  }
});

test('session adapter loss removes scoped catalog contributions and restoration registers them once', async () => {
  const SlotRegistry = await loadSlotRegistry();
  const ctx = new Context();
  const renderer = ctx.plugin({ apply: (ctx: Context) => { new SlotRegistry(ctx); } });
  await renderer;
  const slots = ctx.slots as InstanceType<typeof SlotRegistry>;
  const root = slots.register({ name: 'root', children: {
    main: { kind: 'keyed', scope: 'session-maybe' },
    'sidebar.panellist': { kind: 'list', scope: 'root' },
  } } as never, () => null);
  const providers = [
    ctx.provide('remote', { $mount: async () => async () => {} } as never),
    ctx.provide('remote.skillhub', {} as never),
    ctx.provide('sessions', { scope: () => undefined } as never),
    ctx.provide('layout', { selectPanel: () => {} } as never),
  ];
  const plugin = ctx.plugin(clientPlugin);
  const flush = async () => { await new Promise(resolve => setTimeout(resolve, 25)); };
  const source = { getSnapshot: () => ({ hooks: {}, keyedHooks: {}, props: {} }), subscribe: () => () => {} };
  const adapter = { current: source, bindingSource: () => source };
  let owner: ReturnType<Context['plugin']> | undefined;
  try {
    await plugin; await flush();
    // sessions is available, but the session-maybe renderer cannot run without its UI adapter.
    expect(slots.entriesOfSlot('main')).toHaveLength(0);
    expect(slots.entriesOfSlot('sidebar.panellist')).toHaveLength(0);
    for (let cycle = 0; cycle < 2; cycle++) {
      owner = ctx.plugin({ inject: ['slots'], apply: (ctx: Context) => {
        ctx.provide('uiSession', {} as never);
        ctx.slots.installScope('session', adapter);
      } });
      await owner; await flush();
      expect(slots.entriesOfSlot('main')).toHaveLength(1);
      expect(slots.entriesOfSlot('sidebar.panellist')).toHaveLength(1);
      await owner.dispose(); await flush();
      expect(slots.entriesOfSlot('main')).toHaveLength(0);
      expect(slots.entriesOfSlot('sidebar.panellist')).toHaveLength(0);
    }
  } finally {
    await plugin.dispose(); await owner?.dispose();
    for (const remove of providers.reverse()) await remove();
    root(); await renderer.dispose();
  }
});
