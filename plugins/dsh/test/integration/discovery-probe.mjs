// Copy outside the package to a temporary directory before loading as a DSH Host observer.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, realpath, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { createRequire } from 'node:module';
import { basename, isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

export const inject = ['sessionController', 'workspaceRegistry', 'skills', 'tools', 'sessions', 'typertGateway', 'skillhub'];

export function apply(ctx, config) {
  const moduleRel = relative(resolve(tmpdir()), fileURLToPath(import.meta.url));
  assert(moduleRel && !moduleRel.startsWith('..') && !isAbsolute(moduleRel),
    'Copy discovery-probe.mjs outside the plugin package into a temporary directory before loading');
  const root = resolve(config.root);
  const rel = relative(resolve(tmpdir()), root);
  assert(rel && !rel.startsWith('..') && !isAbsolute(rel), 'Probe root must be an isolated temporary directory');
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  assert.equal(process.env.DSH_AGENTS_HOME, join(root, 'agents'));
  assert.equal(process.env.HOME, join(root, 'native-install-home'), 'Installation probe requires isolated CLI inventory home');
  if (process.env.DSH_PROBE_SKIP_DISCOVERY === '1') return;
  const abort = new AbortController();
  // Do not make Profile activation depend on Agent composition completing.
  const timer = setTimeout(() => {
    run(ctx, root, abort.signal).then(
      result => writeFile(join(root, 'discovery-report.json'), JSON.stringify({ passed: true, ...result }, null, 2)),
      error => writeFile(join(root, 'discovery-report.json'), JSON.stringify({ passed: false, error: error.message }, null, 2)),
    ).catch(() => undefined);
  }, 1000);
  return () => { clearTimeout(timer); abort.abort(); };
}

async function fixture(root, name) {
  const path = join(root, name);
  await mkdir(join(path, 'references'), { recursive: true });
  await writeFile(join(path, 'SKILL.md'), `---\nname: ${name}\ndescription: Isolated SkillHub M0 fixture.\n---\n\nRead references/note.md. M0 fixture.\n`);
  await writeFile(join(path, 'references/note.md'), 'M0 resource');
  return path;
}

async function run(ctx, root, signal) {
  const runtimeRequire = createRequire(await realpath(join(root, 'node_modules/@deepseek-ai/dsh/package.json')));
  const baseRequire = createRequire(runtimeRequire.resolve('@deepseek-ai/dsh-base/package.json'));
  const { defineTool } = await import(pathToFileURL(baseRequire.resolve('@deepseek-ai/dsh-tools')).href);
  const { setSandboxMode } = await import(pathToFileURL(baseRequire.resolve('@deepseek-ai/dsh-sandbox-policy')).href);
  const project = join(root, 'project');
  const otherProject = join(root, 'other-project');
  const skills = join(project, '.dsh/skills');
  await mkdir(otherProject, { recursive: true });
  await fixture(join(root, 'home/skills'), 'm0-user-probe');
  const projectBundle = await fixture(skills, 'm0-project-probe');
  const liveBundle = join(skills, 'm0-live-probe');
  await rm(liveBundle, { force: true, recursive: true });

  async function optionsFor(directory) {
    const workspace = await ctx.workspaceRegistry.create(directory);
    const created = await ctx.sessionController.create({ workspaceId: workspace.id, agentPreset: 'standard' });
    const resolved = await ctx.sessionController.resolveAgent(created.sessionId);
    if ('error' in resolved) throw resolved.error;
    assert.equal(resolved.agent.session.header.cwd, workspace.path);
    return { scope: resolved.agent, cwd: resolved.agent.session.header.cwd, signal };
  }

  const options = await optionsFor(project);
  const snapshot = await ctx.skills.snapshot(options);
  assert.equal(snapshot.complete, true);
  const user = await ctx.skills.get('m0-user-probe', options);
  const local = await ctx.skills.get('m0-project-probe', options);
  assert(user?.content.includes('M0 fixture'));
  assert(local?.content.includes('M0 fixture'));
  assert.equal(local.resourceBase?.kind, 'directory');
  assert.equal(local.resourceBase.path, projectBundle);
  assert.equal(await readFile(join(local.resourceBase.path, 'references/note.md'), 'utf8'), 'M0 resource');
  const global = await ctx.skills.snapshot({ cwd: options.cwd, signal });
  assert(!global.skills.some(item => item.name === 'm0-project-probe'));
  const other = await ctx.skills.snapshot(await optionsFor(otherProject));
  assert(!other.skills.some(item => item.name === 'm0-project-probe'));
  assert(other.skills.some(item => item.name === 'm0-user-probe'));

  async function waitForLive(present) {
    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) {
      signal.throwIfAborted();
      const current = await ctx.skills.snapshot(options);
      if (current.complete && current.skills.some(item => item.name === 'm0-live-probe') === present) return;
      await delay(100, undefined, { signal });
    }
    throw new Error(`Live skill discovery did not become ${present ? 'present' : 'absent'}`);
  }
  try {
    await fixture(skills, 'm0-live-probe');
    await waitForLive(true);
  } finally {
    await rm(liveBundle, { force: true, recursive: true });
  }
  await waitForLive(false);
  let receivedContext = false;
  const removeTool = ctx.tools.register(defineTool({
    name: 'skillhub_m0_probe',
    description: 'Read-only isolated integration fixture; returns its input.',
    parameters: { query: { type: 'string', description: 'Probe text' } },
    output: {
      schema: { type: 'string' },
      render: (_args, value) => [{ type: 'text', text: value }],
      presentationMeta: (_args, value) => ({ schemaVersion: 1, query: value }),
    },
    async execute(args, exec) {
      assert.deepEqual(Object.keys(args), ['query']);
      assert.equal(exec.agent.session.id, options.scope.session.id);
      assert.equal(exec.callId, 'skillhub-m0-call');
      receivedContext = true;
      return args.query;
    },
  }));
  try {
    const result = await ctx.tools.execute({
      callId: 'skillhub-m0-call', name: 'skillhub_m0_probe', arguments: { query: 'M0 fixture' },
      agent: options.scope, signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]),
    });
    assert.equal(result.isError, false, JSON.stringify(result));
    assert(receivedContext);
  } finally {
    removeTool();
  }
  let nativeSkillHubSearch = false;
  let searchFixtureTitle;
  let scopedConversationDetail = false;
  let installationTargetsQueried = false;
  let installationTargetsRevalidated = false;
  let nativeInstallationCompleted = false;
  let nativeRecoveryCompleted = false;
  let nativeInstallToolCompleted = false;
  let installedProjectionVerified = false;
  let installFixtureTitle;
  let installFixtureWorkspace;
  let installFailureFixtureTitle;
  if (ctx.tools.get('astron_skillhub_search')) {
    const invoke = (args, agent = options.scope) => ctx.tools.execute({
      callId: 'skillhub-native-search-probe', name: 'astron_skillhub_search', arguments: args,
      agent, signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]),
    });
    const found = await invoke({ q: ' document ' });
    assert.equal(found.isError, false, JSON.stringify(found));
    assert.equal(found.value.schemaVersion, 1);
    assert.equal(found.value.query, 'document');
    assert.equal(found.value.items[0].slug, 'document-helper');
    assert.equal(typeof found.value.items[0].selectionRef, 'string');
    assert.deepEqual(found.meta, found.value);
    assert(!JSON.stringify(found.value).includes(options.cwd));
    const reference = found.value.items[0].selectionRef;
    const request = (method, agentId, extra = {}) => ctx.typertGateway.invoke({ namespace: 'skillhub', method,
      args: { agentId, selectionRef: reference, ...extra }, signal });
    const detail = await request('openConversationDetail', options.scope.session.id);
    assert.equal(detail.version, '1.2.0');
    assert.equal(typeof detail.selectionRef, 'string');
    assert.notEqual(detail.selectionRef, reference);
    const foreign = await optionsFor(otherProject);
    await assert.rejects(request('openConversationDetail', foreign.scope.session.id), error => error.code === 'skillhub/context-stale');
    await assert.rejects(request('acknowledgeConversationDetail', options.scope.session.id, { detailToken: 'forged' }), error => error.code === 'skillhub/context-stale');
    assert.equal(await request('acknowledgeConversationDetail', options.scope.session.id, { selectionRef: detail.selectionRef, detailToken: detail.detailToken }), true);
    const queryTargets = detailToken => ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'listInstallTargets',
      args: { agentId: options.scope.session.id, detailToken }, signal });
    const targets = await queryTargets(detail.detailToken);
    assert.deepEqual(targets.unavailable, [], JSON.stringify({
      sandboxMode: options.scope.ctx.get('fs')?.sandboxMode,
      hostMapped: options.scope.ctx.get('fs')?.processPathFromHostPath(options.cwd) === options.cwd,
    }));
    assert.deepEqual(targets.targets.map(item => item.scope).sort(), ['project', 'user']);
    for (const target of targets.targets) {
      assert.equal(typeof target.targetToken, 'string');
      assert.deepEqual(Object.keys(target).sort(), ['scope', 'targetToken']);
    }
    await assert.rejects(queryTargets('forged'), error => error.code === 'skillhub/context-stale');
    const projectTarget = targets.targets.find(item => item.scope === 'project');
    const resolveTarget = sessionId => ctx.skillhub.resolveInstallationTarget(sessionId, detail.detailToken, projectTarget.targetToken, signal);
    const checked = await resolveTarget(options.scope.session.id);
    assert.equal(checked.target.root, skills);
    assert.equal(checked.snapshot.version, detail.version);
    await assert.rejects(resolveTarget(foreign.scope.session.id), error => error.code === 'skillhub/context-stale');
    setSandboxMode(options.scope.session, 'read-only');
    await assert.rejects(resolveTarget(options.scope.session.id), /read-only/);
    const denied = await queryTargets(detail.detailToken);
    assert.deepEqual(denied.targets, []);
    assert.deepEqual(denied.unavailable.sort(), ['project', 'user']);
    setSandboxMode(options.scope.session, 'workspace-write');
    installationTargetsRevalidated = true;
    installationTargetsQueried = true;
    const installProject = join(root, 'install-' + randomUUID());
    await mkdir(installProject);
    const installSession = await optionsFor(installProject);
    const installAgentId = installSession.scope.session.id;
    const installTargets = await ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'listInstallTargets',
      args: { agentId: installAgentId, detailToken: detail.detailToken }, signal });
    const installTarget = installTargets.targets.find(item => item.scope === 'project');
    assert(installTarget);
    const submission = { detailToken: detail.detailToken, targetToken: installTarget.targetToken, requestKey: randomUUID(),
      hostInstanceId: ctx.skillhub.getLocalRuntimeStatus().hostInstanceId, mode: 'new', initiatedFrom: 'plaza' };
    assert.equal(installTargets.hostInstanceId, submission.hostInstanceId);
    const startInstall = input => ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'startInstall',
      args: { agentId: installAgentId, input }, signal });
    await assert.rejects(startInstall({ ...submission, path: '/untrusted' }), error => error.code === 'skillhub/invalid-installation-request');
    const receipt = await startInstall(submission);
    assert.equal(receipt.phase, 'accepted');
    const end = Date.now() + 15_000;
    let operation;
    while (Date.now() < end) {
      operation = await ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'getOperation',
        args: { agentId: installAgentId, operationId: receipt.operationId, hostInstanceId: submission.hostInstanceId }, signal });
      if (['completed', 'failed', 'recovery-required', 'unknown'].includes(operation.phase)) break;
      await delay(50, undefined, { signal });
    }
    assert.equal(operation.phase, 'completed', JSON.stringify(operation));
    assert.equal(operation.files, 'installed');
    assert.equal(await readFile(join(installProject, '.dsh/skills/document-helper/references/note.md'), 'utf8'), 'Native installation resource');
    const loaded = await ctx.skills.get('document-helper', installSession);
    assert(loaded?.content.includes('references/note.md'));
    assert.equal(loaded.resourceBase.path, join(installProject, '.dsh/skills/document-helper'));
    const listInstalled = agentId => ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'listInstalled',
      args: { agentId, sourceRevision: ctx.skillhub.getContext().sourceRevision }, signal });
    setSandboxMode(installSession.scope.session, 'read-only');
    const installed = await listInstalled(installAgentId);
    assert.equal(installed.complete, true, JSON.stringify(installed));
    assert(installed.items.some(item => item.scope === 'project' && item.slug === 'document-helper' && item.version === '1.2.0' && item.state === 'present'));
    assert(!JSON.stringify(installed).includes(installProject));
    const installedItem = installed.items.find(item => item.scope === 'project' && item.slug === 'document-helper');
    const installedDetailRequest = agentId => ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'getInstalledDetail',
      args: { agentId, sourceRevision: ctx.skillhub.getContext().sourceRevision, itemKey: installedItem.itemKey }, signal });
    const installedDetail = await installedDetailRequest(installAgentId);
    assert.equal(installedDetail.item.version, '1.2.0');
    assert.equal(installedDetail.content.state, 'ready');
    assert.equal(installedDetail.content.detail.version, '1.2.0');
    assert(!JSON.stringify(installedDetail).includes(installProject));
    await assert.rejects(installedDetailRequest(foreign.scope.session.id), error => error.code === 'skillhub/context-stale');
    const updateRequest = agentId => ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'checkUpdate',
      args: { agentId, sourceRevision: ctx.skillhub.getContext().sourceRevision, itemKey: installedItem.itemKey }, signal });
    const update = await updateRequest(installAgentId);
    assert.equal(update.state, 'current');
    assert.equal(update.currentVersion, '1.2.0');
    assert.equal(update.candidateVersion, '1.2.0');
    assert.equal(update.reason, 'same-content');
    await assert.rejects(updateRequest(foreign.scope.session.id), error => error.code === 'skillhub/context-stale');
    assert(!(await listInstalled(foreign.scope.session.id)).items.some(item => item.scope === 'project' && item.slug === 'document-helper'));
    setSandboxMode(installSession.scope.session, 'workspace-write');
    installedProjectionVerified = true;
    assert.equal((await startInstall({ ...submission, mode: 'retry' })).operationId, receipt.operationId);
    await assert.rejects(startInstall({ ...submission, initiatedFrom: 'conversation' }), error => error.code === 'skillhub/request-conflict');
    assert.equal(await ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'getOperation', args: {
      agentId: foreign.scope.session.id, operationId: receipt.operationId, hostInstanceId: submission.hostInstanceId }, signal }), null);
    await assert.rejects(ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'getOperation', args: {
      agentId: installAgentId, operationId: receipt.operationId, hostInstanceId: randomUUID() }, signal }), error => error.code === 'skillhub/context-stale');
    nativeInstallationCompleted = true;
    const toolProject = join(root, 'tool-install-' + randomUUID());
    installFixtureWorkspace = basename(toolProject);
    await mkdir(toolProject);
    const toolSession = await optionsFor(toolProject);
    const toolCallId = 'skillhub-install-probe-' + randomUUID();
    const toolArguments = { namespace: process.env.DSH_UPGRADE_FIXTURE === '1' ? 'upgrade-' + randomUUID() : 'demo', slug: 'document-helper', scope: 'project' };
    const invokeInstall = argumentsValue => ctx.tools.execute({ callId: toolCallId, name: 'astron_skillhub_install',
      arguments: argumentsValue, agent: toolSession.scope, signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) });
    assert.equal((await invokeInstall({ ...toolArguments, path: '/untrusted' })).isError, true);
    const toolResult = await invokeInstall(toolArguments);
    assert.equal(toolResult.isError, false, JSON.stringify(toolResult));
    assert.equal(toolResult.value.status, 'accepted');
    assert.equal(toolResult.value.toolCallId, toolCallId);
    assert.deepEqual(toolResult.meta, toolResult.value);
    const toolDeadline = Date.now() + 15000;
    let toolOperation;
    while (Date.now() < toolDeadline) {
      toolOperation = await ctx.skillhub.getInstallationOperation(toolSession.scope.session.id, toolResult.value.operationId);
      if (['completed', 'failed', 'recovery-required', 'unknown'].includes(toolOperation.phase)) break;
      await delay(50, undefined, { signal });
    }
    assert.equal(toolOperation.phase, 'completed', JSON.stringify(toolOperation));
    assert.equal(toolOperation.files, 'installed');
    assert.equal(await readFile(join(toolProject, '.dsh/skills/document-helper/references/note.md'), 'utf8'), 'Native installation resource');
    assert((await ctx.skills.get('document-helper', toolSession))?.content.includes('references/note.md'));
    const replay = await invokeInstall(toolArguments);
    assert.equal(replay.isError, false, JSON.stringify(replay));
    assert.equal(replay.value.operationId, toolResult.value.operationId);
    assert.deepEqual(replay.value, toolResult.value);
    const changedToolRequest = await invokeInstall({ ...toolArguments, scope: 'user' });
    assert.equal(changedToolRequest.isError, false, JSON.stringify(changedToolRequest));
    assert.equal(changedToolRequest.value.status, 'not-confirmed');
    assert.equal(changedToolRequest.value.code, 'REQUEST_CONFLICT');
    nativeInstallToolCompleted = true;
    if (process.env.DSH_UPGRADE_TWO_TARGETS === '1') {
      const userResult = await ctx.tools.execute({ callId: 'skillhub-user-install-' + randomUUID(), name: 'astron_skillhub_install',
        arguments: { ...toolArguments, scope: 'user' }, agent: toolSession.scope,
        signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) });
      assert.equal(userResult.isError, false, JSON.stringify(userResult));
      assert.equal(userResult.value.status, 'accepted');
      const deadline = Date.now() + 15000;
      let userOperation;
      while (Date.now() < deadline) {
        userOperation = await ctx.skillhub.getInstallationOperation(toolSession.scope.session.id, userResult.value.operationId);
        if (['completed', 'failed', 'recovery-required', 'unknown'].includes(userOperation.phase)) break;
        await delay(50, undefined, { signal });
      }
      assert.equal(userOperation.phase, 'completed', JSON.stringify(userOperation));
      assert.equal(await readFile(join(process.env.DSH_HOME, 'skills/document-helper/references/note.md'), 'utf8'), 'Native installation resource');
    }
    const installHistory = toolSession.scope.session;
    installFixtureTitle = `SkillHub install fixture ${installHistory.id.slice(-8)}`;
    await ctx.sessionController.rename({ sessionId: installHistory.id, title: installFixtureTitle });
    installHistory.append('turn/start', { turn: 1 });
    installHistory.append('step/start', { turn: 1, step: 1 });
    installHistory.append('assistant/message', { turn: 1, step: 1, stream: [
      { type: 'tool-call-chunks', time0: Date.now(), index: 0, dt: [0], id: toolCallId,
        name: 'astron_skillhub_install', args: [JSON.stringify(toolArguments)] },
    ], message: { id: 'skillhub-install-fixture-assistant', role: 'assistant',
      source: { kind: 'model', provider: 'test-fixture', model: 'test-fixture' },
      content: [{ type: 'tool-call', id: toolCallId, name: 'astron_skillhub_install', arguments: JSON.stringify(toolArguments) }],
    } }, { surfaceOp: 'append' });
    installHistory.append('tool/call', { turn: 1, step: 1, callId: toolCallId,
      name: 'astron_skillhub_install', arguments: JSON.stringify(toolArguments) });
    installHistory.append('tool/result', { turn: 1, step: 1, message: {
      id: 'skillhub-install-fixture-result', role: 'tool', source: { kind: 'tool', callId: toolCallId },
      toolCallId, content: toolResult.content, isError: false,
    }, meta: toolResult.meta }, { surfaceOp: 'append' });
    installHistory.append('step/end', { turn: 1, step: 1 });
    installHistory.append('turn/end', { turn: 1, reason: { kind: 'completed' } });
    await ctx.sessions.flush(installHistory);
    const failureSession = await optionsFor(toolProject);
    const failureHistory = failureSession.scope.session;
    const failureCallId = 'skillhub-stale-install-' + randomUUID();
    const failureArguments = { selectionRef: randomUUID(), scope: 'project' };
    const failureResult = await ctx.tools.execute({ callId: failureCallId, name: 'astron_skillhub_install',
      arguments: failureArguments, agent: failureSession.scope, signal });
    assert.equal(failureResult.isError, false, JSON.stringify(failureResult));
    assert.equal(failureResult.value.status, 'not-confirmed');
    assert.equal(failureResult.value.code, 'CONTEXT_STALE');
    assert.equal(failureResult.value.operationId, undefined);
    assert.deepEqual(failureResult.meta, failureResult.value);
    installFailureFixtureTitle = `SkillHub failed install fixture ${failureHistory.id.slice(-8)}`;
    await ctx.sessionController.rename({ sessionId: failureHistory.id, title: installFailureFixtureTitle });
    failureHistory.append('turn/start', { turn: 1 });
    failureHistory.append('step/start', { turn: 1, step: 1 });
    failureHistory.append('assistant/message', { turn: 1, step: 1, stream: [
      { type: 'tool-call-chunks', time0: Date.now(), index: 0, dt: [0], id: failureCallId,
        name: 'astron_skillhub_install', args: [JSON.stringify(failureArguments)] },
    ], message: { id: 'skillhub-install-failure-fixture-assistant', role: 'assistant',
      source: { kind: 'model', provider: 'test-fixture', model: 'test-fixture' },
      content: [{ type: 'tool-call', id: failureCallId, name: 'astron_skillhub_install', arguments: JSON.stringify(failureArguments) }],
    } }, { surfaceOp: 'append' });
    failureHistory.append('tool/call', { turn: 1, step: 1, callId: failureCallId,
      name: 'astron_skillhub_install', arguments: JSON.stringify(failureArguments) });
    failureHistory.append('tool/result', { turn: 1, step: 1, message: {
      id: 'skillhub-install-failure-fixture-result', role: 'tool', source: { kind: 'tool', callId: failureCallId },
      toolCallId: failureCallId, content: failureResult.content, isError: false,
    }, meta: failureResult.meta }, { surfaceOp: 'append' });
    failureHistory.append('step/end', { turn: 1, step: 1 });
    failureHistory.append('turn/end', { turn: 1, reason: { kind: 'completed' } });
    await ctx.sessions.flush(failureHistory);


    const profileRequire = createRequire(join(root, 'home/profiles/skillhub-m0/package.json'));
    const core = await import(new URL('./local-core.js', pathToFileURL(profileRequire.resolve('@astron-team/skillhub-dsh'))).href);
    const recoveryProject = join(root, 'recovery-' + randomUUID());
    await mkdir(recoveryProject);
    const recoverySession = await optionsFor(recoveryProject);
    const recoveryAgentId = recoverySession.scope.session.id;
    const recoveryRoot = join(recoveryProject, '.dsh/skills');
    const operations = await core.OperationStore.open(join(root, 'home/skillhub/operations'));
    const accepted = await operations.accept({ hostInstanceId: operations.hostInstanceId, ownerId: recoveryAgentId,
      requestKey: randomUUID(), requestDigest: core.operationRequestDigest({ scenario: 'recovery-probe' }), mode: 'new' }, async () => ({
      kind: 'install', namespace: detail.namespace, slug: detail.slug, version: detail.version, targetLabel: 'Project',
      targetPaths: [join(recoveryRoot, detail.slug)], context: { sourceIdentity: 'http://127.0.0.1:43219',
        sourceLabel: 'Fixture', sourceRevision: randomUUID(), hostLabel: 'DSH Host', initiatedFrom: 'plaza' },
    }));
    await operations.update(accepted.operationId, accepted.ownerId, accepted.revision, { ...accepted.operation.status, phase: 'staging' });
    const observer = await core.createInstallTransactionObserver(operations, accepted.operationId, accepted.ownerId);
    const release = await fetch('http://127.0.0.1:43219/api/cli/v1/skills/' + detail.namespace + '/' + detail.slug + '/resolve?version=' + detail.version).then(response => response.json());
    let interruptedBeforeMove = false;
    await assert.rejects(core.installSkill({ registry: 'http://127.0.0.1:43219', namespace: detail.namespace, slug: detail.slug,
      force: false, home: process.env.HOME, resolved: release.data,
      client: { downloadFromUrl: async () => fetch('http://127.0.0.1:43219/download') },
      targets: [{ agent: 'dsh', rootDir: recoveryRoot, scope: 'project', source: 'explicit' }],
      authorizeTargetMutation: async (_paths, phase) => { if (phase === 'cleanup') throw new Error('Retain isolated staging fixture'); },
      transactionObserver: { ...observer, beforeMove: async move => { await observer.beforeMove(move); interruptedBeforeMove = true; throw new Error('Injected pre-move interruption'); } },
    }));
    assert(interruptedBeforeMove, 'Recovery fixture must reach the intended interruption boundary');
    const interrupted = await operations.get(accepted.operationId, accepted.ownerId);
    const stage = interrupted.transaction.preparation.targets[0].stagingDir;
    assert((await stat(stage)).isDirectory());
    assert.equal(await ctx.skillhub.recoverInstallationOperation(foreign.scope.session.id, accepted.operationId), undefined);
    setSandboxMode(recoverySession.scope.session, 'read-only');
    await assert.rejects(ctx.skillhub.recoverInstallationOperation(recoveryAgentId, accepted.operationId), /target is unavailable/);
    assert((await stat(stage)).isDirectory());
    setSandboxMode(recoverySession.scope.session, 'workspace-write');
    assert.equal((await ctx.skillhub.recoverInstallationOperation(recoveryAgentId, accepted.operationId)).outcome, 'restored');
    await assert.rejects(stat(stage), { code: 'ENOENT' });
    await assert.rejects(stat(join(recoveryRoot, detail.slug)), { code: 'ENOENT' });
    assert.equal((await operations.get(accepted.operationId, accepted.ownerId)).operation.status.recovery, 'restored');
    nativeRecoveryCompleted = true;
    scopedConversationDetail = true;
    const invalid = await invoke({ q: 'document', path: '/untrusted' });
    assert.equal(invalid.isError, true);
    const missingContext = await ctx.tools.execute({
      callId: 'skillhub-no-agent-probe', name: 'astron_skillhub_search', arguments: { q: '' }, signal,
    });
    assert.equal(missingContext.isError, true);
    // Durable synthetic fixture: real tool output, no model invocation.
    const session = options.scope.session;
    searchFixtureTitle = `SkillHub search fixture ${session.id.slice(-8)}`;
    await ctx.sessionController.rename({ sessionId: session.id, title: searchFixtureTitle });
    session.append('turn/start', { turn: 1 });
    session.append('step/start', { turn: 1, step: 1 });
    session.append('assistant/message', { turn: 1, step: 1, stream: [
      { type: 'text-chunks', time0: Date.now(), index: 0, dt: [0], texts: ['SkillHub integration fixture — no model was called.'] },
      { type: 'tool-call-chunks', time0: Date.now(), index: 1, dt: [0], id: 'skillhub-native-search-probe',
        name: 'astron_skillhub_search', args: [JSON.stringify({ q: 'document' })] },
    ], message: {
      id: 'skillhub-fixture-assistant', role: 'assistant',
      source: { kind: 'model', provider: 'test-fixture', model: 'test-fixture' },
      content: [{ type: 'text', text: 'SkillHub integration fixture — no model was called.' },
        { type: 'tool-call', id: 'skillhub-native-search-probe', name: 'astron_skillhub_search', arguments: JSON.stringify({ q: 'document' }) }],
    } }, { surfaceOp: 'append' });
    session.append('tool/call', { turn: 1, step: 1, callId: 'skillhub-native-search-probe',
      name: 'astron_skillhub_search', arguments: JSON.stringify({ q: 'document' }) });
    session.append('tool/result', { turn: 1, step: 1, message: {
      id: 'skillhub-fixture-result', role: 'tool', source: { kind: 'tool', callId: 'skillhub-native-search-probe' },
      toolCallId: 'skillhub-native-search-probe', content: found.content, isError: false,
    }, meta: found.meta }, { surfaceOp: 'append' });
    session.append('step/end', { turn: 1, step: 1 });
    session.append('turn/end', { turn: 1, reason: { kind: 'completed' } });
    await ctx.sessions.flush(session);
    nativeSkillHubSearch = true;
  }
  if (process.env.DSH_UPGRADE_RECOVERY_FIXTURE === '1') {
    assert(installFixtureTitle && process.env.DSH_UPGRADE_FIXTURE === '1');
    const runtime = ctx.skillhub.localRuntime;
    const submit = runtime.submitUpgrade.bind(runtime);
    runtime.submitUpgrade = (request, prepare) => submit(request, async () => {
      const prepared = await prepare();
      assert(prepared.acceptance.namespace.startsWith('upgrade-'));
      const expectedTargets = process.env.DSH_UPGRADE_TWO_TARGETS === '1' ? 2 : 1;
      assert.equal(prepared.acceptance.targetPaths.length, expectedTargets);
      const authorize = prepared.execution.authorize;
      let replacements = 0;
      return { ...prepared, execution: { ...prepared.execution, authorize: async (paths, phase) => {
        await authorize(paths, phase);
        if (phase === 'replace' && ++replacements === expectedTargets * 2) {
          if (process.env.DSH_UPGRADE_CRASH_FIXTURE === '1') {
            await writeFile(join(root, 'upgrade-crash-ready.json'), JSON.stringify({ boundary: 'second-target-backed-up', targets: expectedTargets }));
            await new Promise(() => {}); // Coordinator kills this isolated Host while its transaction is active.
          }
          throw new Error('Isolated browser fixture: interrupt upgrade after backup');
        }
        if (phase === 'rollback') throw new Error('Isolated browser fixture: interrupt upgrade after backup');
      } } };
    });
  }
  if (process.env.DSH_REMOVAL_RECOVERY_FIXTURE === '1') {
    assert(installFixtureTitle && process.env.DSH_UPGRADE_FIXTURE === '1');
    const runtime = ctx.skillhub.localRuntime;
    const submit = runtime.submitRemoval.bind(runtime);
    let interrupted = false;
    runtime.submitRemoval = (request, prepare) => submit(request, async () => {
      const prepared = await prepare();
      assert(prepared.acceptance.namespace.startsWith('upgrade-'));
      const authorize = prepared.execution.authorize;
      return { ...prepared, execution: { ...prepared.execution, authorize: async (paths, phase, operationId) => {
        await authorize(paths, phase, operationId);
        if (!interrupted && phase === 'replace' && paths.includes(prepared.execution.baseline.target.root)) {
          interrupted = true;
          throw new Error('Isolated browser fixture: interrupt removal before inventory commit');
        }
      } } };
    });
  }
  return {
    nativeSkillHubSearch, scopedConversationDetail, installationTargetsQueried, installationTargetsRevalidated, nativeInstallationCompleted, nativeRecoveryCompleted, nativeInstallToolCompleted, installedProjectionVerified,
    ...(searchFixtureTitle ? { searchFixtureTitle } : {}),
    ...(installFixtureTitle ? { installFixtureTitle, installFixtureWorkspace, installFailureFixtureTitle } : {}),
    trustedSessionCwd: true, userAndProjectRead: true, resourceBasePreserved: true,
    projectScopeIsolated: true, globalScopeExcluded: true,
    liveAdditionObserved: true, liveRemovalObserved: true,
    nativeToolContextReceived: true,
  };
}
