import { expect, spyOn, test } from 'bun:test';
import { randomBytes } from 'node:crypto';
import { mkdtemp, mkdir, realpath, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '@astron-team/skillhub-dsh';
import type { InstalledProjectionItem } from '../src/host/generated/native-contract.js';

async function fixture(run: (fixture: ReturnType<typeof setup>) => Promise<void>) {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-round-')));
  const previous = process.env.DSH_HOME;
  process.env.DSH_HOME = join(root, 'home');
  await mkdir(join(root, 'project'));
  const f = setup(root);
  try { await run(f); }
  finally { for (const scan of f.service.installedUpdateScans.values()) scan.close(); if (previous === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = previous; await rm(root, { recursive: true, force: true }); }
}
function setup(root: string) {
  const fs = { processPathFromHostPath: (path: string) => path, resolve: async (path: string) => ({ displayPath: path }), processPath: (target: { displayPath: string }) => target.displayPath };
  const session = { id: 'session', header: { cwd: join(root, 'project') } };
  const agent = { session, ctx: { get: () => fs } };
  const state = { reads: 0, snapshots: 0, resolves: 0, invalid: false, revision: 'source', currentAgent: agent,
    resolve: async (signal: AbortSignal) => { signal.throwIfAborted(); return { version: '2.0.0', fingerprint: 'candidate' }; } };
  const items: InstalledProjectionItem[] = ['user', 'project'].map(scope => ({ scope: scope as 'user' | 'project', namespace: 'demo', slug: 'writer', version: '1.0.0', state: 'present', fingerprint: 'original', installationState: 'enabled', targetPath: join(root, scope === 'user' ? 'home/skills' : 'project/.dsh/skills', 'writer') }));
  const service = Object.assign(Object.create(SkillHubService.prototype), {
    lifetime: new AbortController(), installedReferenceKey: randomBytes(32), installedUpdateScans: new Map(),
    ctx: { invocation: { request: { args: { agentId: 'session' } } }, get: () => ({ get: () => state.currentAgent }) },
    assertSource: (revision: string) => { if (revision !== state.revision) throw new Error('source changed'); },
    getContext: () => ({ registryUrl: 'https://registry.example' }),
    readSource: async (_revision: string, signal: AbortSignal, read: (registry: object, signal: AbortSignal) => unknown) => read({ resolve: async () => { state.resolves++; return state.resolve(signal); } }, signal),
    localRuntime: {
      openInstalledSnapshot: async () => { state.snapshots++; return { projection: { items, complete: true, issues: [] }, read: async (item: InstalledProjectionItem) => { state.reads++; if (state.invalid) throw new Error('Inventory changed'); return item; } }; },
      assessUpdate: async () => ({ state: 'upgradable', currentVersion: '1.0.0', candidateVersion: '2.0.0', reason: 'newer-version' }),
    },
  });
  const begin = (signal = new AbortController().signal): Promise<{ scanId: string; itemKeys: string[] }> => service.beginInstalledUpdateScan('source', signal);
  const check = (scan: { scanId: string; itemKeys: string[] }, index = 0, signal = new AbortController().signal) => service.checkInstalledUpdateScan('source', scan.scanId, scan.itemKeys[index], signal);
  return { service, state, session, fs, agent, items, begin, check };
}

test('round shares resolve across locations, isolates target cancellation, and end aborts shared work', async () => fixture(async f => {
  let resolve!: (value: { version: string; fingerprint: string }) => void;
  let shared!: AbortSignal;
  f.state.resolve = signal => { shared = signal; return new Promise(done => { resolve = done; }); };
  const scan = await f.begin();
  const cancelled = new AbortController();
  const first = f.check(scan, 0, cancelled.signal).catch(() => 'cancelled');
  const second = f.check(scan, 1);
  while (!resolve) await new Promise(done => setImmediate(done));
  cancelled.abort(); expect(shared.aborted).toBe(false);
  resolve({ version: '2.0.0', fingerprint: 'candidate' });
  expect(await first).toBe('cancelled'); expect(await second).toMatchObject({ state: 'upgradable' });
  expect(f.state.resolves).toBe(1); expect(f.state.reads).toBe(4);
  await f.service.endInstalledUpdateScan('source', scan.scanId);
  expect(shared.aborted).toBe(true); expect(f.service.installedUpdateScans.size).toBe(0);
  await expect(f.check(scan)).rejects.toThrow();
  const next = await f.begin(); f.state.resolve = async () => ({ version: '2.0.0', fingerprint: 'candidate' });
  await f.check(next); expect(f.state.resolves).toBe(2);
}));

test('round rejects foreign agent, forged item, source and actual session/fs/runtime changes', async () => fixture(async f => {
  let scan = await f.begin();
  f.service.ctx.invocation.request.args.agentId = 'foreign';
  await expect(f.check(scan)).rejects.toThrow();
  await f.service.endInstalledUpdateScan('source', scan.scanId); expect(f.service.installedUpdateScans.size).toBe(1);
  f.service.ctx.invocation.request.args.agentId = 'session';
  await expect(f.check({ ...scan, itemKeys: ['/client/path'] })).rejects.toThrow(); expect(f.service.installedUpdateScans.size).toBe(0);
  for (const mutate of [() => { f.session.header.cwd += '/changed'; }, () => { f.state.currentAgent = { ...f.agent }; }, () => { f.agent.ctx.get = () => ({ ...f.fs }); }, () => { f.service.localRuntime = { ...f.service.localRuntime }; }, () => { f.state.revision = 'new-source'; }]) {
    const cwd = f.session.header.cwd; const get = f.agent.ctx.get; const runtime = f.service.localRuntime;
    scan = await f.begin(); mutate(); await expect(f.check(scan)).rejects.toThrow(); expect(f.service.installedUpdateScans.size).toBe(0);
    f.session.header.cwd = cwd; f.state.currentAgent = f.agent; f.agent.ctx.get = get; f.service.localRuntime = runtime; f.state.revision = 'source';
  }
}));

test('bounded rounds, idle expiration, hard expiration and begin abort clean up', async () => fixture(async f => {
  const scans = await Promise.all(Array.from({ length: 4 }, () => f.begin()));
  await expect(f.begin()).rejects.toThrow('Too many');
  for (const scan of scans) await f.service.endInstalledUpdateScan('source', scan.scanId);
  const aborted = new AbortController(); aborted.abort(); await expect(f.begin(aborted.signal)).rejects.toThrow(); expect(f.service.installedUpdateScans.size).toBe(0);
  const now = Date.now(); const clock = spyOn(Date, 'now').mockReturnValue(now);
  try {
    let scan = await f.begin(); clock.mockReturnValue(now + 60_001); await expect(f.check(scan)).rejects.toThrow();
    expect(f.service.installedUpdateScans.size).toBe(0);
    clock.mockReturnValue(now); scan = await f.begin();
    for (let second = 50; second < 1800; second += 50) { clock.mockReturnValue(now + second * 1000); await f.check(scan); }
    clock.mockReturnValue(now + 1800_001); await expect(f.check(scan)).rejects.toThrow(); expect(f.service.installedUpdateScans.size).toBe(0);
  } finally { clock.mockRestore(); }
}));

test('inventory changes during resolve invalidate the whole round, and failures deduplicate', async () => fixture(async f => {
  f.state.resolve = async () => { f.state.invalid = true; return { version: '2.0.0', fingerprint: 'candidate' }; };
  const scan = await f.begin(); await expect(f.check(scan)).rejects.toThrow('Installed update scan changed');
  expect(f.service.installedUpdateScans.size).toBe(0);
  f.state.invalid = false; f.state.resolve = async () => { throw new Error('offline'); };
  const retry = await f.begin();
  const results = await Promise.all([f.check(retry), f.check(retry, 1)]);
  expect(results.map(value => value.state)).toEqual(['unavailable', 'unavailable']); expect(f.state.resolves).toBe(2);
}));

for (const size of [10, 100, 500]) test(`round ${size} targets resolves unique skills once`, async () => fixture(async f => {
  const base = [...f.items]; f.items.length = 0;
  for (let index = 0; index < size / 2; index++) for (const item of base) f.items.push({ ...item, slug: `skill-${index}`, targetPath: join(item.targetPath, '..', `skill-${index}`) });
  const scan = await f.begin();
  // Production overview schedules three checks at a time.
  for (let index = 0; index < size; index += 3) await Promise.all(Array.from({ length: Math.min(3, size - index) }, (_, offset) => f.check(scan, index + offset)));
  expect(f.state.snapshots).toBe(1); expect(f.state.reads).toBe(2 * size); expect(f.state.resolves).toBe(size / 2);
  console.info(JSON.stringify({ targets: size, snapshots: f.state.snapshots, targetChecks: f.state.reads, remoteResolves: f.state.resolves }));
}));


test('Host bounds actual remote concurrency even when callers exceed the UI queue', async () => fixture(async f => {
  const base = f.items[0]!; f.items.length = 0;
  for (let index = 0; index < 12; index++) f.items.push({ ...base, slug: `skill-${index}`, targetPath: join(base.targetPath, '..', `skill-${index}`) });
  let active = 0; let maximum = 0;
  const releases: (() => void)[] = [];
  f.state.resolve = async () => {
    active++; maximum = Math.max(maximum, active);
    await new Promise<void>(done => releases.push(done)); active--;
    return { version: '2.0.0', fingerprint: 'candidate' };
  };
  const scan = await f.begin();
  const results = Promise.all(scan.itemKeys.map((_, index) => f.check(scan, index)));
  for (let wave = 0; wave < 4; wave++) {
    while (releases.length < 3) await new Promise(done => setImmediate(done));
    expect(active).toBe(3);
    releases.splice(0).forEach(done => done());
  }
  expect(await results).toHaveLength(12); expect(maximum).toBe(3); expect(f.state.resolves).toBe(12);
}));

test('aborting begin during snapshot construction releases its reserved slot', async () => fixture(async f => {
  const snapshot = f.service.localRuntime.openInstalledSnapshot;
  const controller = new AbortController();
  f.service.localRuntime.openInstalledSnapshot = async () => { controller.abort(); return snapshot(); };
  await expect(f.begin(controller.signal)).rejects.toThrow();
  expect(f.service.installedUpdateScans.size).toBe(0);
}));


test('cancelled initializing snapshots retain slots until filesystem work settles', async () => fixture(async f => {
  const snapshot = f.service.localRuntime.openInstalledSnapshot;
  const releases: (() => void)[] = [];
  f.service.localRuntime.openInstalledSnapshot = async () => {
    await new Promise<void>(done => releases.push(done));
    return snapshot();
  };
  const controllers = Array.from({ length: 4 }, () => new AbortController());
  const pending = controllers.map(controller => f.begin(controller.signal).catch(() => 'cancelled'));
  while (releases.length < 4) await new Promise(done => setImmediate(done));
  controllers.forEach(controller => controller.abort());
  expect(f.service.installedUpdateScans.size).toBe(4);
  await expect(f.begin()).rejects.toThrow('Too many');
  releases.splice(0).forEach(done => done());
  expect(await Promise.all(pending)).toEqual(['cancelled', 'cancelled', 'cancelled', 'cancelled']);
  expect(f.service.installedUpdateScans.size).toBe(0);
}));
