import { expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import SystemPrompt, { type AssembleContext } from '@deepseek-ai/dsh-system-prompt';
import type { Agent } from '@deepseek-ai/dsh-agent';
import SkillHubService from '@astron-team/skillhub-dsh';
import { installationContext, registerManagementContext } from '../src/host/management-context.js';
import type { InstalledSkillList } from '../src/host/types.js';

const item = { itemKey: 'hidden-item-key', namespace: 'team', slug: 'writer', version: '1.0.0', scope: 'project' as const, state: 'present' as const };
const list: InstalledSkillList = { items: [item], complete: true, unavailable: [], issues: [] };
function scope(): AssembleContext {
  const agent = { session: { id: 'test-session', header: { cwd: '/private/project' } } } as unknown as Agent;
  return { agent, scope: agent };
}
function snapshot(text: string) { return JSON.parse(text.slice(text.lastIndexOf('\n') + 1)); }

test('snapshot exposes bounded installation facts, not routing references, paths or integrity claims', () => {
  const text = installationContext(list);
  expect(snapshot(text)).toEqual({ source: 'currently configured SkillHub registry', complete: true, truncated: false, unavailable: [], totalRecords: 1,
    items: [{ namespace: 'team', name: 'writer', version: '1.0.0', scope: 'project', state: 'present' }] });
  expect(text).not.toContain(item.itemKey); expect(text).not.toContain('/private');
  expect(text).toContain('Presence does not prove content integrity');
  const literal = installationContext({ ...list, items: [{ ...item, slug: '{{private_variable}}' }] });
  expect(literal).not.toContain('{{');
  expect(snapshot(literal).items[0].name).toBe('{{private_variable}}');
  const escaped = installationContext({ ...list, items: Array.from({ length: 40 }, () => ({ ...item, slug: '{{'.repeat(100) })) });
  expect(snapshot(escaped).truncated).toBe(true);
  expect(escaped.length).toBeLessThan(7500);
  expect(snapshot(escaped).items[0].name).toBe('{{'.repeat(100));
  const partial = installationContext({ ...list, complete: false, unavailable: ['user'] });
  expect(snapshot(partial)).toMatchObject({ complete: false, unavailable: ['user'] });
  expect(partial).toContain('missing entries do NOT prove uninstallation');
  const long = installationContext({ ...list, items: Array.from({ length: 100 }, (_, i) => ({ ...item, slug: `skill-${i}` })) });
  expect(snapshot(long).truncated).toBe(true); expect(snapshot(long).items.length).toBeLessThanOrEqual(40); expect(long.length).toBeLessThan(7500);
  const huge = installationContext({ ...list, items: [{ ...item, slug: 'x'.repeat(10000) }] });
  expect(snapshot(huge).truncated).toBe(true); expect(snapshot(huge).items).toEqual([]);
});

test('official async assembly keeps unrelated inputs and rereads facts on each request', async () => {
  const ctx = new Context(); const prompt = ctx.plugin(SystemPrompt); await prompt;
  ctx.systemPrompt.context({ name: 'another-plugin', order: 1, text: 'other context' });
  ctx.systemPrompt.section({ name: 'policy', order: 1, text: 'system policy' });
  let current = list; const reads: string[] = [];
  const stop = registerManagementContext(ctx, async (id, cwd) => { reads.push(`${id}:${cwd}`); return current; });
  const context = scope();
  try {
    const first = await ctx.systemPrompt.assemble(context);
    expect(first.sections.find(section => section.name === 'policy')?.text).toBe('system policy'); expect(first.contexts[0]?.text).toBe('other context');
    expect(snapshot(first.contexts[1]!.text).items[0].version).toBe('1.0.0');
    current = { ...list, items: [] };
    const second = await ctx.systemPrompt.assemble(context);
    expect(snapshot(second.contexts[1]!.text).items).toEqual([]);
    expect(reads).toEqual(['test-session:/private/project', 'test-session:/private/project']);
    expect(first.contexts[1]!.text).toContain('1.0.0');
    expect((await ctx.systemPrompt.assemble(context)).contexts).toEqual(second.contexts); // Prior history was not rewritten.
    expect(ctx.systemPrompt.refreshContext(second, context).contexts).toEqual(second.contexts);
    const unsuppress = ctx.systemPrompt.suppressRuntimeContext();
    expect(ctx.systemPrompt.refreshContext(second, context).contexts).toEqual([]);
    unsuppress();
    expect((await ctx.systemPrompt.assemble({})).contexts).toEqual([{ name: 'another-plugin', text: 'other context' }]);
  } finally { stop(); await prompt.dispose(); }
});

test('source disconnection and read failure contribute explicit unknown state without leaking the error', async () => {
  const ctx = new Context(); const prompt = ctx.plugin(SystemPrompt); await prompt;
  let fail = false;
  const stop = registerManagementContext(ctx, async () => { if (fail) throw new Error('/private/token-secret'); return null; });
  try {
    const disconnected = await ctx.systemPrompt.assemble(scope());
    expect(disconnected.contexts[0]?.text).toContain('no registry is currently connected');
    expect(disconnected.contexts[0]?.text).toContain('unavailable, not empty');
    fail = true;
    const failure = await ctx.systemPrompt.assemble(scope());
    expect(failure.contexts[0]?.text).toContain('state is unknown');
    expect(failure.contexts[0]?.text).not.toContain('token-secret');
  } finally { stop(); await prompt.dispose(); }
});

test('slow reads cannot block chat indefinitely and disposal aborts the waiting context', async () => {
  const ctx = new Context(); const prompt = ctx.plugin(SystemPrompt); await prompt;
  let signal: AbortSignal | undefined;
  const stop = registerManagementContext(ctx, async (_id, _cwd, currentSignal) => { signal = currentSignal; return new Promise(() => {}); });
  try {
    const assembly = await ctx.systemPrompt.assemble(scope());
    expect(signal?.aborted).toBe(true); expect(assembly.contexts[0]?.text).toContain('state is unknown');
    const pending = ctx.systemPrompt.assemble(scope());
    await new Promise(resolve => setTimeout(resolve, 0));
    stop();
    expect((await pending).contexts).toEqual([]); expect(signal?.aborted).toBe(true);
    expect((await ctx.systemPrompt.assemble(scope())).contexts).toEqual([]);
  } finally { stop(); await prompt.dispose(); }
}, 5000);

test('compiled service injects and disposes its official context listener with systemPrompt', async () => {
  const ctx = new Context(); const service = ctx.plugin(SkillHubService, { registryUrl: '' }); await service;
  const context = scope();
  let prompt = ctx.plugin(SystemPrompt); await prompt;
  await new Promise(resolve => setTimeout(resolve, 0));
  try {
    expect((await ctx.systemPrompt.assemble(context)).contexts[0]?.text).toContain('no registry is currently connected');
    await prompt.dispose();
    prompt = ctx.plugin(SystemPrompt); await prompt;
    await new Promise(resolve => setTimeout(resolve, 0));
    expect((await ctx.systemPrompt.assemble(context)).contexts).toHaveLength(1);
    await service.dispose();
    expect((await ctx.systemPrompt.assemble(context)).contexts).toEqual([]);
  } finally { await service.dispose(); await prompt.dispose(); }
});
