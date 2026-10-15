import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
import { setTimeout as delay } from 'node:timers/promises';

export const inject = ['sessionController', 'workspaceRegistry', 'sessions', 'tools', 'skillhub'];
export function apply(ctx, config) {
  const root = resolve(config.root);
  assert(root.startsWith(resolve(tmpdir()) + sep));
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  const abort = new AbortController();
  const timer = setTimeout(() => {
    void run(ctx, root, abort.signal).catch(error => writeFile(join(root, 'dual-result.json'), JSON.stringify({ error: error.message })));
  }, 500);
  return () => { clearTimeout(timer); abort.abort(); };
}
async function run(ctx, root, signal) {
  const project = join(root, 'dual-' + randomUUID());
  await mkdir(project);
  const workspace = await ctx.workspaceRegistry.create(project);
  const created = await ctx.sessionController.create({ workspaceId: workspace.id, agentPreset: 'standard' });
  const resolved = await ctx.sessionController.resolveAgent(created.sessionId);
  if ('error' in resolved) throw resolved.error;
  const agent = resolved.agent;
  const title = 'SkillHub concurrent ' + randomUUID();
  await ctx.sessionController.rename({ sessionId: created.sessionId, title });
  agent.session.append('turn/start', { turn: 1 });
  agent.session.append('step/start', { turn: 1, step: 1 });
  agent.session.append('assistant/message', { turn: 1, step: 1, stream: [], message: {
    id: 'concurrency-fixture', role: 'assistant', source: { kind: 'model', provider: 'test-fixture', model: 'test-fixture' },
    content: [{ type: 'text', text: 'Isolated concurrency fixture; no model invoked.' }],
  } }, { surfaceOp: 'append' });
  agent.session.append('step/end', { turn: 1, step: 1 });
  agent.session.append('turn/end', { turn: 1, reason: { kind: 'completed' } });
  await ctx.sessions.flush(agent.session);
  await writeFile(join(root, 'dual-ready.json'), JSON.stringify({ title, project }));
  const deadline = Date.now() + 90000;
  let pageOperationId;
  while (!pageOperationId) {
    assert(Date.now() < deadline, 'Page installation was not submitted');
    try { pageOperationId = JSON.parse(await readFile(join(root, 'dual-trigger.json'), 'utf8')).operationId; }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
    if (!pageOperationId) await delay(100, undefined, { signal });
  }
  const before = await ctx.skillhub.getInstallationOperation(agent.session.id, pageOperationId);
  assert(before && !['completed', 'failed', 'recovery-required', 'unknown'].includes(before.phase), 'Requests must overlap');
  const toolCallId = 'dual-' + randomUUID();
  const toolArguments = { namespace: 'demo', slug: 'document-helper', scope: 'project' };
  const tool = await ctx.tools.execute({ callId: toolCallId, name: 'astron_skillhub_install', arguments: toolArguments, agent, signal });
  assert.equal(tool.isError, false);
  const operationIds = [pageOperationId, ...(tool.value.status === 'accepted' ? [tool.value.operationId] : [])];
  let operations;
  for (;;) {
    operations = await Promise.all(operationIds.map(id => ctx.skillhub.getInstallationOperation(agent.session.id, id)));
    assert(Date.now() < deadline, 'Concurrent installation did not terminate');
    if (operations.every(op => ['completed', 'failed'].includes(op?.phase))) break;
    await delay(100, undefined, { signal });
  }
  assert.equal(operations.filter(op => op.phase === 'completed').length, 1);
  assert.equal(operations[0].phase, 'completed');
    agent.session.append('turn/start', { turn: 2 });
    agent.session.append('step/start', { turn: 2, step: 1 });
    agent.session.append('assistant/message', { turn: 2, step: 1, stream: [
      { type: 'tool-call-chunks', time0: Date.now(), index: 0, dt: [0], id: toolCallId,
        name: 'astron_skillhub_install', args: [JSON.stringify(toolArguments)] },
    ], message: { id: 'skillhub-install-fixture-assistant', role: 'assistant',
      source: { kind: 'model', provider: 'test-fixture', model: 'test-fixture' },
      content: [{ type: 'tool-call', id: toolCallId, name: 'astron_skillhub_install', arguments: JSON.stringify(toolArguments) }],
    } }, { surfaceOp: 'append' });
    agent.session.append('tool/call', { turn: 2, step: 1, callId: toolCallId,
      name: 'astron_skillhub_install', arguments: JSON.stringify(toolArguments) });
    agent.session.append('tool/result', { turn: 2, step: 1, message: {
      id: 'skillhub-install-fixture-result', role: 'tool', source: { kind: 'tool', callId: toolCallId },
      toolCallId, content: tool.content, isError: false,
    }, meta: tool.meta }, { surfaceOp: 'append' });
    agent.session.append('step/end', { turn: 2, step: 1 });
    agent.session.append('turn/end', { turn: 2, reason: { kind: 'completed' } });
    await ctx.sessions.flush(agent.session);
  await writeFile(join(root, 'dual-result.json'), JSON.stringify({ passed: true, overlapPhase: before.phase,
    modelInvoked: false, toolStatus: tool.value.status, toolCode: tool.value.code,
    operations: operations.map(op => ({ operationId: op.operationId, phase: op.phase, files: op.files })) }));
}
