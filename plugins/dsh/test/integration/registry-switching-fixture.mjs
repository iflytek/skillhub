// Real Host observer: copied into an isolated root by registry-switching-host.mjs.
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
export const inject = ['loader', 'sessionController', 'workspaceRegistry', 'skillhub', 'typertGateway'];
export function apply(ctx, config) {
  const root = resolve(config.root);
  for (const path of [root, fileURLToPath(import.meta.url)]) {
    const rel = relative(resolve(tmpdir()), path);
    assert(rel && !rel.startsWith('..') && !isAbsolute(rel));
  }
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  assert.equal(process.env.HOME, join(root, 'native-install-home'));
  const controller = new AbortController();
  const checkpoint = { stage: 'setup' };
  const timer = setTimeout(() => run(ctx, root, config, controller.signal, checkpoint)
    .then(result => ({ passed: true, ...result }), error => ({ passed: false, checkpoint, error: String(error.message).replace(/token=[^\s&]+/g, 'token=[redacted]') }))
    .then(result => writeFile(config.output, JSON.stringify(result, null, 2))), 1000);
  return () => { clearTimeout(timer); controller.abort(); };
}
async function hashTree(root) {
  const files = {};
  async function walk(path, prefix = '') {
    for (const entry of await readdir(path, { withFileTypes: true })) {
      if (entry.isDirectory()) await walk(join(path, entry.name), prefix + entry.name + '/');
      else { assert(entry.isFile()); files[prefix + entry.name] = createHash('sha256').update(await readFile(join(path, entry.name))).digest('hex'); }
    }
  }
  await walk(root); return files;
}
async function run(ctx, root, config, signal, checkpoint) {
  const a = 'http://127.0.0.1:43219'; const b = 'http://127.0.0.1:43220';
  const namespace = 'registry-demo';
  const project = join(root, 'registry-project-' + randomUUID());
  await mkdir(join(project, '.git'), { recursive: true });
  const workspace = await ctx.workspaceRegistry.create(project);
  const created = await ctx.sessionController.create({ workspaceId: workspace.id, agentPreset: 'standard' });
  const title = '多来源切换验收（真实 Host，无模型）';
  await ctx.sessionController.rename({ sessionId: created.sessionId, title });
  const entries = [...ctx.loader.entries()].filter(entry => entry.options.name === '@astron-team/skillhub-dsh');
  assert.equal(entries.length, 1); const entry = entries[0];
  const original = structuredClone(entry.options.config);
  const invoke = (method, args = {}) => ctx.typertGateway.invoke({ peer: ctx.get('connection')?.browserPeer ?? ctx.get('connection')?.operator,
    namespace: 'skillhub', method, args: { ...(method === 'getSkillDetail' ? {} : { agentId: created.sessionId }), ...args }, signal });
  const source = () => ctx.skillhub.getContext().sourceRevision;
  const list = () => invoke('listInstalled', { sourceRevision: source() });
  const requests = async file => (await readFile(file, 'utf8').catch(error => { if (error.code === 'ENOENT') return ''; throw error; })).trim().split('\n').filter(Boolean).map(JSON.parse);
  const counts = async () => [(await requests(config.logA)).length, (await requests(config.logB)).length];
  const switchTo = async registryUrl => {
    await entry.update({ config: { ...original, registryUrl, savedRegistries: [{ name: '来源 A', url: a }, { name: '来源 B', url: b }] } });
    assert.equal(ctx.skillhub.getContext().registryUrl, registryUrl);
  };
  async function complete(receipt) {
    assert(receipt?.operationId); const end = Date.now() + 20000;
    while (Date.now() < end) {
      const operation = await invoke('getOperation', { operationId: receipt.operationId, hostInstanceId: receipt.context.hostInstanceId });
      if (operation.phase === 'completed') return operation;
      assert(!['failed', 'recovery-required', 'unknown'].includes(operation.phase), JSON.stringify(operation));
      await delay(75, undefined, { signal });
    }
    throw new Error('Operation deadline exceeded');
  }
  async function install(scope) {
    const detail = await invoke('getSkillDetail', { sourceRevision: source(), namespace, slug: 'document-helper', version: '1.2.0' });
    const targets = await invoke('listInstallTargets', { detailToken: detail.detailToken });
    const target = targets.targets.find(item => item.scope === scope); assert(target);
    return complete(await invoke('startInstall', { input: { detailToken: detail.detailToken, targetToken: target.targetToken,
      hostInstanceId: targets.hostInstanceId, requestKey: randomUUID(), mode: 'new', initiatedFrom: 'plaza' } }));
  }
  const itemAt = async registry => { const item = (await list()).items.find(item => item.registry === registry); assert(item); return item; };
  async function mutate(registry, action) {
    const item = await itemAt(registry);
    const plan = await invoke('beginConversationManagement', { sourceRevision: source(), itemKey: item.itemKey, action, version: null, requestKey: randomUUID() });
    assert.equal(plan.status, 'awaiting-confirmation', JSON.stringify(plan));
    const accepted = await invoke('confirmConversationManagement', { confirmationRef: plan.confirmationRef, confirmed: true });
    assert.equal(accepted.status, 'accepted'); return complete(accepted.operation);
  }
  checkpoint.stage = 'install-A-user'; await switchTo(a); await install('user');
  const userDirectory = join(root, 'home/skills/document-helper');
  const before = await hashTree(userDirectory);
  const oldRevision = source(); const oldItem = await itemAt(a);
  const stalePlan = await invoke('prepareUninstall', { sourceRevision: oldRevision, itemKey: oldItem.itemKey });
  assert.equal(stalePlan.status, 'ready');
  checkpoint.stage = 'switch-B'; await switchTo(b);
  assert.deepEqual(await hashTree(userDirectory), before);
  await assert.rejects(invoke('listInstalled', { sourceRevision: oldRevision }));
  await assert.rejects(invoke('executePlan', { input: { planId: stalePlan.planId, requestKey: randomUUID() } }));
  checkpoint.stage = 'old-source-local';
  const local = await itemAt(a); assert.equal(local.currentSource, false);
  const start = await counts();
  assert.equal((await invoke('getInstalledDetail', { sourceRevision: source(), itemKey: local.itemKey })).content.state, 'local-only');
  assert.equal((await invoke('checkUpdate', { sourceRevision: source(), itemKey: local.itemKey })).reason, 'other-source');
  assert.equal((await invoke('prepareUpgrade', { sourceRevision: source(), itemKey: local.itemKey })).reason, 'other-source');
  const useEnd = Date.now() + 15000; let use;
  do { use = await invoke('prepareSkillUse', { sourceRevision: source(), itemKey: local.itemKey }); if (use.status === 'ready') break; await delay(100, undefined, { signal }); } while (Date.now() < useEnd);
  assert.equal(use.status, 'ready', JSON.stringify(use));
  await mutate(a, 'disable'); assert.equal((await itemAt(a)).installationState, 'disabled');
  await mutate(a, 'enable'); assert.equal((await itemAt(a)).installationState, 'enabled');
  assert.deepEqual(await hashTree(userDirectory), before);
  assert.deepEqual(await counts(), start);
  checkpoint.stage = 'install-B-project'; await install('project');
  const all = await list(); assert.equal(all.items.length, 2);
  assert.equal(new Set(all.items.map(item => item.itemKey)).size, 2);
  assert.equal(all.items.filter(item => item.currentSource).length, 1);
  checkpoint.stage = 'current-source-scan';
  await writeFile(config.stateB, 'newer');
  const scanStart = await counts();
  const scan = await invoke('beginInstalledUpdateScan', { sourceRevision: source() }); assert.equal(scan.itemKeys.length, 1);
  const checked = await invoke('checkInstalledUpdateScan', { sourceRevision: source(), scanId: scan.scanId, itemKey: scan.itemKeys[0] });
  assert.equal(checked.state, 'upgradable', JSON.stringify(checked));
  await invoke('endInstalledUpdateScan', { sourceRevision: source(), scanId: scan.scanId });
  const scanEnd = await counts(); assert.equal(scanEnd[0], scanStart[0]); assert(scanEnd[1] > scanStart[1]);
  checkpoint.stage = 'slow-check-source-switch';
  await switchTo(a);
  const delayedRevision = source();
  const delayedScan = await invoke('beginInstalledUpdateScan', { sourceRevision: delayedRevision });
  assert.equal(delayedScan.itemKeys.length, 1);
  const resolvesBefore = (await requests(config.logA)).filter(item => item.path.endsWith('/resolve')).length;
  let settled = false;
  const late = invoke('checkInstalledUpdateScan', { sourceRevision: delayedRevision, scanId: delayedScan.scanId, itemKey: delayedScan.itemKeys[0] })
    .then(value => ({ value }), error => ({ error })).finally(() => { settled = true; });
  const requestDeadline = Date.now() + 10000;
  while ((await requests(config.logA)).filter(item => item.path.endsWith('/resolve')).length === resolvesBefore) {
    assert(Date.now() < requestDeadline, 'Delayed request never reached Registry A');
    await delay(20, undefined, { signal });
  }
  assert.equal(settled, false, 'The real HTTP response must still be pending at switch');
  await switchTo(b);
  const lateOutcome = await late;
  assert(lateOutcome.error, 'A late check must not return a result under B');
  assert.equal(lateOutcome.error.code, 'skillhub/context-stale');
  const freshScan = await invoke('beginInstalledUpdateScan', { sourceRevision: source() });
  assert.equal(freshScan.itemKeys.length, 1);
  await invoke('endInstalledUpdateScan', { sourceRevision: source(), scanId: freshScan.scanId });
  checkpoint.stage = 'accepted-upgrade-source-switch';
  const bItem = await itemAt(b);
  const upgrade = await invoke('prepareUpgrade', { sourceRevision: source(), itemKey: bItem.itemKey });
  assert.equal(upgrade.status, 'ready', JSON.stringify(upgrade));
  const upgradeRequest = { planId: upgrade.planId, requestKey: randomUUID() };
  const downloadsBefore = (await requests(config.logB)).filter(item => item.path === '/download').length;
  const accepted = await invoke('executePlan', { input: upgradeRequest });
  assert(accepted && accepted.phase !== 'completed', 'Upgrade must still be running');
  const downloadDeadline = Date.now() + 10000;
  while ((await requests(config.logB)).filter(item => item.path === '/download').length === downloadsBefore) {
    assert(Date.now() < downloadDeadline, 'Accepted upgrade never requested its download');
    await delay(20, undefined, { signal });
  }
  const inFlight = await invoke('getOperation', { operationId: accepted.operationId, hostInstanceId: accepted.context.hostInstanceId });
  assert.notEqual(inFlight.phase, 'completed', 'Download delay must keep write in flight at switch');
  await switchTo(a);
  const completed = await complete(accepted);
  assert.equal(completed.context.sourceIdentity, b);
  assert.equal((await itemAt(b)).version, '1.3.0');
  const duplicate = await invoke('executePlan', { input: upgradeRequest });
  assert.equal(duplicate.operationId, accepted.operationId, 'Accepted write retry must reuse original operation after switch');
  assert.equal((await requests(config.logB)).filter(item => item.path === '/download').length, downloadsBefore + 1);
  await switchTo(b);
  checkpoint.stage = 'disconnect-local-management'; await switchTo('');
  const disconnectedStart = await counts(); assert.equal((await list()).items.length, 2);
  await mutate(a, 'disable'); await mutate(a, 'enable');
  await mutate(a, 'uninstall'); assert.equal((await list()).items.length, 1);
  assert.deepEqual(await counts(), disconnectedStart);
  checkpoint.stage = 'restore-preview'; await switchTo(a); await install('user'); await switchTo(b);
  assert.equal((await list()).items.length, 2);
  return { sessionId: created.sessionId, title, project, modelCalled: false, sameCoordinatesAcrossSources: true,
    oldSourceLocalManagement: true, oldSourceUse: true, oldReferencesRejected: true,
    switchPreservesFiles: true, currentSourceOnlyScan: true, disconnectedManagement: true,
    noRemoteReadsDuringLocalManagement: true, slowResponseDiscarded: true, acceptedWriteSurvivesSwitch: true, acceptedWriteIdempotent: true, currentSource: b,
    notCovered: ['config-persistence-after-restart', 'browser'] };
}
