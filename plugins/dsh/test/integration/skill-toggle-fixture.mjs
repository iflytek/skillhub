// Load a copied observer from a fresh temporary DSH profile; no model calls.
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

export const inject = ['sessionController', 'workspaceRegistry', 'skills', 'skillhub', 'typertGateway'];
export function apply(ctx, config) {
  const root = resolve(config.root);
  for (const path of [root, fileURLToPath(import.meta.url)]) {
    const rel = relative(resolve(tmpdir()), path);
    assert(rel && !rel.startsWith('..') && !isAbsolute(rel), 'Use an isolated temporary copy');
  }
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  assert.equal(process.env.DSH_AGENTS_HOME, join(root, 'agents'));
  assert.equal(process.env.HOME, join(root, 'native-install-home'));
  const checkpoint = { scenario: 'setup', assertion: 'none' };
  const abort = new AbortController();
  const timer = setTimeout(() => {
    run(ctx, root, abort.signal, config.watchOff === true, checkpoint).then(result => ({ passed: true, ...result }), error => {
      // Only this copied fixture's bounded line number may leave the private result.
      const frame = String(error.stack ?? '').split('\n').filter(line => line.includes(fileURLToPath(import.meta.url)) || line.includes(import.meta.url)).at(-1);
      const line = Number(/:(\d+):\d+\)?$/.exec(frame ?? '')?.[1]);
      const location = Number.isInteger(line) && line >= 1 && line <= 10000 ? { fixture: 'skill-toggle-fixture.mjs', line } : undefined;
      return { passed: false, error: error.message, checkpoint, location };
    })
      .then(result => writeFile(join(root, 'skill-toggle-result.json'), JSON.stringify(result, null, 2))).catch(() => {});
  }, 1000);
  return () => { clearTimeout(timer); abort.abort(); };
}

async function hashes(directory) {
  const result = {};
  async function walk(path, prefix = '') {
    for (const entry of await readdir(path, { withFileTypes: true })) {
      const name = prefix + entry.name;
      if (entry.isDirectory()) await walk(join(path, entry.name), name + '/');
      else { assert(entry.isFile()); result[name] = createHash('sha256').update(await readFile(join(path, entry.name))).digest('hex'); }
    }
  }
  await walk(directory);
  return result;
}

async function run(ctx, root, signal, watchOff, checkpoint) {
  const checked = Object.assign((...args) => { checkpoint.assertion = 'truthy'; return assert(...args); },
    Object.fromEntries(['equal', 'notEqual', 'deepEqual', 'rejects'].map(name => [name, (...args) => {
      checkpoint.assertion = name; return assert[name](...args);
    }])));

  const project = join(root, 'toggle-project-' + randomUUID());
  await mkdir(join(project, '.git'), { recursive: true });
  const workspace = await ctx.workspaceRegistry.create(project);
  const created = await ctx.sessionController.create({ workspaceId: workspace.id, agentPreset: 'standard' });
  const resolved = await ctx.sessionController.resolveAgent(created.sessionId);
  if ('error' in resolved) throw resolved.error;
  let agent = resolved.agent;
  const options = { scope: agent, cwd: agent.session.header.cwd, signal };
  const invoke = (method, extra = {}) => { checkpoint.assertion = 'invoke'; return ctx.typertGateway.invoke({
    peer: ctx.get('connection')?.browserPeer ?? ctx.get('connection')?.operator,
    namespace: 'skillhub', method, args: { ...(method === 'getSkillDetail' ? {} : { agentId: agent.session.id }), ...extra }, signal,
  }); };
  const source = ctx.skillhub.getContext();
  const sourceRevision = source.sourceRevision;
  const detail = await invoke('getSkillDetail', { sourceRevision, namespace: process.env.DSH_FIXTURE_NAMESPACE ?? 'demo', slug: 'document-helper', version: '1.2.0' });
  const targets = await invoke('listInstallTargets', { detailToken: detail.detailToken });
  const target = targets.targets.find(item => item.scope === 'project'); checked(target);
  const operations = [];
  async function complete(receipt) {
    checkpoint.assertion = 'operation';
    checked(receipt); const deadline = Date.now() + 20000;
    while (Date.now() < deadline) {
      const operation = await invoke('getOperation', { operationId: receipt.operationId, hostInstanceId: receipt.context.hostInstanceId });
      if (operation.phase === 'completed') { operations.push({ kind: operation.kind, files: operation.files, discovery: operation.discovery }); return operation; }
      checked(!['failed', 'recovery-required', 'unknown'].includes(operation.phase), JSON.stringify(operation));
      await delay(50, undefined, { signal });
    }
    throw new Error('Operation timed out');
  }
  checkpoint.scenario = 'install';
  await complete(await invoke('startInstall', { input: { detailToken: detail.detailToken, targetToken: target.targetToken,
    hostInstanceId: targets.hostInstanceId, requestKey: randomUUID(), mode: 'new', initiatedFrom: 'plaza' } }));
  const directory = join(project, '.dsh/skills/document-helper');
  const before = await hashes(directory);
  const list = async () => (await invoke('listInstalled', { sourceRevision })).items.find(item => item.scope === 'project' && item.slug === 'document-helper');
  async function discovered(present) {
    checkpoint.assertion = 'discovery';
    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) {
      const snapshot = await ctx.skills.snapshot(options);
      const loaded = await ctx.skills.get('document-helper', options);
      if (snapshot.complete && snapshot.skills.some(item => item.name === 'document-helper') === present && !!loaded === present) return loaded;
      await delay(100, undefined, { signal });
    }
    throw new Error(`Full Host discovery did not become ${present}`);
  }
  checkpoint.scenario = 'initial-discovery';
  const loaded = await discovered(true); checked.equal(loaded.resourceBase.path, directory);
  async function mutate(action, item, version = null) {
    const prepared = await invoke('beginConversationManagement', { sourceRevision, itemKey: item.itemKey, action, version, requestKey: randomUUID() });
    checked.equal(prepared.status, 'awaiting-confirmation', JSON.stringify(prepared));
    const receipt = await invoke('confirmConversationManagement', { confirmationRef: prepared.confirmationRef, confirmed: true });
    checked.equal(receipt.status, 'accepted'); return complete(receipt.operation);
  }
  const original = await list();
  if (watchOff) {
    checkpoint.scenario = 'watch-off-disable';
    await mutate('disable', original);
    checkpoint.scenario = 'watch-off-diagnosis';
    const selected = await list();
    const diagnosis = await invoke('checkAvailability', { sourceRevision, itemKey: selected.itemKey });
    checked.equal(diagnosis.installationState, 'disabled');
    checked.equal(diagnosis.integrity, 'verified');
    checked.equal((await invoke('prepareSkillUse', { sourceRevision, itemKey: selected.itemKey })).reason, 'installation-disabled');
    checked.equal(operations.at(-1).discovery, 'unconfirmed');
    const snapshot = await ctx.skills.snapshot(options);
    const cached = await ctx.skills.get('document-helper', options);
    checkpoint.scenario = 'watch-off-uninstall';
    await mutate('uninstall', selected);
    return { operations, watchOff: true, diagnosis: { installationState: diagnosis.installationState,
      discovery: diagnosis.discovery, reason: diagnosis.reason },
      snapshotComplete: snapshot.complete, snapshotContainsName: snapshot.skills.some(item => item.name === 'document-helper'),
      loadedAfterDisable: !!cached, noInstantRevocationClaim: true };
  }
  checkpoint.scenario = 'cancel';
  const cancelled = await invoke('beginConversationManagement', { sourceRevision, itemKey: original.itemKey, action: 'disable', version: null, requestKey: randomUUID() });
  checked.equal((await invoke('confirmConversationManagement', { confirmationRef: cancelled.confirmationRef, confirmed: false })).status, 'cancelled');
  checked.deepEqual(await hashes(directory), before);
  checkpoint.scenario = 'disable';
  await mutate('disable', original);
  await discovered(false);
  let item = await list(); checked.equal(item.installationState, 'disabled'); checked.equal(item.state, 'present');
  checked.equal(item.locationKey, original.locationKey); checked.notEqual(item.itemKey, original.itemKey);
  checkpoint.scenario = 'disabled-files';
  const disabled = await hashes(directory);
  checked.equal(disabled['SKILL.md.disabled'], before['SKILL.md']); checked(!('SKILL.md' in disabled));
  const canonical = { ...disabled, 'SKILL.md': disabled['SKILL.md.disabled'] }; delete canonical['SKILL.md.disabled'];
  checked.deepEqual(canonical, before);
  checked.equal(await readFile(join(loaded.resourceBase.path, 'references/note.md'), 'utf8'), 'Native installation resource');
  checkpoint.scenario = 'disabled-use';
  await checked.rejects(invoke('prepareSkillUse', { sourceRevision, itemKey: original.itemKey }));
  checked.equal((await invoke('prepareSkillUse', { sourceRevision, itemKey: item.itemKey })).reason, 'installation-disabled');
  checkpoint.scenario = 'enable';
  await mutate('enable', item); await discovered(true); checked.deepEqual(await hashes(directory), before);
  checkpoint.scenario = 'disable-again';
  item = await list(); await mutate('disable', item); await discovered(false);
  checkpoint.scenario = 'disabled-upgrade';
  item = await list(); await mutate('upgrade', item, '1.3.0');
  item = await list(); checked.equal(item.installationState, 'disabled'); checked.equal(item.version, '1.3.0');
  checked.equal(await readFile(join(directory, 'references/note.md'), 'utf8'), 'Native upgraded resource 1.3.0');
  checked(!('SKILL.md' in await hashes(directory))); await discovered(false);
  checkpoint.scenario = 'disabled-uninstall';
  await mutate('uninstall', item); checked.equal(await list(), undefined); await discovered(false);
  // Independent same-name user/project installations: only the project entry is disabled.
  const installScope = async scope => {
    const targets = await invoke('listInstallTargets', { detailToken: detail.detailToken });
    const selected = targets.targets.find(item => item.scope === scope); checked(selected);
    await complete(await invoke('startInstall', { input: { detailToken: detail.detailToken, targetToken: selected.targetToken,
      hostInstanceId: targets.hostInstanceId, requestKey: randomUUID(), mode: 'new', initiatedFrom: 'plaza' } }));
  };
  checkpoint.scenario = 'same-name-install';
  await installScope('user'); await installScope('project');
  await discovered(true);
  const userDirectory = join(root, 'home/skills/document-helper');
  const userBefore = await hashes(userDirectory);
  checkpoint.scenario = 'same-name-disable';
  await mutate('disable', await list());
  checkpoint.scenario = 'same-name-fallback';
  const deadline = Date.now() + 15000;
  let fallback;
  do {
    await ctx.skills.snapshot(options); fallback = await ctx.skills.get('document-helper', options);
    if (fallback?.resourceBase?.path === userDirectory) break;
    await delay(100, undefined, { signal });
  } while (Date.now() < deadline);
  const originalSessionFallback = fallback?.resourceBase?.path === userDirectory;
  const originalSelected = await list();
  const originalDiagnosis = await invoke('checkAvailability', { sourceRevision, itemKey: originalSelected.itemKey });
  if (!originalSessionFallback) {
    checked.equal(originalDiagnosis.installationState, 'disabled');
    checked.equal(originalDiagnosis.discovery, 'unknown');
    const fresh = await ctx.sessionController.create({ workspaceId: workspace.id, agentPreset: 'standard' });
    const current = await ctx.sessionController.resolveAgent(fresh.sessionId);
    if ('error' in current) throw current.error;
    agent = current.agent; options.scope = agent;
    await ctx.skills.snapshot(options); fallback = await ctx.skills.get('document-helper', options);
  }
  checked.equal(fallback?.resourceBase?.path, userDirectory);
  checkpoint.scenario = 'same-name-diagnosis';
  const selected = await list();
  const diagnosis = await invoke('checkAvailability', { sourceRevision, itemKey: selected.itemKey });
  checked.equal(diagnosis.installationState, 'disabled'); checked.equal(diagnosis.discovery, 'shadowed');
  checked.equal(diagnosis.reason, 'same-name-winner'); checked.equal(diagnosis.name, 'document-helper');
  checked.deepEqual(await hashes(userDirectory), userBefore);
  checked.equal((await invoke('prepareSkillUse', { sourceRevision, itemKey: selected.itemKey })).reason, 'installation-disabled');
  checkpoint.scenario = 'cleanup';
  await mutate('uninstall', selected);
  const userItem = (await invoke('listInstalled', { sourceRevision })).items.find(item => item.scope === 'user' && item.slug === 'document-helper');
  await mutate('uninstall', userItem);
  return { operations, originalSessionFallback, originalSessionDiscovery: originalDiagnosis.discovery, newSessionRequired: !originalSessionFallback, sameNameFallbackObserved: true, fallbackUntouched: true, cancellation: true, oldReferenceRejected: true, resourcePathsPreserved: true,
    canonicalDigestsPreserved: true, disabledUpgrade: true, disabledUninstall: true, fullHostSnapshotAndGet: true,
    confirmation: 'trusted Remote automation; not a human browser or model test' };
}
