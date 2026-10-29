import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, readdir, rename, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

export const inject = ['sessionController', 'workspaceRegistry', 'tools', 'sessions', 'skills', 'skillhub', 'typertGateway'];
export function apply(ctx, config) {
  const root = resolve(config.root);
  assert(root.startsWith(resolve(tmpdir()) + sep));
  assert(fileURLToPath(import.meta.url).startsWith(resolve(tmpdir()) + sep));
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  const checkpoint = { scenario: 'setup', checkpoint: 'setup', assertion: 'none' };
  const abort = new AbortController();
  const timer = setTimeout(() => run(ctx, root, AbortSignal.any([abort.signal, AbortSignal.timeout(70000)]), checkpoint)
    .catch(error => writeFile(join(root, 'skill-availability-result.json'), JSON.stringify({ passed: false, error: error.message, checkpoint, operation: checkpoint.operation }))), 1000);
  return () => { clearTimeout(timer); abort.abort(); };
}

async function digest(directory) {
  const hash = createHash('sha256');
  async function visit(path, relative = '') {
    let entries;
    try { entries = await readdir(path, { withFileTypes: true }); }
    catch (error) { if (error.code === 'ENOENT') { hash.update('missing:' + relative); return; } throw error; }
    const observed = await stat(path);
    hash.update(JSON.stringify([relative, observed.mtimeMs, observed.ctimeMs, observed.mode]));
    for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name))) {
      const name = relative + '/' + entry.name;
      hash.update(name);
      if (entry.isDirectory()) await visit(join(path, entry.name), name);
      else if (entry.isFile()) {
        const observed = await stat(join(path, entry.name));
        hash.update(JSON.stringify([observed.mtimeMs, observed.ctimeMs, observed.mode]));
        hash.update(await readFile(join(path, entry.name)));
      }
      else throw new Error('Unexpected fixture filesystem entry');
    }
  }
  await visit(directory);
  return hash.digest('hex');
}

async function run(ctx, root, signal, checkpoint) {
  const mark = (phase, assertion = 'none') => Object.assign(checkpoint, { checkpoint: phase, assertion });
  const project = join(root, 'availability-' + randomUUID());
  await mkdir(project);
  const workspace = await ctx.workspaceRegistry.create(project);
  const created = await ctx.sessionController.create({ workspaceId: workspace.id, agentPreset: 'standard' });
  const resolved = await ctx.sessionController.resolveAgent(created.sessionId);
  if ('error' in resolved) throw resolved.error;
  const agent = resolved.agent;
  const execute = (name, args) => ctx.tools.execute({ callId: randomUUID(), name, arguments: args, agent, signal });
  const namespace = process.env.DSH_FIXTURE_NAMESPACE;
  const paths = { project: join(project, '.dsh/skills/document-helper'), user: join(root, 'home/skills/document-helper') };
  const records = [];
  const protectedPaths = [project, join(root, 'home/skills'), join(root, 'home/skillhub'), join(root, 'native-install-home/.skillhub')];
  async function check(args, expected) {
    mark('before-digest');
    const before = await Promise.all(protectedPaths.map(digest));
    mark('execute');
    const result = await execute('astron_skillhub_check_availability', args);
    mark('assertion', 'isError');
    assert.equal(result.isError, false);
    mark('assertion', 'kind');
    assert.equal(result.value.kind, 'skillhub-availability');
    mark('assertion', 'schemaVersion');
    assert.equal(result.value.schemaVersion, 1);
    for (const [key, value] of Object.entries(expected)) { mark('assertion', key); assert.deepEqual(result.value.diagnosis[key], value, key); }
    mark('assertion', 'installation');
    if (args.name) assert.equal(result.value.diagnosis.installation, undefined);
    else {
      mark('assertion', 'namespace');
      assert.equal(result.value.diagnosis.installation.namespace, namespace);
      mark('assertion', 'slug');
      assert.equal(result.value.diagnosis.installation.slug, 'document-helper');
      mark('assertion', 'version');
      assert.equal(result.value.diagnosis.installation.version, '1.2.0');
    }
    mark('assertion', 'version');
    assert.equal(result.value.diagnosis.version, undefined);
    mark('assertion', 'fingerprint');
    assert.equal(result.value.diagnosis.fingerprint, undefined);
    const rendered = JSON.stringify(result.content);
    mark('assertion', 'render');
    assert(rendered.includes('skillhub-availability'), 'Real tools.execute rendered the diagnosis');
    mark('assertion', 'privacy');
    for (const forbidden of [root, 'AVAILABILITY_PRIVATE_SENTINEL', 'resourceBase', 'metadata', 'provider']) assert(!rendered.includes(forbidden), 'Model render leaked ' + forbidden);
    mark('after-digest', 'unchanged');
    assert.deepEqual(await Promise.all(protectedPaths.map(digest)), before, 'Diagnosis changed installation/inventory/operation files');
    records.push({ query: args.name ? 'name' : 'managed', ...expected });
    return result.value.diagnosis;
  }
  async function install(scope) {
    checkpoint.operation = { deadlineReached: false };
    mark('install');
    const result = await execute('astron_skillhub_install', { namespace, slug: 'document-helper', scope });
    mark('assertion', 'isError');
    assert.equal(result.isError, false);
    mark('assertion', 'accepted');
    assert.equal(result.value.status, 'accepted');
    const deadline = Date.now() + 15000;
    let operation;
    mark('install-poll');
    while (Date.now() < deadline) {
      operation = await ctx.skillhub.getInstallationOperation(created.sessionId, result.value.operationId);
      checkpoint.operation = { phase: operation?.phase, files: operation?.files, recovery: operation?.recovery,
        discovery: operation?.discovery, deadlineReached: Date.now() >= deadline };
      if (['completed', 'failed', 'unknown', 'recovery-required'].includes(operation?.phase)) break;
      await delay(100, undefined, { signal });
    }
    checkpoint.operation.deadlineReached = Date.now() >= deadline;
    mark('assertion', 'completed');
    assert.equal(operation?.phase, 'completed');
  }
  async function reference(scope) {
    mark('reference');
    const sourceRevision = ctx.skillhub.getContext().sourceRevision;
    const list = await ctx.typertGateway.invoke({ peer: ctx.get('connection')?.browserPeer ?? ctx.get('connection')?.operator,
      namespace: 'skillhub', method: 'listInstalled', args: { agentId: created.sessionId, sourceRevision }, signal });
    const item = list.items.find(value => value.namespace === namespace && value.scope === scope);
    mark('assertion', 'reference');
    assert(item);
    return { sourceRevision, itemKey: item.itemKey };
  }
  checkpoint.scenario = 'user-install';
  await install('user');
  checkpoint.scenario = 'user-winner';
  await check(await reference('user'), { association: 'managed-reference', scope: 'user', integrity: 'verified', discovery: 'winner', name: 'document-helper' });
  checkpoint.scenario = 'project-install';
  await install('project');
  checkpoint.scenario = 'project-winner';
  await check(await reference('project'), { scope: 'project', integrity: 'verified', discovery: 'winner' });
  checkpoint.scenario = 'user-shadowed';
  await check(await reference('user'), { integrity: 'verified', discovery: 'shadowed', reason: 'same-name-winner' });
  checkpoint.scenario = 'remote';
  const remote = await ctx.typertGateway.invoke({ peer: ctx.get('connection')?.browserPeer ?? ctx.get('connection')?.operator,
    namespace: 'skillhub', method: 'checkAvailability', args: { agentId: created.sessionId, ...await reference('project') }, signal });
  mark('assertion', 'discovery');
  assert.equal(remote.discovery, 'winner');

  checkpoint.scenario = 'by-name';
  await check({ name: 'document-helper' }, { association: 'not-associated', integrity: 'unknown', discovery: 'winner' });
  const target = paths.project;
  const skillPath = join(target, 'SKILL.md');
  const original = await readFile(skillPath, 'utf8');
  for (const mode of ['content-modified', 'skill-missing', 'directory-missing']) {
    checkpoint.scenario = mode;
    mark('mutation');
    const backup = join(root, 'availability-backup-' + randomUUID());
    try {
      if (mode === 'content-modified') await writeFile(skillPath, original + '\nAVAILABILITY_PRIVATE_SENTINEL');
      else await rename(mode === 'skill-missing' ? skillPath : target, backup);
      await check(await reference('project'), { integrity: mode === 'content-modified' ? 'changed' : 'missing' });
    } finally {
      if (mode === 'content-modified') await writeFile(skillPath, original);
      else await rename(backup, mode === 'skill-missing' ? skillPath : target);
    }
  }
  checkpoint.scenario = 'policy';
  for (const userInvocable of [true, false]) for (const modelInvocable of [true, false]) {
    const name = `availability-policy-${Number(userInvocable)}-${Number(modelInvocable)}`;
    const dispose = ctx.skills.register({ name, description: 'Fixture', content: 'AVAILABILITY_PRIVATE_SENTINEL',
      source: 'runtime', provider: 'AVAILABILITY_PRIVATE_SENTINEL', metadata: { private: 'AVAILABILITY_PRIVATE_SENTINEL' },
      resourceBase: { kind: 'directory', path: join(root, 'AVAILABILITY_PRIVATE_SENTINEL') }, invocation: { userInvocable, modelInvocable } });
    try { await check({ name }, { association: 'not-associated', integrity: 'unknown', discovery: 'winner', invocation: { userInvocable, modelInvocable } }); }
    finally { dispose(); }
  }
  checkpoint.scenario = 'absent';
  await check({ name: 'availability-absent' }, { discovery: 'not-discovered', integrity: 'unknown' });
  checkpoint.scenario = 'incomplete-provider';
  const dispose = ctx.skills.registerProvider(() => ({ name: 'availability-incomplete',
    list: async () => ({ candidates: [], complete: false }), get: async () => undefined }));
  try { await check({ name: 'document-helper' }, { discovery: 'unknown', reason: 'snapshot-incomplete' }); }
  finally { dispose(); }
  checkpoint.scenario = 'failed-provider';
  const failedProvider = ctx.skills.registerProvider(() => ({ name: 'availability-failed',
    list: async () => { throw new Error('AVAILABILITY_PRIVATE_SENTINEL'); }, get: async () => undefined }));
  try { await check({ name: 'document-helper' }, { discovery: 'unknown', reason: 'snapshot-incomplete' }); }
  finally { failedProvider(); }
  await writeFile(join(root, 'skill-availability-result.json'), JSON.stringify({ passed: true, records,
    realAgentScope: true, remoteAgentMethod: true, modelRenderChecked: true, protectedFilesUnchanged: true,
    registry: 'isolated-fixture', modelInvoked: false }, null, 2));
}
