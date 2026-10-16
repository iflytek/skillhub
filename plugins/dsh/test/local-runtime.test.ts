import { LocalRuntime } from '../src/host/local-runtime.js';
import type { NativeCore } from '../src/host/generated/native-contract.js';
import { randomUUID } from 'node:crypto';
import { expect, test } from 'bun:test';
import { mkdtemp, realpath, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

test('compiled Host binds persistent state to DSH home, scans without replay and follows session dependency disposal', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-native-host-')));
  try {
    const child = Bun.spawn(['node', '--input-type=module', '-e', `
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';
import { OperationStore, operationRequestDigest, openNativeRuntime } from './lib/local-core.js';
const ctx = new Context();
const state = join(process.env.DSH_HOME, 'skillhub/operations');
async function ready(service, state = 'ready') {
  const deadline = Date.now() + 5000;
  while (Date.now() < deadline) {
    const status = service.getLocalRuntimeStatus();
    if (status?.state === state) return status;
    await new Promise(resolve => setTimeout(resolve, 10));
  }
  throw new Error('Local runtime did not reach ' + state);
}
let fiber = ctx.plugin(SkillHubService);
await fiber;
assert.equal(ctx.skillhub.getLocalRuntimeStatus(), null);
await assert.rejects(readFile(join(state, 'host.json')), { code: 'ENOENT' });
const removeSessions = ctx.provide('sessions', {});
const first = await ready(ctx.skillhub);
assert.equal(first.pendingOperations, 0);
const operations = await OperationStore.open(state);
assert.equal(first.hostInstanceId, operations.hostInstanceId);
const accepted = await operations.accept({ ownerId: 'fixture-session', hostInstanceId: operations.hostInstanceId, mode: 'new',
  requestKey: randomUUID(), requestDigest: operationRequestDigest({ version: '1' }) }, async () => ({
  kind: 'install', namespace: 'team', slug: 'pending', version: '1', targetLabel: 'Fixture', targetPaths: [join(process.env.DSH_HOME, 'skills/pending')],
  context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Fixture', sourceRevision: randomUUID(), hostLabel: 'Host', initiatedFrom: 'plaza' }
}));
await fiber.dispose();
fiber = ctx.plugin(SkillHubService);
await fiber;
const second = await ready(ctx.skillhub);
assert.equal(second.hostInstanceId, first.hostInstanceId);
assert.equal(second.pendingOperations, 1);
const native = await openNativeRuntime({ stateDirectory: state, inventoryHome: process.env.HOME });
assert.equal((await native.listPending())[0].operationId, accepted.operationId);
assert.equal((await operations.get(accepted.operationId, accepted.ownerId)).operation.status.phase, 'accepted');
const prior = await ctx.skillhub.localRuntime.getOperation(accepted.operationId, accepted.ownerId);
assert.equal(prior.phase, 'unknown');
assert.equal(prior.recovery, 'unknown');
assert.equal((await operations.get(accepted.operationId, accepted.ownerId)).operation.status.phase, 'accepted');
await assert.rejects(readFile(join(process.env.DSH_HOME, 'skills/pending/SKILL.md')), { code: 'ENOENT' });
const service = ctx.skillhub;
await removeSessions();
await new Promise(resolve => setTimeout(resolve, 20));
assert.equal(service.getLocalRuntimeStatus(), null);
await fiber.dispose();
await writeFile(join(state, accepted.operationId + '.json'), 'invalid');
const removeAgain = ctx.provide('sessions', {});
fiber = ctx.plugin(SkillHubService);
await fiber;
await ready(ctx.skillhub, 'failed');
assert.equal(ctx.skillhub.getStatus(), 'SkillHub: connected');
await fiber.dispose();
await removeAgain();
console.log('native lifecycle verified');
`], { cwd: fileURLToPath(new URL('..', import.meta.url)), stdout: 'pipe', stderr: 'pipe', env: {
      ...process.env, HOME: join(root, 'os-home'), USERPROFILE: join(root, 'os-home'), DSH_HOME: join(root, 'custom-dsh'),
    } });
    expect(await child.exited, await new Response(child.stderr).text()).toBe(0);
    expect(await new Response(child.stdout).text()).toBe('native lifecycle verified\n');
  } finally { await rm(root, { recursive: true, force: true }); }
}, 20_000);


test('recovery of a real accepted-only journal clears the LocalRuntime management blocker', async () => {
  // Recovery deliberately rejects ancestor aliases; persist the same canonical path the Host accepts.
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-early-recovery-host-')));
  const core: NativeCore = await import(new URL('../lib/local-core.js', import.meta.url).href);
  const options = { stateDirectory: join(root, 'operations'), inventoryHome: join(root, 'inventory') };
  let runtime: LocalRuntime | undefined;
  try {
    const original = await core.openNativeRuntime(options);
    const ownerId = 'fixture-owner';
    const accepted = await original.acceptInstall({ hostInstanceId: original.hostInstanceId, ownerId,
      requestKey: randomUUID(), mode: 'new', parameters: { version: '1' } }, async () => ({
      namespace: 'team', slug: 'pending', version: '1', targetLabel: '当前用户', targetPaths: [join(root, 'skills/pending')],
      context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry', sourceRevision: 'source',
        hostLabel: 'Fixture', initiatedFrom: 'plaza' },
    }));
    if (!accepted) throw new Error('Expected persisted acceptance');
    let baselineChecks = 0;
    // Exercise the actual journal/recovery core with an explicitly injected test-only Host baseline.
    // The compiled Host lifecycle test above stays read-only; its fake sessions service is not writable.
    runtime = new LocalRuntime(() => core.openNativeRuntime(options), () => { baselineChecks++; });
    await runtime.start();
    expect(runtime.getStatus().pendingOperations).toBe(1);
    expect((await runtime.getOperation(accepted.operationId, ownerId))?.phase).toBe('unknown');
    const recovered = await runtime.recoverOperation(accepted.operationId, ownerId, async operation => {
      expect(operation.targetPaths).toEqual([join(root, 'skills/pending')]);
      return async () => {};
    });
    expect(recovered?.outcome).toBe('restored');
    expect(baselineChecks).toBe(1);
    expect(runtime.getStatus().pendingOperations).toBe(0);
    expect(await runtime.getOperation(accepted.operationId, ownerId)).toMatchObject({ phase: 'failed', recovery: 'not-needed' });
    expect(await original.listPending()).toEqual([]);
  } finally {
    await runtime?.dispose();
    await rm(root, { recursive: true, force: true });
  }
});
