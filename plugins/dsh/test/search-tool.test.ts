import { describe, expect, test } from 'bun:test';
import type { ToolDefinition, ToolRunContext } from '@deepseek-ai/dsh-tools';
import { registerSearchTool, SEARCH_TOOL_NAME } from '../src/host/search-tool.ts';

describe('conversation search registration', () => {
  test('official execution validates arguments and forwards only trusted invocation context', async () => {
    let tool: ToolDefinition | undefined;
    const calls: unknown[][] = [];
    const saved = { schemaVersion: 1 as const, kind: 'skillhub-search' as const, query: 'pdf',
      sourceRevision: 'source', sourceLabel: 'Registry', createdAt: '2026-10-15T00:00:00Z',
      total: 0, returned: 0, limit: 20, items: [] };
    registerSearchTool({ get: () => undefined, register: value => { tool = value; return () => {}; } },
      async (...args) => { calls.push(args); return saved; });
    const context = { agent: { session: { id: 'owner', header: { cwd: '/project' } } },
      callId: 'call', signal: new AbortController().signal } as ToolRunContext;
    for (const input of [null, {}, { q: 1 }, { q: 'x'.repeat(1001) }, { q: 'pdf', path: '/private' },
      { q: 'pdf', sessionId: 'other' }, { q: 'pdf', callId: 'other' }]) {
      await expect(tool!.execute(input, context)).rejects.toThrow();
    }
    for (const invalid of [{ ...context, agent: undefined }, { ...context, callId: '' }]) {
      await expect(Reflect.apply(tool!.execute, tool, [{ q: 'pdf' }, invalid])).rejects.toThrow('trusted conversation');
    }
    expect(calls).toHaveLength(0);
    expect(await tool!.execute({ q: '  pdf  ' }, context)).toEqual(saved);
    expect(calls).toEqual([['pdf', { sessionId: 'owner', cwd: '/project', callId: 'call' }, context.signal]]);
  });

  test('preserves an occupied name and returns the registry disposer', () => {
    const entries = new Map<string, ToolDefinition>();
    const tools = {
      get: (name: string) => entries.get(name),
      register: (tool: ToolDefinition) => { entries.set(tool.name, tool); return () => { entries.delete(tool.name); }; },
    };
    const search = async () => { throw new Error('registration must not search'); };
    const remove = registerSearchTool(tools, search);
    const original = entries.get(SEARCH_TOOL_NAME);
    expect(original).toBeDefined();
    expect(() => registerSearchTool(tools, search)).toThrow('already registered');
    expect(entries.get(SEARCH_TOOL_NAME)).toBe(original);
    remove();
    expect(entries.size).toBe(0);
  });

  test('history presentation is a pure projection of the saved result', () => {
    let tool: ToolDefinition | undefined;
    registerSearchTool({ get: () => undefined, register: value => { tool = value; return () => {}; } },
      async () => { throw new Error('rendering must not search'); });
    const saved = { schemaVersion:1, kind:'skillhub-search', query:'pdf',sourceRevision:'old-source',
      sourceLabel:'https://registry.example',createdAt:'2026-10-13T00:00:00Z',total:0,returned:0,limit:20,items:[] };
    expect(tool!.output.presentationMeta!({q:'different'}, saved)).toEqual(saved);
    const rendered = tool!.output.render({ q: 'different' }, saved);
    expect(rendered).toHaveLength(1);
    const block = rendered[0]!;
    if (block.type !== 'text') throw new Error('Expected a text tool result');
    expect(JSON.parse(block.text.split('\n')[0]!)).toEqual(saved);
    expect(block.text).toContain('no skill was installed or executed');
    expect(block.text).toContain('do not choose a candidate or call install until clarified');
    expect(block.text).toContain('do not display them or ask the user to copy them');
  });
});
