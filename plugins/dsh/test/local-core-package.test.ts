import { expect, test } from 'bun:test';
import { createHash } from 'node:crypto';
import { realpath, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

test('published tarball core installs and reconciles under Node outside the repository', async () => {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-dsh-packed-core-')));
  const packageRoot = fileURLToPath(new URL('..', import.meta.url));
  const cliRequire = createRequire(new URL('../../../cli/package.json', import.meta.url));
  const zipSync = cliRequire('fflate').zipSync as (files: Record<string, Uint8Array>) => Uint8Array;
  const files = { 'SKILL.md': '# Packed native skill', 'assets/message.txt': 'Resource from packed core' };
  const aggregate = createHash('sha256');
  for (const [path, content] of Object.entries(files).sort(([left], [right]) => left < right ? -1 : 1)) {
    aggregate.update(`${path}:${createHash('sha256').update(content).digest('hex')}\n`);
  }
  try {
    const pack = Bun.spawn(['npm', 'pack', '--json', '--ignore-scripts', '--pack-destination', root], { cwd: packageRoot, stdout: 'pipe', stderr: 'pipe' });
    const packed = await new Response(pack.stdout).text();
    expect(await pack.exited, await new Response(pack.stderr).text()).toBe(0);
    const manifest = JSON.parse(packed) as Array<{ filename: string; files: Array<{ path: string }> }>;
    const packedPaths = new Set(manifest[0]!.files.map(file => file.path));
    const packageManifest = JSON.parse(await readFile(join(packageRoot, 'package.json'), 'utf8')) as {
      exports: Record<string, Record<string, string>>;
      dsh: { bundle: { patch: string } };
    };
    for (const entry of Object.values(packageManifest.exports)) {
      for (const path of Object.values(entry)) {
        expect(packedPaths.has(path.replace(/^\.\//, '')), `Missing exported artifact: ${path}`).toBe(true);
      }
    }
    expect(packedPaths.has(packageManifest.dsh.bundle.patch.replace(/^\.\//, ''))).toBe(true);
    expect(packedPaths.has('LICENSE')).toBe(true);
    for (const path of packedPaths) {
      expect(/^(?:package\.json|README(?:\.en)?\.md|LICENSE|cordis\.patch\.yml|lib\/)/.test(path),
        `Unexpected published file: ${path}`).toBe(true);
      expect(/(?:^|\/)(?:test|tests|fixtures|node_modules|\.env[^/]*|\.trellis)(?:\/|$)/.test(path),
        `Development-only file in package: ${path}`).toBe(false);
    }
    expect(manifest[0]!.files.some(file => file.path === 'lib/local-core.js')).toBe(true);
    expect(manifest[0]!.files.some(file => file.path === 'README.en.md')).toBe(true);
    expect(manifest[0]!.files.some(file => file.path.startsWith('node_modules/') || file.path.startsWith('cli/'))).toBe(false);
    const extract = Bun.spawn(['tar', '-xzf', join(root, manifest[0]!.filename), '-C', root], { stdout: 'pipe', stderr: 'pipe' });
    expect(await extract.exited, await new Response(extract.stderr).text()).toBe(0);
    await writeFile(join(root, 'fixture.json'), JSON.stringify({ fingerprint: `sha256:${aggregate.digest('hex')}`,
      zip: Buffer.from(zipSync(Object.fromEntries(Object.entries(files).map(([path, content]) => [path, new TextEncoder().encode(content)])))).toString('base64') }));
    await writeFile(join(root, 'probe.mjs'), `
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { OperationStore, operationRequestDigest, InventoryStore, installSkill, createInstallTransactionObserver, recoverInstallation } from './package/lib/local-core.js';
const root = process.cwd();
assert.equal(typeof globalThis.Bun, 'undefined');
assert.equal(process.env.PATH, root);
const fixture = JSON.parse(await readFile('fixture.json', 'utf8'));
const identity = { registry: 'https://registry.example', namespace: 'team', slug: 'packed' };
const operations = await OperationStore.open(join(root, 'state'));
const home = join(root, 'home');
const target = join(root, 'skills', 'packed');
const operation = await operations.accept({ ownerId: 'session', hostInstanceId: operations.hostInstanceId,
  requestKey: randomUUID(), requestDigest: operationRequestDigest({ version: '1.0.0' }), mode: 'new' }, async () => ({
  kind: 'install', namespace: 'team', slug: 'packed', version: '1.0.0', targetLabel: 'Fixture', targetPaths: [target],
  context: { sourceIdentity: identity.registry, sourceLabel: 'Fixture', sourceRevision: randomUUID(), hostLabel: 'Node', initiatedFrom: 'plaza' }
}));
await operations.update(operation.operationId, operation.ownerId, operation.revision, { ...operation.operation.status, phase: 'staging' });
globalThis.fetch = () => { throw new Error('No external request is permitted in the packed core probe'); };
await installSkill({ ...identity, home, force: false,
  targets: [{ agent: 'dsh', rootDir: join(root, 'skills'), scope: 'user', source: 'explicit' }],
  resolved: { namespace: 'team', slug: 'packed', version: '1.0.0', versionId: 1, fingerprint: fixture.fingerprint, downloadUrl: '' },
  client: { download: async () => new Response(Buffer.from(fixture.zip, 'base64')) },
  transactionObserver: await createInstallTransactionObserver(operations, operation.operationId, operation.ownerId)
});
const result = await recoverInstallation({ operations, operationId: operation.operationId, ownerId: operation.ownerId,
  inventory: new InventoryStore(home), trustedTargetPaths: [target] });
assert.equal(result.outcome, 'committed');
assert.equal(await readFile(join(target, 'assets/message.txt'), 'utf8'), 'Resource from packed core');
const reopened = await OperationStore.open(join(root, 'state'));
assert.equal((await reopened.get(operation.operationId, operation.ownerId)).operation.status.phase, 'completed');
assert.deepEqual(await reopened.listForRecovery(), []);
assert.equal((await readdir(root)).includes('node_modules'), false);
// A future journal is a downgrade fixture, not a supported new schema. The old
// runtime must reject it without changing installed resources or inventory.
const journalPath = join(root, 'state', operation.operationId + '.json');
const journal = JSON.parse(await readFile(journalPath, 'utf8'));
const futureBytes = JSON.stringify({ ...journal, schemaVersion: 2 });
await writeFile(journalPath, futureBytes);
const inventoryBefore = await new InventoryStore(home).read();
const filesBefore = (await readdir(join(root, 'state'))).sort();
const older = await OperationStore.open(join(root, 'state'));
await assert.rejects(older.get(operation.operationId, operation.ownerId), { code: 'CORRUPT_RECORD' });
await assert.rejects(older.listForRecovery(), { code: 'CORRUPT_RECORD' });
let validated = false;
await assert.rejects(older.accept({ ownerId: 'session', hostInstanceId: older.hostInstanceId,
  requestKey: randomUUID(), requestDigest: operationRequestDigest({ version: '2.0.0' }), mode: 'new' },
  async () => { validated = true; throw new Error('Must reject before preparing any new operation'); }), { code: 'CORRUPT_RECORD' });
assert.equal(validated, false);
assert.equal(await readFile(journalPath, 'utf8'), futureBytes);
assert.deepEqual((await readdir(join(root, 'state'))).sort(), filesBefore);
assert.deepEqual(await new InventoryStore(home).read(), inventoryBefore);
assert.equal(await readFile(join(target, 'assets/message.txt'), 'utf8'), 'Resource from packed core');

console.log('packed core verified');
`);
    const node = Bun.which('node');
    if (!node) throw new Error('Node is required for the packed runtime check');
    // Remove all casing variants on Windows: no inherited executable lookup may
    // supply Bun or SkillHub CLI to the standalone installation operation.
    const env = Object.fromEntries(Object.entries(process.env).filter(([key]) =>
      !['path', 'node_path', 'node_options'].includes(key.toLowerCase())));
    const child = Bun.spawn([node, 'probe.mjs'], { cwd: root, stdout: 'pipe', stderr: 'pipe',
      env: { ...env, PATH: root, NODE_PATH: '', HOME: root, USERPROFILE: root } });
    expect(await child.exited, await new Response(child.stderr).text()).toBe(0);
    expect(await new Response(child.stdout).text()).toBe('packed core verified\n');
    expect(await readFile(join(root, 'skills/packed/SKILL.md'), 'utf8')).toBe(files['SKILL.md']);
  } finally { await rm(root, { recursive: true, force: true }); }
}, 30_000);
