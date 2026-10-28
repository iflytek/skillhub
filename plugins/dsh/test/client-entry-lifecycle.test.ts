import { expect, test } from 'bun:test';
import { Context, Service } from '@deepseek-ai/cordis';
import * as clientPlugin from '../src/client/index.js';

test('layout loss removes only catalog entries and preserves conversation registrations', async () => {
  const ctx = new Context();
  const entries = new Map<string, unknown>();
  class Slots extends Service {
    constructor(ctx: Context) { super(ctx, 'slots'); }
    inject(_name: string, register: () => unknown) { return register(); }
    register(spec: { name: string; key?: string; id?: string }, component: unknown) {
      const key = `${spec.name}/${spec.key ?? spec.id ?? ''}`;
      if (entries.has(key)) throw new Error(`Duplicate slot ${key}`);
      entries.set(key, component);
      return this.ctx.effect(() => () => { entries.delete(key); });
    }
  }
  const slots = ctx.plugin(Slots);
  await slots;
  const remote = { $mount: async () => async () => {}, skillhub: {} };
  const removeRemote = ctx.provide('remote', remote as never);
  const removeSkillHub = ctx.provide('remote.skillhub', {} as never);
  const removeSessions = ctx.provide('sessions', { scope: () => undefined } as never);
  const removeUiSession = ctx.provide('uiSession', {} as never);
  const plugin = ctx.plugin(clientPlugin);
  const flush = async () => { await new Promise(resolve => setTimeout(resolve, 25)); };
  try {
    await plugin; await flush();
    const tools = ['conversation.input.dock/skillhub-case-choices', 'conversation.input.left/skillhub-use-focus', 'tool.call.toolview/astron_skillhub_check_availability', 'tool.call.toolview/astron_skillhub_check_update', 'tool.call.toolview/astron_skillhub_disable', 'tool.call.toolview/astron_skillhub_enable', 'tool.call.toolview/astron_skillhub_install', 'tool.call.toolview/astron_skillhub_list_installed', 'tool.call.toolview/astron_skillhub_operation', 'tool.call.toolview/astron_skillhub_search', 'tool.call.toolview/astron_skillhub_uninstall', 'tool.call.toolview/astron_skillhub_upgrade'];
    expect([...entries.keys()].sort()).toEqual(tools);
    const original = tools.map(key => entries.get(key));
    for (let cycle = 0; cycle < 2; cycle++) {
      const removeLayout = ctx.provide('layout', { selectPanel: () => {} } as never);
      await flush();
      expect(entries.has('main/skillhub')).toBe(true);
      expect(entries.has('sidebar.panellist/skillhub')).toBe(true);
      await removeLayout(); await flush();
      expect([...entries.keys()].sort()).toEqual(tools);
      expect(tools.map(key => entries.get(key))).toEqual(original);
    }
    await plugin.dispose();
    expect(entries.size).toBe(0);
    const restored = ctx.plugin(clientPlugin);
    await restored; await flush();
    expect([...entries.keys()].sort()).toEqual(tools);
    await restored.dispose(); expect(entries.size).toBe(0);
  } finally {
    await plugin.dispose(); await removeUiSession(); await removeSessions(); await removeSkillHub(); await removeRemote(); await slots.dispose();
  }
});
