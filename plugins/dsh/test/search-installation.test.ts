import { expect, test } from 'bun:test';
import { validateJsonSchemaValue, type ToolDefinition } from '@deepseek-ai/dsh-tools';
import { registerSearchTool } from '../src/host/search-tool.js';
import { Context } from '@deepseek-ai/cordis';
import { mkdtemp, mkdir, realpath, rm, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import SkillHubService from '../src/host/index.js';
import { summarizeInstallation } from '../src/host/installation-summary.js';
import type { ConversationSearchResult } from '../src/host/search-result.js';
import { parseSearchResult } from '../src/host/search-result.js';
import type { InstalledSkillList } from '../src/host/types.js';

const item = { itemKey: 'private-key', namespace: 'team', slug: 'writer', version: '1.0.0', scope: 'user', state: 'present' } as const;
const checkedAt = '2026-10-19T00:00:00.000Z';
test('summary matches full coordinates, preserves scopes and abnormal records, and strips private fields', () => {
  const inventory: InstalledSkillList = { complete: true, issues: [], unavailable: [], items: [item,
    { ...item, namespace: 'other' }, { ...item, slug: 'other' },
    { ...item, scope: 'project', state: 'missing', version: '0.9.0' }] };
  const value = summarizeInstallation(item, inventory, checkedAt);
  expect(value).toEqual({ checkedAt, complete: true, unavailable: [], records: [
    { scope: 'user', state: 'present', version: '1.0.0', installationState: 'enabled' }, { scope: 'project', state: 'missing', version: '0.9.0', installationState: 'enabled' }] });
  expect(JSON.stringify(value)).not.toContain('private-key');
  expect(summarizeInstallation(item, { ...inventory, unavailable: ['project'] }, checkedAt).complete).toBe(false);
  expect(summarizeInstallation(item, undefined, checkedAt)).toEqual({ checkedAt, complete: false, unavailable: ['user', 'project'], records: [] });
});

test('persisted search accepts old metadata and the optional strict installation snapshot', () => {
  const saved: ConversationSearchResult = { schemaVersion: 1, kind: 'skillhub-search', query: '', sourceRevision: randomUUID(),
    sourceLabel: 'https://registry.example', createdAt: checkedAt, total: 1, returned: 1, limit: 20,
    items: [{ namespace: 'team', slug: 'writer', latestVersion: '2.0.0', summary: '', selectionRef: randomUUID() }] };
  expect(parseSearchResult(saved)).toEqual(saved);
  const enriched = { ...saved, items: [{ ...saved.items[0]!, installation: summarizeInstallation(item, undefined, checkedAt) }] };
  expect(parseSearchResult(enriched)).toEqual(enriched);
  let tool: ToolDefinition | undefined;
  registerSearchTool({ get: () => undefined, register: value => { tool = value; return () => {}; } }, async () => enriched);
  expect(validateJsonSchemaValue(tool!.output.schema, saved)).toEqual([]);
  expect(validateJsonSchemaValue(tool!.output.schema, enriched)).toEqual([]);
  const canonical = JSON.parse(JSON.stringify(enriched));
  expect(tool!.output.presentationMeta!({ q: '' }, canonical)).toEqual(canonical);
  const block = tool!.output.render({ q: '' }, canonical)[0]!;
  if (block.type !== 'text') throw new Error('Expected model text');
  expect(JSON.parse(block.text.split('\n')[0]!)).toEqual(enriched);
  expect(block.text).toContain('never globally uninstalled');
  expect(block.text).toContain('does not prove content integrity');
  expect(parseSearchResult({ ...enriched, items: [{ ...enriched.items[0]!, installation: { ...enriched.items[0]!.installation, path: '/private' } }] })).toBeUndefined();
  expect(parseSearchResult({ ...enriched, items: [{ ...enriched.items[0]!, installation: { ...enriched.items[0]!.installation, checkedAt: 'yesterday' } }] })).toBeUndefined();
});

async function fixture(run: (value: {
  service: SkillHubService; project: string; home: string; session: { id: string; header: { cwd: string } };
  setRead(read: (registry: string, roots: { scope: string; root: string }[]) => Promise<unknown>): void;
  setSearch(read: () => Promise<void>): void;
}) => Promise<void>) {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'dsh-search-installed-')));
  const project = join(root, 'project'); const home = join(root, 'home'); await mkdir(project); await mkdir(home);
  const previousHome = process.env.DSH_HOME; process.env.DSH_HOME = home;
  let search = async () => {};
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch() {
    await search();
    return Response.json({ data: { items: [{ namespace: 'team', slug: 'writer', latestVersion: '2.0.0', summary: 'write' }], total: 1, limit: 20 } });
  } });
  const ctx = new Context();
  const fs = { processPathFromHostPath: (path: string) => path,
    resolve: async (path: string) => ({ displayPath: path }), processPath: (value: { displayPath: string }) => value.displayPath };
  const session = { id: 'owner', header: { cwd: project } };
  const agent = { session, ctx: { get: () => fs } };
  const remove = ctx.provide('agents', { get: (id: string) => id === session.id ? agent : undefined } as never);
  const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href }); await fiber;
  try {
    await run({ service: ctx.skillhub, project, home, session,
      setRead: read => Reflect.set(ctx.skillhub, 'localRuntime', { listInstalled: read }),
      setSearch: read => { search = read; } });
  } finally {
    await fiber.dispose(); await remove(); server.stop(true);
    if (previousHome === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = previousHome;
    await rm(root, { recursive: true, force: true });
  }
}
const context = (cwd: string) => ({ sessionId: 'owner', cwd, callId: 'search' });

test('conversation search reads only current Registry approved roots and exposes safe namespace-specific records', async () => {
  await fixture(async ({ service, project, home, setRead }) => {
    let registry: string | undefined; let roots: unknown;
    setRead(async (source, targets) => { registry = source; roots = targets; return { complete: true, issues: [], items: [
      { ...item, fingerprint: 'private-fingerprint', targetPath: join(home, 'skills', 'writer') },
      { ...item, namespace: 'other', fingerprint: 'foreign', targetPath: join(home, 'skills', 'writer') },
    ] }; });
    const result = await service.searchConversation('writer', context(project), new AbortController().signal);
    expect(registry).toBe(service.getContext().registryUrl);
    expect(roots).toEqual([{ scope: 'user', root: join(home, 'skills'), dshHome: home },
      { scope: 'project', root: join(project, '.dsh', 'skills'), dshHome: home }]);
    expect(result.items[0]?.installation).toMatchObject({ complete: true, unavailable: [], records: [{ scope: 'user', version: '1.0.0', state: 'present' }] });
    expect(parseSearchResult(result)).toEqual(result);
    for (const value of [home, project, 'private-key', 'private-fingerprint', 'foreign']) expect(JSON.stringify(result)).not.toContain(value);
  });
});

test('conversation local query failure stays unknown; cancelled or changed sessions fail closed', async () => {
  await fixture(async ({ service, project, session, setRead, setSearch }) => {
    setRead(async () => { throw new Error('private filesystem error /sensitive'); });
    const result = await service.searchConversation('', context(project), new AbortController().signal);
    expect(result.items[0]?.installation).toMatchObject({ complete: false, unavailable: ['user', 'project'], records: [] });
    expect(JSON.stringify(result)).not.toContain('sensitive');
    await expect(service.searchConversation('', context('/other'), new AbortController().signal)).rejects.toThrow('Search session changed');
    const controller = new AbortController();
    setRead(async () => { controller.abort(); throw new Error('local failed'); });
    await expect(service.searchConversation('', context(project), controller.signal)).rejects.toThrow();
    setRead(async () => { session.header.cwd = '/other'; throw new Error('local failed'); });
    await expect(service.searchConversation('', context(project), new AbortController().signal)).rejects.toThrow('Search session changed');
    session.header.cwd = project;
    setSearch(async () => { Reflect.set(service, 'sourceRevision', randomUUID()); });
    await expect(service.searchConversation('', context(project), new AbortController().signal)).rejects.toThrow('source context changed');
  });
});

test('unavailable roots remain unknown and roots redirected during a failed read fail closed', async () => {
  await fixture(async ({ service, project, home, setRead }) => {
    await mkdir(join(project, '.dsh')); const redirected = join(project, 'redirected'); await mkdir(redirected);
    await symlink(redirected, join(project, '.dsh', 'skills'), 'junction');
    setRead(async () => ({ items: [], complete: true, issues: [] }));
    expect((await service.searchConversation('', context(project), new AbortController().signal)).items[0]?.installation)
      .toMatchObject({ complete: false, unavailable: ['project'], records: [] });
    setRead(async () => { await symlink(redirected, join(home, 'skills'), 'junction'); throw new Error('failed'); });
    await expect(service.searchConversation('', context(project), new AbortController().signal)).rejects.toThrow('Skill root changed');
  });
});
