import { expect, test } from 'bun:test';
import { LocalRuntime } from '../src/host/local-runtime.js';
import type { NativeRuntime, NativeInstallExecution, NativeRecoveryResult, NativeOperation } from '../src/host/generated/native-contract.js';

function native(): NativeRuntime {
  return {
    observeInstallation: async () => false,
    hostInstanceId: 'fixture', listPending: async () => [], getOperation: async () => undefined,
    acceptInstall: async () => undefined,
    acceptUpgrade: async () => undefined,
    findPlanOperation: async () => undefined,
    acceptRemoval: async () => undefined,
    executeRemoval: async () => { throw new Error('Unexpected removal execution'); },
    executeUpgrade: async () => { throw new Error('Unexpected upgrade'); },
    assessUpdate: () => { throw new Error('Unexpected assessment'); },
    inspectUpgrade: async () => { throw new Error('Unexpected inspection'); },
    inspectRemoval: async () => { throw new Error('Unexpected removal inspection'); },
    listInstalled: async () => ({ items: [], complete: true, issues: [] }),
    executeInstall: async () => { throw new Error('Unexpected execution'); },
    recover: async () => { throw new Error('Unexpected recovery'); },
  };
}
const request: NativeInstallExecution = {
  operationId: 'operation', ownerId: 'owner', registry: 'https://registry.example', namespace: 'team', slug: 'writer',
  version: '1', versionId: 1, fingerprint: 'fingerprint', root: '/fixture', scope: 'project', authorize: async () => {}, validateStaged: async () => {},
};
const accepted: NativeOperation = {
  operationId: 'operation', revision: 0, kind: 'install', namespace: 'team', slug: 'writer', version: '1', targetLabel: 'Project',
  context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry', sourceRevision: 'source', hostLabel: 'Host',
    hostInstanceId: 'fixture', initiatedFrom: 'plaza', createdAt: '2026-10-14T00:00:00Z', updatedAt: '2026-10-14T00:00:00Z' },
  phase: 'accepted', files: 'pending', discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0,
};

test('an unverified Host blocks all new mutations but still queries and recovers original acceptance receipts', async () => {
  const core = native();
  const rejectNew = async (_input: unknown, prepare: () => Promise<unknown>) => { await prepare(); return accepted; };
  core.acceptInstall = rejectNew;
  core.acceptUpgrade = rejectNew;
  core.acceptRemoval = rejectNew;
  core.getOperation = async () => accepted;
  const runtime = new LocalRuntime(async () => core, () => { throw new Error('Host dependency mismatch'); });
  await runtime.start();
  const input = { ownerId: 'owner', hostInstanceId: 'fixture', requestKey: 'key', mode: 'new' as const, parameters: {} };
  let prepared = false;
  const prepare = async (): Promise<never> => { prepared = true; throw new Error('Must not prepare writes'); };
  await expect(runtime.submitInstall(input, prepare)).rejects.toThrow('Host dependency mismatch');
  await expect(runtime.submitUpgrade(input, prepare)).rejects.toThrow('Host dependency mismatch');
  await expect(runtime.submitRemoval(input, prepare)).rejects.toThrow('Host dependency mismatch');
  expect(prepared).toBe(false);
  expect(await runtime.getOperation('operation', 'owner')).toEqual(accepted);
  core.acceptInstall = async () => accepted;
  core.acceptUpgrade = async () => ({ ...accepted, kind: 'upgrade' });
  core.acceptRemoval = async () => ({ ...accepted, kind: 'uninstall' });
  expect(await runtime.submitInstall({ ...input, mode: 'retry' }, prepare)).toEqual(accepted);
  expect((await runtime.submitUpgrade({ ...input, mode: 'retry' }, prepare))?.kind).toBe('upgrade');
  expect((await runtime.submitRemoval({ ...input, mode: 'retry' }, prepare))?.kind).toBe('uninstall');
  expect(prepared).toBe(false);
  core.listPending = async () => [{ operationId: 'operation', ownerId: 'owner', kind: 'install',
    namespace: 'team', slug: 'writer', targetPaths: ['/fixture/writer'] }];
  await expect(runtime.recoverOperation('operation', 'owner', prepare)).rejects.toThrow('Host dependency mismatch');
  expect(prepared).toBe(false);
  expect(await runtime.recoverOperation('operation', 'foreign-owner', prepare)).toBeUndefined();
  await runtime.dispose();
});

test('removal acceptance is early, shutdown drains execution and retries do not replay', async () => {
  const core = native();
  const gate = Promise.withResolvers<NativeRecoveryResult>();
  const entered = Promise.withResolvers<void>();
  const removal: NativeOperation = { ...accepted, kind: 'remove-stale-record' };
  core.acceptRemoval = async (_input, prepare) => { await prepare(); return removal; };
  core.executeRemoval = async execution => {
    expect(execution.operationId).toBe(removal.operationId);
    expect(execution.ownerId).toBe('owner');
    entered.resolve(); return gate.promise;
  };
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  const input = { hostInstanceId: 'fixture', ownerId: 'owner', requestKey: 'request', mode: 'new' as const, parameters: { planId: 'plan' } };
  expect(await runtime.submitRemoval(input, async () => ({
    acceptance: { kind: 'remove-stale-record', namespace: 'team', slug: 'writer', version: '1', targetLabel: 'Project',
      targetPaths: ['/fixture/writer'], context: accepted.context },
    execution: { authorize: async () => {}, baseline: { status: 'ready', kind: 'remove-stale-record',
      inventoryItem: { registry: request.registry, namespace: 'team', slug: 'writer', version: '1', targets: [] },
      target: { scope: 'project', root: '/fixture', path: '/fixture/writer' } } },
  }))).toEqual(removal);
  await entered.promise;
  let disposed = false;
  const closing = runtime.dispose().then(() => { disposed = true; });
  await Promise.resolve();
  expect(disposed).toBe(false);
  gate.resolve({ operationId: removal.operationId, revision: 1, outcome: 'committed', reason: 'fixture' });
  await closing;
  expect(disposed).toBe(true);
  core.acceptRemoval = async () => removal;
  core.executeRemoval = async () => { throw new Error('Retry must not execute'); };
  const next = new LocalRuntime(async () => core);
  await next.start();
  expect(await next.submitRemoval(input, async () => { throw new Error('Expired plan must not be read'); })).toEqual(removal);
  await next.dispose();
});

test('upgrade acceptance is early, shutdown waits for execution, and durable retries do not prepare again', async () => {
  const core = native();
  const gate = Promise.withResolvers<NativeRecoveryResult>();
  const entered = Promise.withResolvers<void>();
  const upgrade: NativeOperation = { ...accepted, kind: 'upgrade' };
  let validated = false;
  core.acceptUpgrade = async (_input, prepare) => { await prepare(); validated = true; return upgrade; };
  core.executeUpgrade = async execution => {
    expect(execution.operationId).toBe(upgrade.operationId);
    expect(execution.ownerId).toBe('owner');
    entered.resolve(); return gate.promise;
  };
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  const input = { hostInstanceId: 'fixture', ownerId: 'owner', requestKey: 'request', mode: 'new' as const, parameters: { planId: 'plan' } };
  expect(await runtime.submitUpgrade(input, async () => ({
    acceptance: { namespace: 'team', slug: 'writer', version: '2', targetLabel: 'Project', targetPaths: ['/fixture/writer'], context: accepted.context },
    execution: { ...request, version: '2', baseline: { status: 'ready', inventoryItem: {
      registry: request.registry, namespace: request.namespace, slug: request.slug, version: '1', targets: [] }, targets: [] } },
  }))).toEqual(upgrade);
  await entered.promise;
  expect(validated).toBe(true);
  let disposed = false;
  const closing = runtime.dispose().then(() => { disposed = true; });
  await Promise.resolve();
  expect(disposed).toBe(false);
  gate.resolve({ operationId: upgrade.operationId, revision: 1, outcome: 'committed', reason: 'fixture' });
  await closing;
  expect(disposed).toBe(true);
  core.acceptUpgrade = async () => upgrade;
  core.executeUpgrade = async () => { throw new Error('Retry must not execute'); };
  const next = new LocalRuntime(async () => core);
  await next.start();
  expect(await next.submitUpgrade(input, async () => { throw new Error('Expired plan must not be read'); })).toEqual(upgrade);
  await next.dispose();
});

test('submission returns acceptance before completion and shutdown waits across the acceptance-execution boundary', async () => {
  const release = Promise.withResolvers<void>();
  const entered = Promise.withResolvers<void>();
  const completion = Promise.withResolvers<NativeRecoveryResult>();
  const core = native();
  core.acceptInstall = async (_request, validate) => {
    await validate(); entered.resolve(); await release.promise; return accepted;
  };
  let executions = 0;
  core.executeInstall = async execution => {
    expect(execution.operationId).toBe('operation');
    expect(execution.ownerId).toBe('owner');
    executions++; return completion.promise;
  };
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  const receipt = runtime.submitInstall({ hostInstanceId: 'fixture', ownerId: 'owner', requestKey: 'key', mode: 'new', parameters: {} },
    async () => ({ acceptance: { namespace: 'team', slug: 'writer', version: '1', targetLabel: 'Project',
      targetPaths: ['/fixture/writer'], context: accepted.context }, execution: request }));
  await entered.promise;
  const closing = runtime.dispose();
  release.resolve();
  expect(await receipt).toEqual(accepted);
  expect(executions).toBe(1);
  expect(runtime.getStatus().state).toBe('closing');
  completion.resolve({ operationId: 'operation', revision: 1, outcome: 'committed', reason: 'verified' });
  await closing;
  expect(runtime.getStatus().state).toBe('disposed');
});

test('durable retry returns its original receipt without preparing or scheduling another execution', async () => {
  const core = native();
  core.acceptInstall = async () => accepted;
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  expect(await runtime.submitInstall({ hostInstanceId: 'fixture', ownerId: 'owner', requestKey: 'key', mode: 'retry', parameters: {} },
    async () => { throw new Error('Expired reference must not be resolved'); })).toEqual(accepted);
  await runtime.dispose();
});

test('recovery scopes pending operations to their owner and refreshes the pending count after completion', async () => {
  const core = native();
  const pending = { operationId: 'operation', ownerId: 'owner', kind: 'install' as const,
    namespace: 'team', slug: 'writer', targetPaths: ['/fixture/writer'] };
  let remaining = [pending];
  core.listPending = async () => remaining;
  const authorize = async () => {};
  core.recover = async (id, owner, paths, policy) => {
    expect([id, owner, paths, policy]).toEqual(['operation', 'owner', ['/fixture/writer'], authorize]);
    remaining = [];
    return { operationId: id, revision: 2, outcome: 'restored', reason: 'verified' };
  };
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  expect(runtime.getStatus().pendingOperations).toBe(1);
  expect(await runtime.recoverOperation('operation', 'foreign', async () => { throw new Error('Must not prepare'); })).toBeUndefined();
  expect(await runtime.recoverOperation('operation', 'owner', async operation => {
    operation.targetPaths[0] = '/tampered'; // The callback receives a snapshot, not the trusted scan result.
    return authorize;
  })).toMatchObject({ outcome: 'restored' });
  expect(runtime.getStatus().pendingOperations).toBe(0);
  expect(pending.targetPaths).toEqual(['/fixture/writer']);
  await runtime.dispose();
});

test('closing drains recovery without reviving runtime state and rejects new recovery calls', async () => {
  const core = native();
  core.listPending = async () => [{ operationId: 'operation', ownerId: 'owner', kind: 'install',
    namespace: 'team', slug: 'writer', targetPaths: ['/fixture/writer'] }];
  const entered = Promise.withResolvers<void>();
  const completed = Promise.withResolvers<NativeRecoveryResult>();
  core.recover = async () => { entered.resolve(); return completed.promise; };
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  const recovery = runtime.recoverOperation('operation', 'owner', async () => async () => {});
  await entered.promise;
  const closing = runtime.dispose();
  await expect(runtime.recoverOperation('operation', 'owner', async () => async () => {})).rejects.toThrow('unavailable');
  expect(runtime.getStatus().state).toBe('closing');
  completed.resolve({ operationId: 'operation', revision: 2, outcome: 'restored', reason: 'verified' });
  await recovery;
  await closing;
  expect(runtime.getStatus()).toEqual({ state: 'disposed', hostInstanceId: null, pendingOperations: 0 });
});

test.each(['recovery', 'rescan'] as const)('recovery handles %s failure without losing pending state', async failure => {
  const core = native();
  let scans = 0;
  core.listPending = async () => {
    if (++scans === 3 && failure === 'rescan') throw new Error('Journal unreadable');
    return [{ operationId: 'operation', ownerId: 'owner', kind: 'install', namespace: 'team', slug: 'writer', targetPaths: ['/fixture/writer'] }];
  };
  core.recover = async () => {
    if (failure === 'recovery') throw new Error('Recovery denied');
    return { operationId: 'operation', revision: 2, outcome: 'unknown', reason: 'requires-verification' };
  };
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  const result = runtime.recoverOperation('operation', 'owner', async () => async () => {});
  if (failure === 'recovery') await expect(result).rejects.toThrow('Recovery denied');
  else expect((await result)?.outcome).toBe('unknown');
  expect(runtime.getStatus().pendingOperations).toBe(1);
  expect(runtime.getStatus().state).toBe(failure === 'rescan' ? 'failed' : 'ready');
  if (failure === 'rescan') await expect(runtime.getOperation('operation', 'owner')).rejects.toThrow('unavailable');
  await runtime.dispose();
});

test('closing waits for an in-flight installation and rejects new calls without cancelling the accepted work', async () => {
  const execution = Promise.withResolvers<NativeRecoveryResult>();
  const entered = Promise.withResolvers<void>();
  const core = native();
  let calls = 0;
  core.executeInstall = async () => { calls++; entered.resolve(); return execution.promise; };
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  const installation = runtime.executeInstall(request);
  await entered.promise;
  let closed = false;
  const close = runtime.dispose().then(() => { closed = true; });
  expect(runtime.getStatus().state).toBe('closing');
  await expect(runtime.executeInstall(request)).rejects.toThrow('unavailable');
  await expect(runtime.getOperation('operation', 'owner')).rejects.toThrow('unavailable');
  expect(closed).toBe(false);
  execution.resolve({ operationId: 'operation', revision: 1, outcome: 'committed', reason: 'verified' });
  expect((await installation).outcome).toBe('committed');
  await close;
  expect(runtime.getStatus().state).toBe('disposed');
  expect(calls).toBe(1);
});

test('initialization is single-flight and cannot revive the runtime after disposal', async () => {
  const opened = Promise.withResolvers<NativeRuntime>();
  let opens = 0;
  const runtime = new LocalRuntime(() => { opens++; return opened.promise; });
  const start = runtime.start();
  expect(runtime.start()).toBe(start);
  let closed = false;
  const close = runtime.dispose().then(() => { closed = true; });
  await Promise.resolve();
  expect(closed).toBe(false);
  opened.resolve(native());
  await Promise.all([start, close]);
  expect(runtime.getStatus().state).toBe('disposed');
  await runtime.start();
  expect(opens).toBe(1);
});

test('draining contains operation failures and waits for validation of already admitted requests', async () => {
  const acceptance = Promise.withResolvers<undefined>();
  const execution = Promise.withResolvers<NativeRecoveryResult>();
  const core = native();
  core.acceptInstall = async () => acceptance.promise;
  core.executeInstall = async () => execution.promise;
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  const accepted = runtime.acceptInstall({ hostInstanceId: 'fixture', ownerId: 'owner', requestKey: 'key', mode: 'new', parameters: {} },
    async () => { throw new Error('Unused validator'); });
  const failed = runtime.executeInstall(request);
  const rejection = failed.catch(error => error);
  const close = runtime.dispose();
  expect(runtime.dispose()).toBe(close);
  execution.reject(new Error('execution failed'));
  expect(await rejection).toMatchObject({ message: 'execution failed' });
  expect(runtime.getStatus().state).toBe('closing');
  acceptance.resolve(undefined);
  await accepted;
  await close;
  expect(runtime.getStatus().state).toBe('disposed');
});

test('startup unfinished operations are queryable as uncertain without mutating the journal or replaying work', async () => {
  const core = native();
  core.listPending = async () => [{ operationId: accepted.operationId, ownerId: 'owner', kind: 'install',
    namespace: 'team', slug: 'writer', targetPaths: ['/fixture/writer'] }];
  let original = { ...accepted };
  core.getOperation = async (id, owner) => owner === 'owner' ? { ...original, operationId: id } : undefined;
  const runtime = new LocalRuntime(async () => core);
  await runtime.start();
  for (const phase of ['accepted', 'staging', 'revalidating', 'committing', 'recovering'] as const) {
    original = { ...accepted, phase };
    expect(await runtime.getOperation('operation', 'owner')).toEqual({ ...original, phase: 'unknown', files: 'unknown', recovery: 'unknown' });
    expect(original.phase).toBe(phase);
    expect(await runtime.getOperation('new-operation', 'owner')).toEqual({ ...original, operationId: 'new-operation' });
  }
  expect(await runtime.getOperation('operation', 'foreign')).toBeUndefined();
  original = { ...accepted, phase: 'failed', files: 'failed', recovery: 'restored' };
  expect(await runtime.getOperation('operation', 'owner')).toEqual(original);
  original = { ...accepted, phase: 'completed', files: 'installed' };
  expect(await runtime.getOperation('operation', 'owner')).toEqual(original);
  await runtime.dispose();
});

test('post-acceptance failure emits a safe diagnostic without rejecting the receipt or replaying execution', async () => {
  for (const sinkThrows of [false, true]) {
    const core = native();
    core.acceptInstall = async (_input, prepare) => { await prepare(); return accepted; };
    const gate = Promise.withResolvers<NativeRecoveryResult>();
    let executions = 0;
    core.executeInstall = async () => { executions++; return gate.promise; };
    const diagnostics: unknown[] = [];
    const runtime = new LocalRuntime(async () => core, () => {}, diagnostic => {
      diagnostics.push(diagnostic);
      if (sinkThrows) throw new Error('Diagnostic sink unavailable');
    });
    await runtime.start();
    const input = { hostInstanceId: 'fixture', ownerId: 'owner', requestKey: 'request', mode: 'new' as const, parameters: {} };
    const receipt = runtime.submitInstall(input, async () => ({ acceptance: {
      namespace: 'team', slug: 'writer', version: '1', targetLabel: 'Project', targetPaths: ['/fixture/writer'], context: accepted.context,
    }, execution: request }));
    expect(await receipt).toEqual(accepted);
    gate.reject(Object.assign(new Error('secret path /private'), { code: 'EPERM', path: '/private' }));
    await runtime.dispose();
    expect(await receipt).toEqual(accepted);
    expect(executions).toBe(1);
    expect(diagnostics).toEqual([{ operationId: 'operation', kind: 'install', code: 'EPERM' }]);
    expect(runtime.getStatus().state).toBe('disposed');
  }
});

test('pre-acceptance errors reject the caller and do not emit accepted-operation diagnostics', async () => {
  const core = native();
  const failure = new Error('Prepare failed');
  core.acceptInstall = async () => { throw failure; };
  const diagnostics: unknown[] = [];
  const runtime = new LocalRuntime(async () => core, () => {}, value => diagnostics.push(value));
  await runtime.start();
  await expect(runtime.submitInstall({ hostInstanceId: 'fixture', ownerId: 'owner', requestKey: 'request', mode: 'new', parameters: {} },
    async () => { throw new Error('Unexpected preparation'); })).rejects.toBe(failure);
  await runtime.dispose();
  expect(diagnostics).toEqual([]);
});
