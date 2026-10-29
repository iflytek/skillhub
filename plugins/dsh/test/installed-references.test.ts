import { expect, test } from 'bun:test';
import { randomBytes } from 'node:crypto';
import { mkdtemp, mkdir, realpath, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '../src/host/index.js';
import type { InstalledProjectionItem } from '../src/host/generated/native-contract.js';
import type { InstalledSkillList } from '../src/host/types.js';

async function fixture(run: (fixture: {
  list(): Promise<InstalledSkillList>; read(key: string): Promise<unknown>;
  records: InstalledProjectionItem[]; session: { id: string; header: { cwd: string } };
  listReads(): number; setComplete(value: boolean): void; revision(value: string): void; restartHost(): void; project: string; home: string;
}) => Promise<void>) {
  // Match the Host's canonical roots (macOS /var aliases and Windows short temp paths).
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-installed-references-')));
  const previousHome = process.env.DSH_HOME;
  const home = join(root, 'home'); const project = join(root, 'project');
  await mkdir(project); process.env.DSH_HOME = home;
  try {
    const fs = { processPathFromHostPath: (path: string) => path,
      resolve: async (path: string) => ({ displayPath: path }), processPath: (target: { displayPath: string }) => target.displayPath };
    const session = { id: 'session-a', header: { cwd: project } };
    const agent = { session, ctx: { get: () => fs } };
    const records: InstalledProjectionItem[] = [];
    let complete = true; let revision = 'source'; let listReads = 0;
    const service = Object.assign(Object.create(SkillHubService.prototype), {
      lifetime: new AbortController(), installedReferenceKey: randomBytes(32),
      localRuntime: { listInstalled: async (_registry: string, roots: { scope: string; root: string }[]) => { listReads++; return {
        items: records.filter(item => roots.some(target => target.scope === item.scope && join(target.root, item.slug) === item.targetPath)),
        complete, issues: complete ? [] : ['inventory-unreadable'],
      }; } },
      ctx: { invocation: { request: { args: { agentId: 'session-a' } } }, get: () => ({ get: () => agent }) },
      assertSource: (value: string) => { if (value !== revision) throw new Error('source changed'); },
      getContext: () => ({ configured: true, registryUrl: 'https://registry.example' }),
    });
    await run({ list: () => service.listInstalled('source', new AbortController().signal),
      read: key => service.readInstalledItem('source', key, new AbortController().signal), records, session,
      listReads: () => listReads, setComplete: value => { complete = value; }, revision: value => { revision = value; },
      restartHost: () => { service.installedReferenceKey = randomBytes(32); }, project, home });
  } finally {
    if (previousHome === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = previousHome;
    await rm(root, { recursive: true, force: true });
  }
}

function record(home: string, index: number): InstalledProjectionItem {
  return { namespace: 'demo', slug: `helper-${index}`, version: '1.0', scope: 'user', state: 'present', installationState: 'enabled',
    fingerprint: 'fingerprint', targetPath: join(home, 'skills', `helper-${index}`) };
}

test('all 401 references work and repeated or cross-session refresh does not evict them', async () => {
  await fixture(async ({ records, home, list, read, session }) => {
    records.push(...Array.from({ length: 401 }, (_, index) => record(home, index)));
    const initial = await list(); expect(initial.items).toHaveLength(401);
    for (const item of initial.items) await read(item.itemKey);
    const key = initial.items[0].itemKey;
    expect(key).toMatch(/^[A-Za-z0-9_-]{43}$/);
    for (let i = 0; i < 401; i++) await list();
    expect((await list()).items[0].itemKey).toBe(key); await read(key);
    session.id = 'session-b'; await expect(read(key)).rejects.toThrow();
    expect((await list()).items[0].itemKey).not.toBe(key);
    session.id = 'session-a'; await read(key);
    await expect(read(`${key.slice(0, -1)}!`)).rejects.toThrow();
    records[0] = { ...records[0], fingerprint: 'changed' }; await expect(read(key)).rejects.toThrow();
  });
}, 20_000);

test('references reject changed cwd, removed/version-changed record and changed source', async () => {
  await fixture(async ({ records, home, list, read, session, project, revision }) => {
    records.push(record(home, 0)); const key = (await list()).items[0].itemKey;
    session.header.cwd = home; await expect(read(key)).rejects.toThrow(); session.header.cwd = project;
    records[0].version = '2'; await expect(read(key)).rejects.toThrow(); records[0].version = '1.0';
    records.length = 0; await expect(read(key)).rejects.toThrow();
    records.push(record(home, 0)); revision('new-source'); await expect(read(key)).rejects.toThrow();
  });
});

test('unavailable independent scope preserves inventory completeness; global failure stays closed', async () => {
  for (const scope of ['project', 'user'] as const) {
    await fixture(async ({ project, home, list, setComplete }) => {
      const parent = scope === 'project' ? join(project, '.dsh') : home;
      const redirected = join(project, 'redirected');
      await mkdir(parent); await mkdir(redirected);
      await symlink(redirected, join(parent, 'skills'), 'junction');
      expect(await list()).toMatchObject({ complete: true, unavailable: [scope] });
      setComplete(false);
      expect(await list()).toMatchObject({ complete: false, issues: ['inventory-unreadable'], unavailable: [scope] });
      // Each scope has its own fixture; recursive teardown handles the junction like the other path tests.
    });
  }
});

test('opaque references expire with a Host restart or a different trusted target root', async () => {
  await fixture(async ({ records, home, list, read, restartHost }) => {
    records.push(record(home, 0)); const key = (await list()).items[0].itemKey;
    process.env.DSH_HOME = join(home, 'different');
    await expect(read(key)).rejects.toThrow(); process.env.DSH_HOME = home;
    await read(key); restartHost(); await expect(read(key)).rejects.toThrow();
    expect((await list()).items[0].itemKey).not.toBe(key);
  });
});

test('identical release reinstalled or ownership changed invalidates the old reference', async () => {
  await fixture(async ({ records, home, list, read }) => {
    records.push({ ...record(home, 0), recordRevision: 'first-target-snapshot' });
    const key = (await list()).items[0]!.itemKey;
    records[0]!.recordRevision = 'new-timestamp-or-owners';
    await expect(read(key)).rejects.toThrow();
    expect((await list()).items[0]!.itemKey).not.toBe(key);
  });
});


test('toggle invalidates old action references but preserves exact location identity', async () => {
  await fixture(async ({ records, home, list, read }) => {
    records.push(record(home, 0));
    const before = (await list()).items[0];
    records[0].installationState = 'disabled';
    const after = (await list()).items[0];
    expect(after.installationState).toBe('disabled');
    expect(after.locationKey).toBe(before.locationKey);
    expect(after.itemKey).not.toBe(before.itemKey);
    await expect(read(before.itemKey)).rejects.toThrow();
    await read(after.itemKey);
  });
});


test('local presentation keeps reference identity and uses a single inventory projection per list', async () => {
  await fixture(async ({ records, home, list, read, listReads }) => {
    const item = record(home, 0); records.push(item);
    const before = (await list()).items[0]; expect(listReads()).toBe(1);
    await mkdir(item.targetPath, { recursive: true });
    await writeFile(join(item.targetPath, 'SKILL.md'), '---\nname: authored-name\ndescription: 本地真实说明\n---\nPRIVATE BODY');
    const after = (await list()).items[0]; expect(listReads()).toBe(2);
    expect(after).toEqual({ ...before, displayName: 'authored-name', summary: '本地真实说明' });
    await read(after.itemKey);
    await writeFile(join(item.targetPath, 'SKILL.md'), '---\nname: renamed-display\ndescription: 修改展示文字\n---');
    expect((await list()).items[0].itemKey).toBe(before.itemKey);
    expect(records[0]).toEqual(item);
  });
});
