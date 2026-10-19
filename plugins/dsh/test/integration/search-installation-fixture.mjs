// Load a temporary copy as an observer in a fresh isolated DSH Web Profile.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { lstatSync, realpathSync } from 'node:fs';
import { mkdir, readFile, realpath, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

export const inject = ['sessionController', 'workspaceRegistry', 'tools', 'sessions', 'skillhub'];

export function assertProbeEnvironment(root, environment) {
  for (const [key, name] of [['HOME', 'native-install-home'], ['DSH_HOME', 'home'], ['DSH_AGENTS_HOME', 'agents']]) {
    const expected = join(root, name);
    assert.equal(environment[key], expected);
    assert(lstatSync(expected).isDirectory(), `${key} must be a real directory, not a symlink`);
    assert.equal(realpathSync(expected), expected, `${key} must not redirect outside the isolated root`);
  }
}

export function assertProbeFile(root, path) {
  const expected = resolve(path);
  const offset = relative(root, expected);
  assert(offset && !offset.startsWith('..') && !isAbsolute(offset), 'Fixture files must stay inside the isolated root');
  assert(lstatSync(expected).isFile(), 'Fixture mutations require regular non-symlink files');
  assert.equal(realpathSync(expected), expected, 'Fixture files and parent directories must not redirect');
}

async function writeFixtureFile(root, path, bytes) {
  assertProbeFile(root, path);
  await writeFile(path, bytes);
}

export function apply(ctx, config) {
  const root = realpathSync(resolve(config.root));
  for (const path of [root, fileURLToPath(import.meta.url)]) {
    const offset = relative(realpathSync(tmpdir()), realpathSync(path));
    assert(offset && !offset.startsWith('..') && !isAbsolute(offset), 'Use an isolated temporary root and observer copy');
  }
  assertProbeEnvironment(root, process.env);
  const reportPath = join(root, 'search-installation-ready.json');
  const checkReport = () => {
    try { assertProbeFile(root, reportPath); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  };
  checkReport();
  const report = result => {
    checkReport();
    return writeFile(reportPath, JSON.stringify(result, null, 2), { mode: 0o600 });
  };
  const controller = new AbortController();
  const timer = setTimeout(() => run(ctx, root, config, controller.signal).then(
    result => report({ passed: true, ...result }),
    error => report({ passed: false, error: error.message }),
  ).catch(() => undefined), 1000);
  return () => { clearTimeout(timer); controller.abort(); };
}

async function run(ctx, root, config, signal) {
  // Never install again when the Host is restarted to inspect persisted history.
  try {
    const previous = JSON.parse(await readFile(join(root, 'search-installation-ready.json'), 'utf8'));
    if (previous.passed) return previous;
    throw new Error('A previous fixture failed; inspect it and use a fresh isolated root rather than replaying writes');
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  const profile = config.profile ?? 'search-installation';
  const packagePath = join(root, 'home/profiles', profile, 'node_modules/@astron-team/skillhub-dsh/lib/index.js');
  const packageOffset = relative(await realpath(root), await realpath(packagePath));
  assert(packageOffset && !packageOffset.startsWith('..') && !isAbsolute(packageOffset), 'Use the isolated candidate package');
  const runtimeDeadline = Date.now() + 20000;
  while (ctx.skillhub.getLocalRuntimeStatus()?.state !== 'ready') {
    signal.throwIfAborted();
    assert(Date.now() < runtimeDeadline, 'Candidate local runtime did not become ready');
    await delay(100, undefined, { signal });
  }
  const namespace = config.namespace ?? 'meeting';
  const slug = config.slug ?? 'meeting-notes';
  const query = config.query ?? '助手';
  const project = join(root, 'search-installation-' + randomUUID());
  await mkdir(project);
  const workspace = await ctx.workspaceRegistry.create(project);
  const created = await ctx.sessionController.create({ workspaceId: workspace.id, agentPreset: 'standard' });
  const resolved = await ctx.sessionController.resolveAgent(created.sessionId);
  if ('error' in resolved) throw resolved.error;
  const agent = resolved.agent;
  const history = agent.session;
  const title = `安装状态集成夹具（非模型） ${history.id.slice(-8)}`;
  await ctx.sessionController.rename({ sessionId: history.id, title });
  let turn = 0;
  async function search(label) {
    const callId = 'search-state-' + randomUUID();
    const args = { q: query };
    const result = await ctx.tools.execute({ callId, name: 'astron_skillhub_search', arguments: args,
      agent, signal: AbortSignal.any([signal, AbortSignal.timeout(20000)]) });
    assert.equal(result.isError, false, JSON.stringify(result));
    assert.equal(result.value.kind, 'skillhub-search');
    assert.deepEqual(result.meta, result.value);
    const item = result.value.items.find(item => item.namespace === namespace && item.slug === slug);
    assert(item, `Fixture search must return ${namespace}/${slug}`);
    assert(item.installation, 'Candidate search must include local installation status');
    const serialized = JSON.stringify(result.value);
    assert(!serialized.includes(root), 'No private Host paths in search output');
    assert(result.content.some(block => block.type === 'text' && block.text.includes('search-time snapshot')),
      'Real model tool rendering must explain snapshot semantics');
    turn += 1;
    history.append('turn/start', { turn });
    history.append('step/start', { turn, step: 1 });
    history.append('assistant/message', { turn, step: 1, stream: [
      { type: 'tool-call-chunks', time0: Date.now(), index: 0, dt: [0], id: callId,
        name: 'astron_skillhub_search', args: [JSON.stringify(args)] },
    ], message: { id: randomUUID(), role: 'assistant',
      source: { kind: 'model', provider: 'integration-fixture-no-model', model: 'integration-fixture-no-model' },
      content: [{ type: 'text', text: `集成夹具：${label}。未调用模型。` },
        { type: 'tool-call', id: callId, name: 'astron_skillhub_search', arguments: JSON.stringify(args) }],
    } }, { surfaceOp: 'append' });
    history.append('tool/call', { turn, step: 1, callId, name: 'astron_skillhub_search', arguments: JSON.stringify(args) });
    history.append('tool/result', { turn, step: 1, message: {
      id: randomUUID(), role: 'tool', source: { kind: 'tool', callId }, toolCallId: callId,
      content: result.content, isError: false,
    }, meta: result.meta }, { surfaceOp: 'append' });
    history.append('step/end', { turn, step: 1 });
    history.append('turn/end', { turn, reason: { kind: 'completed' } });
    await ctx.sessions.flush(history);
    return { value: result.value, item };
  }
  async function install(selectionRef, scope) {
    const result = await ctx.tools.execute({ callId: 'install-state-' + randomUUID(), name: 'astron_skillhub_install',
      arguments: { selectionRef, scope }, agent, signal: AbortSignal.any([signal, AbortSignal.timeout(20000)]) });
    assert.equal(result.isError, false, JSON.stringify(result));
    assert.equal(result.value.status, 'accepted', JSON.stringify(result.value));
    let operation;
    const deadline = Date.now() + 20000;
    do {
      signal.throwIfAborted();
      operation = await ctx.skillhub.getInstallationOperation(history.id, result.value.operationId);
      if (['completed', 'failed', 'recovery-required', 'unknown'].includes(operation?.phase)) break;
      await delay(100, undefined, { signal });
    } while (Date.now() < deadline);
    assert.equal(operation?.phase, 'completed', JSON.stringify(operation));
    return result.value.version;
  }
  const before = await search('安装前');
  assert.equal(before.item.installation.complete, true);
  assert.deepEqual(before.item.installation.records, [], 'Fresh root must have no managed installations');
  const retained = JSON.stringify(before.value);
  const version = await install(before.item.selectionRef, 'project');
  const projectOnly = await search('真实安装到当前项目后');
  assert.deepEqual(projectOnly.item.installation.records, [{ scope: 'project', version, state: 'present' }]);
  await install(projectOnly.item.selectionRef, 'user');
  const dual = await search('真实安装到用户和项目后');
  assert.deepEqual(dual.item.installation.records.map(item => item.scope).sort(), ['project', 'user']);
  assert(dual.item.installation.records.every(item => item.version === version && item.state === 'present'));

  const inventoryPath = join(root, 'native-install-home/.skillhub/inventory.json');
  assertProbeFile(root, inventoryPath);
  const inventoryBytes = await readFile(inventoryPath);
  const inventory = JSON.parse(inventoryBytes.toString());
  const owned = inventory.items.find(item => item.namespace === namespace && item.slug === slug);
  assert(owned?.targets.length === 2);
  let external;
  let differentNamespace;
  let abnormal;
  let unknown;
  try {
    // This is a labelled test-only ledger mutation, never a claimed external installation.
    const foreign = { ...owned, registry: 'https://external.invalid' };
    await writeFixtureFile(root, inventoryPath, JSON.stringify({ ...inventory, items: [foreign] }));
    external = await search('合成其他 Registry 记录，不应认领');
    assert.deepEqual(external.item.installation.records, []);
    assert.equal(external.item.installation.complete, true);
    const otherNamespaceRecord = { ...owned, namespace: 'fixture-other-namespace' };
    await writeFixtureFile(root, inventoryPath, JSON.stringify({ ...inventory, items: [otherNamespaceRecord] }));
    differentNamespace = await search('合成同名不同命名空间与元数据不一致记录，不应认领');
    assert.deepEqual(differentNamespace.item.installation.records, []);
    assert.equal(differentNamespace.item.installation.complete, false,
      'Deliberately inconsistent foreign-namespace metadata makes the whole projection incomplete');
  } finally {
    await writeFixtureFile(root, inventoryPath, inventoryBytes);
    assert.deepEqual(await readFile(inventoryPath), inventoryBytes);
  }
  const projectTarget = owned.targets.find(target => target.rootDir === join(project, '.dsh/skills'));
  assert(projectTarget);
  const metadataPath = join(projectTarget.installDir, '.skillhub/metadata.json');
  assertProbeFile(root, metadataPath);
  const metadataBytes = await readFile(metadataPath);
  try {
    await writeFixtureFile(root, metadataPath, '{invalid-json');
    abnormal = await search('合成项目元数据损坏，用户范围仍正常');
    assert.equal(abnormal.item.installation.complete, false);
    assert(abnormal.item.installation.records.some(item => item.scope === 'user' && item.state === 'present'));
    assert(abnormal.item.installation.records.some(item => item.scope === 'project' && item.state !== 'present'));
  } finally {
    await writeFixtureFile(root, metadataPath, metadataBytes);
    assert.deepEqual(await readFile(metadataPath), metadataBytes);
  }
  try {
    await writeFixtureFile(root, inventoryPath, '{invalid-json');
    unknown = await search('合成不可读安装清单，状态应未知');
    assert.equal(unknown.item.installation.complete, false);
    assert.deepEqual(unknown.item.installation.records, []);
  } finally {
    await writeFixtureFile(root, inventoryPath, inventoryBytes);
    assert.deepEqual(await readFile(inventoryPath), inventoryBytes);
  }
  const restored = await search('恢复夹具后重新读取当前状态');
  assert.equal(restored.item.installation.complete, true);
  assert.equal(restored.item.installation.records.length, 2);
  assert.equal(JSON.stringify(before.value), retained, 'Older search-time result must remain unchanged');
  const snapshots = Object.fromEntries(Object.entries({ before, projectOnly, dual, external, differentNamespace, abnormal, unknown, restored })
    .map(([key, result]) => [key, result.item.installation]));
  return { modelInvoked: false, syntheticHistory: true, trueInstallScopes: ['project', 'user'],
    namespace, slug, version, title, sessionId: history.id, workspace: project, snapshots,
    mutationsRestoredByteForByte: true, historicalSnapshotUnchanged: true };
}
