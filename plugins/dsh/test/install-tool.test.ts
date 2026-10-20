import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import type { ToolDefinition, ToolRunContext } from '@deepseek-ai/dsh-tools';
import type { NativeOperation } from '../src/host/types.js';
import { INSTALL_TOOL_NAME, registerInstallTool } from '../src/host/install-tool.js';

function registry() {
  const entries = new Map<string, ToolDefinition>();
  return { entries, get: (name: string) => entries.get(name), register: (tool: ToolDefinition) => {
    entries.set(tool.name, tool); return () => { entries.delete(tool.name); };
  } };
}
const context = { agent: { session: { id: 'owner', header: { cwd: '/project' } } },
  callId: 'call', signal: new AbortController().signal } as ToolRunContext;

test('install tool protects occupied registration and validates before invoking the Host', async () => {
  const tools = registry();
  let called = 0;
  const install = async () => { called++; return undefined; };
  const dispose = registerInstallTool(tools, install);
  const tool = tools.get(INSTALL_TOOL_NAME)!;
  expect(() => registerInstallTool(tools, install)).toThrow('already registered');
  expect(tools.get(INSTALL_TOOL_NAME)).toBe(tool);
  expect(tool.description).toContain('ask which namespace/slug or result number');
  expect(tool.description).toContain('does not resolve an ambiguous target');
  for (const input of [null, {}, { selectionRef: 1 }, { selectionRef: 'ref', path: '/untrusted' }, { namespace: 'demo' },
    { selectionRef: 'ref', namespace: 'demo', slug: 'writer' }, { selectionRef: 'ref', scope: 'other' },
    { selectionRef: 'ref', sessionId: 'other' }, { selectionRef: 'ref', callId: 'other' },
    { namespace: 'demo', slug: '../writer' }]) {
    await expect(tool.execute(input, context)).rejects.toThrow();
  }
  await expect(tool.execute({ selectionRef: 'ref' }, { ...context, agent: undefined })).rejects.toThrow('trusted conversation');
  await expect(Reflect.apply(tool.execute, tool, [{ selectionRef: 'ref' }, { ...context, callId: '' }])).rejects.toThrow('trusted conversation');
  expect(called).toBe(0);
  dispose();
  expect(tools.get(INSTALL_TOOL_NAME)).toBeUndefined();
});

test('saved acceptance uses original operation identity and presentation never invokes installation', async () => {
  const tools = registry();
  let called = 0;
  const operation: NativeOperation = { operationId: randomUUID(), revision: 2, kind: 'install', namespace: 'demo',
    slug: 'writer', version: '1.2.0', targetLabel: '当前用户', phase: 'completed', files: 'installed',
    discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0,
    context: { hostInstanceId: randomUUID(), hostLabel: 'Original Host', sourceIdentity: 'https://registry.example',
      sourceLabel: 'Original Registry', sourceRevision: 'original-revision', initiatedFrom: 'conversation',
      createdAt: '2026-10-14T00:00:00Z', updatedAt: '2026-10-14T00:01:00Z' } };
  registerInstallTool(tools, async (request, trusted, signal) => {
    called++;
    expect(request).toEqual({ selectionRef: 'ref', scope: 'user' });
    expect(trusted).toEqual({ sessionId: 'owner', cwd: '/project', callId: 'call' });
    expect(signal).toBe(context.signal);
    return operation;
  });
  const tool = tools.get(INSTALL_TOOL_NAME)!;
  const result = await tool.execute({ selectionRef: 'ref', scope: 'user' }, context);
  expect(result).toMatchObject({ status: 'accepted', operationId: operation.operationId, toolCallId: 'call',
    sourceLabel: 'Original Registry', hostLabel: 'Original Host', createdAt: operation.context.createdAt });
  expect(result).not.toHaveProperty('phase');
  expect(result).not.toHaveProperty('sourceIdentity');
  const saved = JSON.parse(JSON.stringify(result));
  expect(tool.output.presentationMeta!({}, saved)).toEqual(saved);
  const rendered = JSON.stringify(tool.output.render({}, saved));
  expect(rendered).toContain('not proof');
  expect(rendered).toContain('No operation-query tool is available');
  expect(rendered).toContain('result card automatically checks');
  expect(rendered).not.toContain('Query the operation');
  expect(called).toBe(1);
});

test('installation failures do not expose Host filesystem diagnostics', async () => {
  const tools = registry();
  registerInstallTool(tools, async () => { throw new Error('/private/host/path secret'); });
  const result = await tools.get(INSTALL_TOOL_NAME)!.execute({ selectionRef: 'ref' }, context);
  expect(result).toEqual({ schemaVersion: 1, kind: 'skillhub-install', status: 'not-confirmed', toolCallId: 'call', code: 'UNCONFIRMED' });
  expect(JSON.stringify(result)).not.toContain('/private');
  expect(JSON.stringify(result)).not.toContain('secret');
});

test('omitted scope returns visible confirmation without a fake operation or leaking its choice to model text', async () => {
  const tools = registry();
  const pending = { schemaVersion: 1 as const, kind: 'skillhub-install' as const, status: 'awaiting-confirmation' as const,
    toolCallId: 'call', selectionRef: randomUUID(), namespace: 'team', slug: 'writer', version: '1.0.0',
    sourceLabel: 'https://registry.example', sourceRevision: randomUUID(), createdAt: new Date().toISOString() };
  registerInstallTool(tools, async request => {
    expect(request).toEqual({ selectionRef: 'ref' });
    return pending;
  });
  const tool = tools.get(INSTALL_TOOL_NAME)!;
  const result = await tool.execute({ selectionRef: 'ref' }, context);
  expect(result).toEqual(pending);
  expect(result).not.toHaveProperty('operationId');
  const text = JSON.stringify(tool.output.render({}, pending));
  expect(text).toContain('Not installed');
  expect(text).toContain('choose current project or current user');
  expect(text).toContain('Do not call this tool again');
  expect(text).not.toContain(pending.selectionRef);
  expect(tool.output.presentationMeta!({}, pending)).toEqual(pending);
});
