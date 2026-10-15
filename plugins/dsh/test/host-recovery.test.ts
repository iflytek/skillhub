import { expect, test } from 'bun:test';
import { runtimePackageBaseline } from '../src/host/runtime-compatibility.js';
import { realpath, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

test('compiled Host recovery binds historical targets to the current session and policy', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-host-recovery-')));
  try {
    const child = Bun.spawn(['node', '--input-type=module', '-e', `
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, stat, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { Context } from '@deepseek-ai/cordis';
import { fileURLToPath } from 'node:url';
import SkillHubService from '@astron-team/skillhub-dsh';
import { OperationStore, operationRequestDigest, createInstallTransactionObserver, openNativeRuntime,
  InventoryStore, snapshotSkillDirectory } from './lib/local-core.js';
const cwd = process.env.HOME;
await mkdir(cwd, { recursive: true });
const store = await OperationStore.open(join(process.env.DSH_HOME, 'skillhub/operations'));
async function seed(root) {
  const operation = await store.accept({ ownerId: 'owner', hostInstanceId: store.hostInstanceId, mode: 'new',
    requestKey: randomUUID(), requestDigest: operationRequestDigest({ root }) }, async () => ({
    kind: 'install', namespace: 'team', slug: 'writer', version: '1', targetLabel: 'User', targetPaths: [join(root, 'writer')],
    context: { sourceIdentity: 'https://historical.example', sourceLabel: 'Old registry', sourceRevision: randomUUID(),
      hostLabel: 'Host', initiatedFrom: 'plaza' }
  }));
  await store.update(operation.operationId, operation.ownerId, operation.revision, { ...operation.operation.status, phase: 'staging' });
  const observer = await createInstallTransactionObserver(store, operation.operationId, operation.ownerId);
  await observer.beforeStaging({ canonicalSkillDir: join(root, 'writer'), rootDir: root,
    stagingDir: join(root, '.writer.install-' + randomUUID()) });
  return operation;
}
const valid = await seed(join(process.env.DSH_HOME, 'skills'));
const foreign = await seed(join(cwd, 'unrelated', 'skills'));
const ctx = new Context();
let mode = 'read-only';
const fs = { sandboxMode: 'workspace-write', resolve: async path => path, processPath: path => path, processPathFromHostPath: path => path };
const agent = { session: { id: 'owner', header: { cwd } }, ctx: { get: name => name === 'fs' ? fs
  : name === 'sandboxPolicy' ? { resolve: () => ({ mode, workspaceRoot: cwd }) } : undefined } };
const removeAgents = ctx.provide('agents', { get: id => id === 'owner' ? agent : undefined });
const removeSessions = ctx.provide('sessions', {});
const fiber = ctx.plugin(SkillHubService);
await fiber;
const deadline = Date.now() + 5000;
while (ctx.skillhub.getLocalRuntimeStatus()?.state !== 'ready' && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 10));
assert.equal(ctx.skillhub.getLocalRuntimeStatus().state, 'ready');
assert.equal(ctx.skillhub.getLocalRuntimeStatus().pendingOperations, 2);
assert.equal(ctx.skillhub.getCompatibility().localWritesAllowed, false);
await assert.rejects(ctx.skillhub.recoverInstallationOperation('owner', valid.operationId), /baseline could not be verified/);
// This is a bare Cordis fixture, not a real DSH Loader. Supply its package services explicitly.
const packageFixtures = new Map();
for (const [name, version] of Object.entries(${JSON.stringify(runtimePackageBaseline)})) {
  const directory = join(cwd, 'package-fixtures', name);
  await mkdir(directory, { recursive: true });
  const manifestPath = join(directory, 'package.json');
  await writeFile(manifestPath, JSON.stringify({ name, version }));
  packageFixtures.set(name, { name, version, manifestPath });
}
const removePackages = ctx.provide('pluginPackages', { packageOf: name => packageFixtures.get(name) });
const removeClients = ctx.provide('clientModules', { clientPath: name => fileURLToPath(import.meta.resolve(name + '/client')) });
assert.equal(ctx.skillhub.getCompatibility().localWritesAllowed, true);
await assert.rejects(ctx.skillhub.recoverInstallationOperation('foreign', valid.operationId), /context unavailable/);
await assert.rejects(ctx.skillhub.recoverInstallationOperation('owner', valid.operationId), /target is unavailable/);
assert.equal((await store.get(valid.operationId, 'owner')).operation.status.phase, 'staging');
mode = 'danger-full-access';
await assert.rejects(ctx.skillhub.recoverInstallationOperation('owner', foreign.operationId), /target is unavailable/);
assert.equal((await ctx.skillhub.recoverInstallationOperation('owner', valid.operationId)).outcome, 'restored');
assert.equal(ctx.skillhub.getLocalRuntimeStatus().pendingOperations, 1);
assert.equal((await store.get(valid.operationId, 'owner')).operation.status.recovery, 'restored');
assert.equal((await store.get(foreign.operationId, 'owner')).operation.status.phase, 'staging');
await assert.rejects(stat(join(process.env.DSH_HOME, 'skills')), { code: 'ENOENT' });
assert.equal(await ctx.skillhub.recoverInstallationOperation('owner', valid.operationId), undefined);
const skillRoot = join(process.env.DSH_HOME, 'skills');
const skillPath = join(skillRoot, 'removal-fixture');
await mkdir(join(skillPath, '.skillhub'), { recursive: true });
await writeFile(join(skillPath, 'SKILL.md'), '# Removal fixture');
const snapshot = await snapshotSkillDirectory(skillPath);
const identity = { registry: 'https://historical.example', namespace: 'team', slug: 'removal-fixture', version: '1' };
const target = { agent: 'dsh', rootDir: skillRoot, installDir: skillPath, installedAt: '2026-10-14' };
await writeFile(join(skillPath, '.skillhub/metadata.json'), JSON.stringify({ ...identity, ...snapshot, agent: 'dsh', installedAt: target.installedAt }));
const inventory = new InventoryStore(process.env.HOME);
await inventory.writeAtomic({ items: [{ ...identity, fingerprint: snapshot.fingerprint, targets: [target] }] });
const core = await openNativeRuntime({ stateDirectory: store.root, inventoryHome: process.env.HOME });
const baseline = await core.inspectRemoval(identity, { root: skillRoot, scope: 'user' });
assert.equal(baseline.status, 'ready');
const removal = await core.acceptRemoval({ hostInstanceId: core.hostInstanceId, ownerId: 'owner', requestKey: randomUUID(),
  mode: 'new', parameters: { planId: 'fixture' } }, async () => ({ kind: 'uninstall', namespace: identity.namespace,
  slug: identity.slug, version: identity.version, targetLabel: 'User', targetPaths: [skillPath],
  context: { sourceIdentity: identity.registry, sourceLabel: 'Old registry', sourceRevision: 'historical', hostLabel: 'Host', initiatedFrom: 'plaza' } }));
await assert.rejects(core.executeRemoval({ operationId: removal.operationId, ownerId: 'owner', baseline,
  authorize: async (paths, phase) => { if (phase === 'replace' && paths.includes(skillRoot)) throw new Error('interrupt before commit'); } }), /interrupt before commit/);
await assert.rejects(stat(skillPath), { code: 'ENOENT' });
mode = 'read-only';
await assert.rejects(ctx.skillhub.recoverInstallationOperation('owner', removal.operationId), /target is unavailable/);
assert.equal((await store.get(removal.operationId, 'owner')).operation.status.phase, 'recovery-required');
mode = 'danger-full-access';
assert.equal((await ctx.skillhub.recoverInstallationOperation('owner', removal.operationId)).outcome, 'restored');
assert.equal(await readFile(join(skillPath, 'SKILL.md'), 'utf8'), '# Removal fixture');
assert.equal((await inventory.read()).items[0].targets.length, 1);
assert.equal((await store.get(removal.operationId, 'owner')).operation.status.recovery, 'restored');
assert.equal(ctx.skillhub.getLocalRuntimeStatus().pendingOperations, 1);
await fiber.dispose();
await removeSessions();
await removeAgents();
await removeClients();
await removePackages();
console.log('host recovery verified');
`], { cwd: fileURLToPath(new URL('..', import.meta.url)), stdout: 'pipe', stderr: 'pipe', env: {
      ...process.env, HOME: join(root, 'os-home'), USERPROFILE: join(root, 'os-home'), DSH_HOME: join(root, 'dsh'),
    } });
    expect(await child.exited, await new Response(child.stderr).text()).toBe(0);
    expect(await new Response(child.stdout).text()).toBe('host recovery verified\n');
  } finally { await rm(root, { recursive: true, force: true }); }
}, 20_000);
