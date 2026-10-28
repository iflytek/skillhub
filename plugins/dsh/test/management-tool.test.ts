import { expect, test } from 'bun:test';
import type { ToolDefinition, ToolRunContext } from '@deepseek-ai/dsh-tools';
import { MANAGEMENT_TOOLS, registerManagementTools } from '../src/host/management-tool.js';

function registry() {
  const entries = new Map<string, ToolDefinition>();
  return { entries, get: (name: string) => entries.get(name), register: (tool: ToolDefinition) => {
    entries.set(tool.name, tool); return () => { entries.delete(tool.name); };
  } };
}
const context = { agent: { session: { id: 'owner', header: { cwd: '/project' } } },
  callId: 'call', signal: new AbortController().signal } as ToolRunContext;

test('management registration preserves occupied tools and strict parameters reject injected authorization', async () => {
  const tools = registry(); let calls = 0;
  const dispose = registerManagementTools(tools, async () => { calls++; return {}; });
  const tool = tools.get(MANAGEMENT_TOOLS.upgrade)!;
  expect(() => registerManagementTools(tools, async () => ({}))).toThrow('already registered');
  expect(tools.entries.size).toBe(8);
  for (const extra of [{ confirmed: true }, { planId: 'plan' }, { path: '/forged' }]) {
    await expect(tool.execute({ sourceRevision: 'source', itemKey: 'item', ...extra }, context)).rejects.toThrow();
  }
  await expect(tool.execute({ sourceRevision: 'source', itemKey: 'item' }, { ...context, agent: undefined })).rejects.toThrow('Trusted conversation');
  expect(calls).toBe(0); dispose(); expect(tools.entries.size).toBe(0);
});

test('management model output contains receipt correlation but not paths, confirmation references or raw diagnostics', async () => {
  const tools = registry();
  registerManagementTools(tools, async () => ({ schemaVersion: 1, kind: 'skillhub-management', status: 'accepted',
    sourceLabel: 'https://private-registry.example', confirmationRef: 'private-confirmation', operation: { operationId: 'operation', phase: 'completed',
      namespace: 'team', slug: 'writer', version: '2', targetLabel: '当前用户', diagnostics: '/private/error',
      targetPaths: ['/private/skills'], context: { hostInstanceId: 'host', sourceIdentity: '/private/registry' } } }));
  const tool = tools.get(MANAGEMENT_TOOLS.upgrade)!;
  const result = JSON.parse(JSON.stringify(await tool.execute({ sourceRevision: 'source', itemKey: 'item' }, context)));
  const rendered = JSON.stringify(tool.output.render({}, result));
  expect(rendered).toContain('operation'); expect(rendered).not.toContain('/private'); expect(rendered).not.toContain('private-confirmation'); expect(rendered).not.toContain('private-registry');
  expect(rendered).toContain('REAL HUMAN APPROVAL IS ALREADY COMPLETE');
  expect(rendered).toContain('DO NOT ask for approval again');
  expect(tool.output.presentationMeta!({}, result)).toEqual(result);
});


test('list model projection retains installation choices without exposing the source URL', async () => {
  const tools = registry();
  const response = { kind: 'skillhub-installed', sourceLabel: 'https://private-registry.example', sourceRevision: 'revision',
    items: [{ itemKey: 'user-key', scope: 'user' }, { itemKey: 'project-key', scope: 'project' }] };
  registerManagementTools(tools, async () => response);
  const tool = tools.get(MANAGEMENT_TOOLS.list)!;
  const result = JSON.parse(JSON.stringify(await tool.execute({}, context)));
  const rendered = JSON.stringify(tool.output.render({}, result));
  expect(rendered).not.toContain('private-registry');
  expect(rendered).toContain('user-key'); expect(rendered).toContain('project-key');
  expect(tool.output.presentationMeta!({}, result)).toEqual(response);
});

test('every registered management tool teaches fresh-state reads and concise public explanations in both description and rendered output', async () => {
  const tools = registry();
  const operationId = '11111111-1111-4111-8111-111111111111';
  const hostInstanceId = '22222222-2222-4222-8222-222222222222';
  const item = { itemKey: 'private-item-routing-key', namespace: 'team', slug: 'writer', scope: 'project', version: '1.3.0', state: 'present' };
  registerManagementTools(tools, async action => action === 'list'
    ? { kind: 'skillhub-installed', sourceRevision: 'private-source-revision', items: [item], complete: true, unavailable: [], issues: [] }
    : action === 'check' ? { kind: 'skillhub-update-check', sourceRevision: 'private-source-revision', item, check: { state: 'current', currentVersion: '1.3.0' } }
      : { kind: action === 'operation' ? 'skillhub-operation' : 'skillhub-management', status: 'accepted', item,
        operation: { operationId, context: { hostInstanceId }, kind: action, phase: 'completed', namespace: 'team', slug: 'writer', version: '1.3.0', targetLabel: '当前项目' } });
  for (const [action, name] of Object.entries(MANAGEMENT_TOOLS)) {
    const tool = tools.get(name)!;
    const args = action === 'list' ? {} : action === 'operation' ? { operationId, hostInstanceId }
      : { sourceRevision: 'private-source-revision', itemKey: item.itemKey };
    const result = JSON.parse(JSON.stringify(await tool.execute(args, context)));
    const rendered = JSON.stringify(tool.output.render(args, result));
    for (const guidance of [tool.description, rendered]) {
      expect(guidance).toContain('point-in-time snapshots');
      expect(guidance).toContain('UI buttons or external actions');
      expect(guidance).toContain('call astron_skillhub_list_installed again in the current turn');
      expect(guidance).toContain('never quote, compare or use them as evidence');
      expect(guidance).toContain('skill name, installation scope, version and state');
      expect(guidance).toContain('short conclusion instead of duplicating it');
    }
    if (action === 'availability') continue;
    // Routing references remain usable by subsequent tools; only prose guidance changes.
    expect(rendered).toContain(item.itemKey);
    if (action === 'list' || action === 'check') expect(rendered).toContain('private-source-revision');
    else expect(rendered).toContain(operationId);
    expect(tool.output.presentationMeta!(args, result)).toEqual(result);
  }
});


test('availability tool enforces exclusive selectors and strips unexpected diagnostic fields', async () => {
  const tools = registry(); let calls = 0;
  registerManagementTools(tools, async () => { calls++; return { kind: 'skillhub-availability', diagnosis: {
    checkedAt: 'now', name: 'writer', association: 'not-associated', integrity: 'unknown', discovery: 'winner', reason: 'observed',
    invocation: { userInvocable: false, modelInvocable: true, raw: 'secret-policy' },
    installation: { namespace: 'team', slug: 'writer', version: '1', scope: 'user', path: '/secret/installation' },
    content: 'secret-content', path: '/secret/path', metadata: { token: 'secret-token' }, provider: 'secret-provider',
  } }; });
  const tool = tools.get(MANAGEMENT_TOOLS.availability)!;
  for (const args of [{}, { name: '../path' }, { name: 'writer', itemKey: 'key', sourceRevision: 'source' }, { itemKey: 'key' }, { name: 'writer', confirmed: true }]) {
    await expect(tool.execute(args, context)).rejects.toThrow();
  }
  expect(calls).toBe(0);
  const value = await tool.execute({ name: 'writer' }, context);
  const rendered = JSON.stringify(tool.output.render({ name: 'writer' }, JSON.parse(JSON.stringify(value))));
  expect(rendered).toContain('not-associated'); expect(rendered).not.toContain('secret');
});
