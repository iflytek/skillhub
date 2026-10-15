import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, readdir, realpath, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
export const inject = ['sessionController', 'workspaceRegistry', 'tools', 'skillhub', 'clientModules'];
export function apply(ctx, config) {
  const root = resolve(config.root);
  assert(root.startsWith(resolve(tmpdir()) + sep));
  assert(fileURLToPath(import.meta.url).startsWith(resolve(tmpdir()) + sep));
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  const abort = new AbortController();
  const timer = setTimeout(() => {
    void run(ctx, root, abort.signal).then(result => writeFile(join(root, 'tool-cancellation-result.json'), JSON.stringify(result, null, 2)),
      error => writeFile(join(root, 'tool-cancellation-result.json'), JSON.stringify({ passed: false, error: error.message })));
  }, 1000);
  return () => { clearTimeout(timer); abort.abort(); };
}
async function until(read, signal) {
  const deadline = Date.now() + 20000;
  for (;;) {
    const value = await read(); if (value) return value;
    assert(Date.now() < deadline, 'Cancellation probe timed out');
    await delay(50, undefined, { signal });
  }
}
async function run(ctx, root, signal) {
  await until(() => ctx.skillhub.getLocalRuntimeStatus()?.state === 'ready', signal);
  assert.equal(await realpath(ctx.clientModules.clientPath('@astron-team/skillhub-dsh')),
    await realpath(join(root, 'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/lib/client.js')));
  const project = join(root, 'cancellation-project'); await mkdir(project);
  const workspace = await ctx.workspaceRegistry.create(project);
  const session = await ctx.sessionController.create({ workspaceId: workspace.id, agentPreset: 'standard' });
  const resolved = await ctx.sessionController.resolveAgent(session.sessionId);
  if ('error' in resolved) throw resolved.error;
  const agent = resolved.agent;
  const args = { namespace: process.env.DSH_FIXTURE_NAMESPACE, slug: 'document-helper', scope: 'project' };
  const log = process.env.DSH_FIXTURE_REQUEST_LOG; assert(log);
  const requests = async () => (await readFile(log, 'utf8')).trim().split('\n').map(line => JSON.parse(line));
  const records = async () => (await readdir(join(root, 'home/skillhub/operations'))).filter(name => name.endsWith('.json') && name !== 'host.json');
  assert.equal((await records()).length, 0);
  const cancelled = new AbortController();
  const early = ctx.tools.execute({ callId: randomUUID(), name: 'astron_skillhub_install', arguments: args, agent, signal: cancelled.signal })
    .then(value => value, () => null);
  await until(async () => (await requests()).some(item => item.path.endsWith('/resolve')), signal);
  cancelled.abort();
  const earlyResult = await early;
  assert(earlyResult === null || earlyResult.isError || earlyResult.value?.status !== 'accepted');
  assert.equal((await records()).length, 0);
  assert.equal((await requests()).filter(item => item.path === '/download').length, 0);
  const acceptedSignal = new AbortController();
  const callId = randomUUID();
  const receipt = await ctx.tools.execute({ callId, name: 'astron_skillhub_install', arguments: args, agent, signal: acceptedSignal.signal });
  assert.equal(receipt.isError, false); assert.equal(receipt.value.status, 'accepted');
  await until(async () => (await requests()).some(item => item.path === '/download'), signal);
  const pending = await ctx.skillhub.getInstallationOperation(session.sessionId, receipt.value.operationId);
  assert.notEqual(pending.phase, 'completed');
  acceptedSignal.abort();
  const operation = await until(async () => {
    const value = await ctx.skillhub.getInstallationOperation(session.sessionId, receipt.value.operationId);
    return ['completed', 'failed', 'unknown', 'recovery-required'].includes(value?.phase) ? value : null;
  }, signal);
  assert.equal(operation.phase, 'completed');
  assert.equal(await readFile(join(project, '.dsh/skills/document-helper/references/note.md'), 'utf8'), 'Native installation resource');
  const replay = await ctx.tools.execute({ callId, name: 'astron_skillhub_install', arguments: args, agent, signal });
  assert.equal(replay.value.operationId, receipt.value.operationId);
  assert.equal((await records()).length, 1);
  assert.equal((await requests()).filter(item => item.path === '/download').length, 1);
  return { passed: true, cancelledBeforeAcceptanceNoOperation: true, cancelledBeforeAcceptanceNoDownload: true,
    cancelledAfterAcceptanceCompleted: true, replaySameOperation: true, resourceVerified: true, downloads: 1,
    operations: 1, modelInvoked: false, installedClientVerified: true, node: process.versions.node };
}
