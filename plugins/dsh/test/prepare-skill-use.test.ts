import { expect, spyOn, test } from 'bun:test';
import { Context, symbols } from '@deepseek-ai/cordis';
import { FileSystemSkillProvider } from '@deepseek-ai/dsh-skill-filesystem';
import { readSkillExamples } from '../../../cli/src/services/skill-examples.js';
import { randomBytes, createHash } from 'node:crypto';
import { chmod, lstat, mkdir, mkdtemp, readFile, readdir, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '../src/host/index.js';
import type { LoadedSkill } from '../src/host/skill-discovery.js';
import type { InstalledProjectionItem, RemovalBaseline } from '../src/host/generated/native-contract.js';
import type { SkillAvailability, SkillUsePreparation, SkillUseSelection } from '../src/host/types.js';

async function fixture(run: (f: {
  select(signal?: AbortSignal): Promise<SkillUseSelection>;
  availability(signal?: AbortSignal, key?: string): Promise<SkillAvailability>;
  named(signal?: AbortSignal): Promise<{ diagnosis: SkillAvailability }>;
  use(signal?: AbortSignal, key?: string): Promise<SkillUsePreparation>; key: string; directory: string;
  setLookup(callback: (loaded: LoadedSkill) => Promise<LoadedSkill | undefined>): void;
  setReadError(error?: Error): void;
  change(kind: string): void; observations(): number;
}) => Promise<void>, frontmatter = '', examples?: string) {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-use-')));
  const previousHome = process.env.DSH_HOME;
  const home = join(root, 'home'); const cwd = join(root, 'project');
  const directory = join(home, 'skills', 'market-slug');
  await mkdir(directory, { recursive: true }); await mkdir(cwd); process.env.DSH_HOME = home;
  const body = `---\nname: actual-name\ndescription: Prepare documents\n${frontmatter}---\nUse references/note.md.`;
  await writeFile(join(directory, 'SKILL.md'), body);
  await mkdir(join(directory, 'references')); await writeFile(join(directory, 'references/note.md'), 'original');
  if (examples !== undefined) { await mkdir(join(directory, '.skillhub')); await writeFile(join(directory, '.skillhub/examples.json'), examples); }
  const ctx = new Context(); const controller = new AbortController();
  const provider = new FileSystemSkillProvider(ctx, { signal: controller.signal, invalidate: () => {} }, {
    providerName: 'fixture', includeDefaultRoots: false, customSkillDirs: [directory], watch: false,
  });
  try {
    const listed = await provider.list({ signal: controller.signal });
    const candidates = Array.isArray(listed) ? listed : listed.candidates;
    const loaded = await provider.get(candidates[0]!, { signal: controller.signal });
    if (!loaded) throw new Error('Missing fixture skill');
    let lookup = async (skill: LoadedSkill): Promise<LoadedSkill | undefined> => skill;
    let revision = 'source'; let observations = 0; let pendingOperations = 0; let shared = false; let invalidMetadata = false; let replaceDuringInventory = false;
    let replaceDuringProviderRead = false;
    let readError: Error | undefined;
    const fs = { processPathFromHostPath: (path: string) => path,
      resolve: async (path: string) => ({ displayPath: path }), processPath: (target: { displayPath: string }) => target.displayPath,
      listDir: async (target: { displayPath: string }) => (await readdir(target.displayPath, { withFileTypes: true })).map(entry => ({
        name: entry.name, type: entry.isDirectory() ? 'directory' : 'file', target: { displayPath: join(target.displayPath, entry.name) },
      })),
      stat: async (target: { displayPath: string }) => ({ type: (await lstat(target.displayPath)).isDirectory() ? 'directory' : 'file' }),
      readText: (target: { displayPath: string }) => {
        if (replaceDuringProviderRead) currentSkills = { ...skills };
        if (readError) throw readError;
        return readFile(target.displayPath, 'utf8');
      },
    };
    let currentFs = fs;
    const session = { id: 'session-a', header: { cwd } };
    const agent = { session, ctx };
    const nativeGet = ctx.get.bind(ctx);
    ctx.get = ((name: string) => name === 'fs' ? currentFs : nativeGet(name as never)) as typeof ctx.get;
    const skills = { snapshot: async () => ({ complete: true, skills: [{ name: 'actual-name' }] }), get: async (name: string, options: { cwd: string; scope: unknown; signal: AbortSignal }) => {
      expect(name).toBe('actual-name'); expect(options.cwd).toBe(cwd); expect(options.scope).toBe(agent);
      options.signal.throwIfAborted(); observations++; return lookup(loaded);
    } };
    let currentSkills: (typeof skills & { [symbols.original]?: object }) | undefined = skills;
    let currentAgent: typeof agent | undefined = agent;
    const item: InstalledProjectionItem = { namespace: 'demo', slug: 'market-slug', version: '1.2.0', scope: 'user',
      state: 'present', installationState: 'enabled', fingerprint: 'fingerprint', targetPath: directory };
    const items = [item];
    const baseline: RemovalBaseline = { status: 'ready', kind: 'uninstall', inventoryItem: {
      registry: 'https://registry.example', namespace: item.namespace, slug: item.slug,
      targets: [{ agent: 'dsh', rootDir: join(home, 'skills'), installDir: directory, installedAt: 'now', version: item.version, fingerprint: item.fingerprint }],
    }, target: { scope: 'user', root: join(home, 'skills'), path: directory, files: examples === undefined ? {} : { '.skillhub/examples.json': createHash('sha256').update(examples).digest('hex') }, metadata: {
      registry: 'https://registry.example', namespace: item.namespace, slug: item.slug, version: item.version,
    } } };
    const runtime = { getStatus: () => ({ state: 'ready', pendingOperations }),
      readSkillExamples,
      listInstalled: async () => { if (replaceDuringInventory) currentSkills = { ...skills }; return { items, complete: true, issues: [] }; },
      inspectRemoval: async (): Promise<RemovalBaseline> => {
        if (invalidMetadata) return { status: 'blocked', reason: 'metadata-invalid' };
        if (shared) return { status: 'blocked', reason: 'shared-target' };
        // Model the existing baseline's all-resource checks; real bundled core is covered by native acceptance.
        if (await readFile(join(directory, 'SKILL.md'), 'utf8') !== body
          || await readFile(join(directory, 'references/note.md'), 'utf8') !== 'original'
          || (examples !== undefined && await readFile(join(directory, '.skillhub/examples.json'), 'utf8') !== examples)) {
          return { status: 'blocked', reason: 'local-changes' };
        }
        return structuredClone(baseline);
      } };
    const service = Object.assign(Object.create(SkillHubService.prototype), {
      lifetime: controller, installedReferenceKey: randomBytes(32), localRuntime: runtime,
      ctx: { invocation: { request: { args: { agentId: 'session-a' } } }, get: (name: string) =>
        name === 'agents' ? { get: () => currentAgent } : name === 'skills' ? currentSkills : undefined },
      assertSource: (value: string) => { if (value !== revision) throw new Error('source changed'); },
      getContext: () => ({ sourceRevision: revision, configured: true, registryUrl: 'https://registry.example' }),
    });
    const key = (await service.listInstalled('source', new AbortController().signal)).items[0].itemKey;
    await run({ select: (signal = new AbortController().signal) => service.selectSkillUse('source', 'demo', 'market-slug', signal),
      availability: (signal = new AbortController().signal, selected = key) => service.checkAvailability('source', selected, signal),
      named: (signal = new AbortController().signal) => service.manageConversation('availability', { name: 'actual-name' }, { sessionId: 'session-a', cwd, callId: 'call' }, signal),
      use: (signal = new AbortController().signal, selected = key) => service.prepareSkillUse('source', selected, signal),
      key, directory, observations: () => observations, setLookup: value => { lookup = value; },
      setReadError: error => { readError = error; }, change: kind => {
        if (kind === 'provider-skills') replaceDuringProviderRead = true;
        if (kind === 'inventory-skills') replaceDuringInventory = true;
        if (kind === 'source') revision = 'other';
        if (kind === 'session') session.id = 'session-b';
        if (kind === 'cwd') session.header.cwd = home;
        if (kind === 'agent') currentAgent = { ...agent };
        if (kind === 'fs') currentFs = { ...fs };
        if (kind === 'skills') currentSkills = { ...skills };
        if (kind === 'proxy') currentSkills = { ...skills, [symbols.original]: skills };
        if (kind === 'incomplete') skills.snapshot = async () => ({ complete: false, skills: [] });
        if (kind === 'no-snapshot') Reflect.deleteProperty(skills, 'snapshot');
        if (kind === 'empty-snapshot') skills.snapshot = async () => ({ complete: true, skills: [] });
        if (kind === 'missing-skills') currentSkills = undefined;
        if (kind === 'runtime') service.localRuntime = { ...runtime };
        if (kind === 'record') items.length = 0;
        if (kind === 'version') item.version = '2.0.0';
        if (kind === 'metadata-invalid') invalidMetadata = true;
        if (kind === 'shared') shared = true;
        if (kind === 'pending') pendingOperations = 1;
        if (kind === 'dispose') controller.abort();
      } });
  } finally {
    await provider.dispose();
    if (previousHome === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = previousHome;
    await rm(root, { recursive: true, force: true });
  }
}

test('use returns actual SKILL.md name, never the market slug or local paths', async () => {
  await fixture(async f => {
    expect(await f.use()).toEqual({ status: 'ready', name: 'actual-name', version: '1.2.0', scope: 'user', examples: { state: 'none' } });
    expect(f.observations()).toBe(2);
    f.change('proxy'); expect((await f.use()).status).toBe('ready');
  });
});

test('unavailable and overridden native winners cannot authorize an invocation', async () => {
  await fixture(async f => {
    f.setLookup(async () => undefined);
    expect(await f.use()).toEqual({ status: 'blocked', reason: 'skill-unavailable' });
    f.setLookup(async loaded => ({ ...loaded, resourceBase: { kind: 'directory', path: '/other' } }));
    expect(await f.use()).toEqual({ status: 'blocked', reason: 'skill-shadowed' });
    f.setLookup(async loaded => ({ ...loaded, content: 'modified winner' }));
    expect(await f.use()).toEqual({ status: 'blocked', reason: 'skill-shadowed' });
  });
});

test('actual user-invocable policy blocks handoff even when model invocation is allowed', async () => {
  await fixture(async f => {
    expect(await f.use()).toEqual({ status: 'blocked', reason: 'user-invocation-disabled' });
  }, 'user-invocable: false\n');
});

for (const filename of ['SKILL.md', 'references/note.md']) {
  test(`changed ${filename} is blocked before discovery and after asynchronous lookup`, async () => {
    await fixture(async f => {
      f.setLookup(async loaded => { await writeFile(join(f.directory, filename), 'changed'); return loaded; });
      expect(await f.use()).toEqual({ status: 'blocked', reason: 'files-changed' });
      expect(await f.use()).toEqual({ status: 'blocked', reason: 'files-changed' });
      expect(f.observations()).toBe(1);
    });
  });
}

for (const kind of ['source', 'session', 'cwd', 'agent', 'fs', 'skills', 'runtime', 'record', 'version', 'dispose']) {
  test(`use rejects ${kind} changing during native discovery`, async () => {
    await fixture(async f => {
      f.setLookup(async loaded => { f.change(kind); return loaded; });
      await expect(f.use()).rejects.toThrow();
    });
  });
}

for (const [kind, reason] of [['shared', 'shared-installation'], ['pending', 'operation-pending'], ['missing-skills', 'host-unavailable']] as const) {
  test(`use blocks ${kind} without reading a native skill`, async () => {
    await fixture(async f => {
      f.change(kind); expect(await f.use()).toEqual({ status: 'blocked', reason }); expect(f.observations()).toBe(0);
    });
  });
}

test('stale and forged references and aborted requests never load a skill', async () => {
  await fixture(async f => {
    await expect(f.use(AbortSignal.abort())).rejects.toThrow();
    await expect(f.use(undefined, `${f.key}!`)).rejects.toThrow();
    f.change('record'); await expect(f.use()).rejects.toThrow(); expect(f.observations()).toBe(0);
  });
});

test('winner is checked again after the second filesystem baseline', async () => {
  await fixture(async f => {
    let lookups = 0;
    f.setLookup(async loaded => ++lookups === 1 ? loaded : { ...loaded,
      resourceBase: { kind: 'directory', path: '/replacement' } });
    expect(await f.use()).toEqual({ status: 'blocked', reason: 'skill-shadowed' });
    expect(lookups).toBe(2);
  });
});

test('deleted local resources never produce a ready handoff', async () => {
  await fixture(async f => {
    f.setLookup(async loaded => { await rm(join(f.directory, 'references/note.md')); return loaded; });
    expect((await f.use()).status).toBe('blocked');
  });
});

test('late native service replacement and cancellation remain errors at the final lookup', async () => {
  for (const kind of ['skills', 'dispose', 'cwd']) {
    await fixture(async f => {
      let lookups = 0;
      f.setLookup(async loaded => { if (++lookups === 2) f.change(kind); return loaded; });
      await expect(f.use()).rejects.toThrow();
      expect(lookups).toBe(2);
    });
  }
});


test('an operation becoming pending during final discovery blocks a ready handoff', async () => {
  await fixture(async f => {
    let lookups = 0;
    f.setLookup(async loaded => { if (++lookups === 2) f.change('pending'); return loaded; });
    expect(await f.use()).toEqual({ status: 'blocked', reason: 'operation-pending' });
    expect(lookups).toBe(2);
  });
});

const multilingual = JSON.stringify({ schemaVersion: 1, defaultLocale: 'zh-CN', examples: {
  'zh-CN': [{ id: 'summary', title: '会议纪要', prompt: '请使用 {{skill}} 整理会议。' }],
  en: [{ id: 'summary', title: 'Summary', prompt: 'Use {{skill}} to summarize.' }],
} });
test('Host returns exact selected installation examples with raw digest and no path disclosure', async () => {
  await fixture(async f => {
    const prepared = await f.use(); expect(prepared.status).toBe('ready');
    if (prepared.status !== 'ready') throw new Error('must be ready');
    expect(prepared.name).toBe('actual-name'); expect(prepared.examples.state).toBe('ready');
    if (prepared.examples.state !== 'ready') throw new Error('must have examples');
    expect(prepared.examples.digest).toBe(createHash('sha256').update(multilingual).digest('hex'));
    expect(prepared.examples.items.en?.[0]?.title).toBe('Summary');
    expect(JSON.stringify(prepared)).not.toContain(f.directory);
  }, '', multilingual);
});
test('invalid packaged examples remain ordinary protected content and retain custom use', async () => {
  await fixture(async f => {
    const prepared = await f.use(); expect(prepared.status).toBe('ready');
    if (prepared.status === 'ready') expect(prepared.examples).toEqual({ state: 'invalid' });
  }, '', '{not json');
});
test('examples modified during native discovery prevent ready even if valid protocol', async () => {
  await fixture(async f => {
    f.setLookup(async loaded => { await writeFile(join(f.directory, '.skillhub/examples.json'), multilingual + ' '); return loaded; });
    expect(await f.use()).toEqual({ status: 'blocked', reason: 'files-changed' });
  }, '', multilingual);
});

test('examples changed at final winner lookup are blocked by the last content barrier', async () => {
  await fixture(async f => {
    let lookups = 0;
    f.setLookup(async loaded => { if (++lookups === 2) await writeFile(join(f.directory, '.skillhub/examples.json'), multilingual + ' '); return loaded; });
    expect(await f.use()).toEqual({ status: 'blocked', reason: 'files-changed' });
  }, '', multilingual);
});


test('availability separates integrity, session discovery and user/model policies without paths', async () => {
  for (const user of [true, false]) for (const model of [true, false]) await fixture(async f => {
    const result = await f.availability();
    expect(result).toMatchObject({ association: 'managed-reference', integrity: 'verified', discovery: 'winner',
      invocation: { userInvocable: user, modelInvocable: model }, reason: 'observed' });
    expect(JSON.stringify(result)).not.toContain(f.directory);
    expect(JSON.stringify(result)).not.toContain('Use references');
  }, `user-invocable: ${user}\ndisable-model-invocation: ${!model}\n`);
});
test('availability distinguishes another winner from same-resource content changes', async () => {
  await fixture(async f => {
    f.setLookup(async skill => ({ ...skill, path: '/private/another/SKILL.md' }));
    expect(await f.availability()).toMatchObject({ discovery: 'shadowed', reason: 'same-name-winner' });
  });
  await fixture(async f => {
    f.setLookup(async skill => ({ ...skill, content: 'secret changed content' }));
    const result = await f.availability();
    expect(result).toMatchObject({ discovery: 'unknown', reason: 'definition-changed' });
    expect(JSON.stringify(result)).not.toContain('secret');
  });
});
test('availability preserves incomplete and unsupported snapshot as unknown', async () => {
  for (const [change, reason] of [['incomplete', 'snapshot-incomplete'], ['no-snapshot', 'snapshot-unavailable']]) await fixture(async f => {
    f.change(change!); expect(await f.availability()).toMatchObject({ discovery: 'unknown', reason });
  });
  await fixture(async f => {
    f.change('empty-snapshot'); f.setLookup(async () => undefined);
    expect(await f.availability()).toMatchObject({ discovery: 'not-discovered' });
  });
});
test('availability keeps local edits, shared baseline and missing entry distinct', async () => {
  await fixture(async f => {
    await writeFile(join(f.directory, 'references/note.md'), 'modified');
    expect(await f.availability()).toMatchObject({ integrity: 'changed', discovery: 'winner', reason: 'local-changes' });
  });
  await fixture(async f => {
    f.change('shared'); expect(await f.availability()).toMatchObject({ integrity: 'unknown', discovery: 'winner', reason: 'baseline-unverified' });
  });
  await fixture(async f => {
    await rm(join(f.directory, 'SKILL.md'));
    expect(await f.availability()).toMatchObject({ integrity: 'missing', discovery: 'unknown', reason: 'files-missing' });
  });
});
test('availability aborts stale references and context or original-service changes', async () => {
  for (const change of ['source', 'session', 'cwd', 'agent', 'fs', 'skills', 'runtime', 'dispose']) await fixture(async f => {
    f.setLookup(async skill => { f.change(change); return skill; });
    await expect(f.availability()).rejects.toThrow();
  });
  await fixture(async f => {
    f.setLookup(async skill => { f.change('proxy'); return skill; });
    expect(await f.availability()).toMatchObject({ discovery: 'winner' });
    await expect(f.availability(undefined, 'old-key')).rejects.toThrow();
    const controller = new AbortController(); controller.abort();
    await expect(f.availability(controller.signal)).rejects.toThrow();
  });
});
test('name diagnostics never claim registry ownership and errors never disclose raw messages', async () => {
  await fixture(async f => {
    const { diagnosis } = await f.named();
    expect(diagnosis).toMatchObject({ association: 'not-associated', integrity: 'unknown', discovery: 'winner' });
    f.setLookup(async () => { throw new Error('/private/secret token'); });
    const result = await f.named();
    expect(result.diagnosis).toMatchObject({ discovery: 'unknown', reason: 'read-failed' });
    expect(JSON.stringify(result)).not.toContain('/private');
  });
});
test('availability rejects changing winners and allows independent concurrent read-only requests', async () => {
  await fixture(async f => {
    let count = 0;
    f.setLookup(async skill => ++count === 1 ? skill : { ...skill, content: 'changed' });
    expect(await f.availability()).toMatchObject({ discovery: 'unknown', reason: 'observation-changed' });
  });
  await fixture(async f => {
    const results = await Promise.all([f.availability(), f.availability()]);
    expect(results.every(value => value.reason === 'observed')).toBe(true);
  });
});


// Windows chmod does not remove directory read/traverse access.
test.skipIf(process.platform === 'win32')('availability does not call POSIX permission-denied files missing', async () => {
  await fixture(async f => {
    await chmod(f.directory, 0);
    try { expect(await f.availability()).toMatchObject({ integrity: 'unknown', discovery: 'unknown', reason: 'files-unreadable' }); }
    finally { await chmod(f.directory, 0o700); }
  });
});
test('availability does not call provider EACCES missing', async () => {
  await fixture(async f => {
    f.setReadError(Object.assign(new Error('Permission denied'), { code: 'EACCES' }));
    // The baseline can read the files, but the host filesystem provider cannot.
    expect(await f.availability()).toMatchObject({ integrity: 'verified', discovery: 'unknown', reason: 'read-failed' });
    expect(f.observations()).toBe(0);
    f.setReadError();
    expect(await f.availability()).toMatchObject({ integrity: 'verified', discovery: 'winner', reason: 'observed' });
  });
});
test('availability does not call invalid metadata modified', async () => {
  await fixture(async f => {
    f.change('metadata-invalid');
    expect(await f.availability()).toMatchObject({ integrity: 'unknown', discovery: 'winner', reason: 'baseline-unverified' });
  });
});
test('invalid skill definitions are unknown and diagnostics preserve every installation byte', async () => {
  await fixture(async f => {
    const before = await Promise.all(['SKILL.md', 'references/note.md'].map(path => readFile(join(f.directory, path), 'utf8')));
    await f.availability(); await f.named();
    expect(await Promise.all(['SKILL.md', 'references/note.md'].map(path => readFile(join(f.directory, path), 'utf8')))).toEqual(before);
    expect((await readdir(f.directory)).sort()).toEqual(['SKILL.md', 'references']);
    await writeFile(join(f.directory, 'SKILL.md'), '---\nname: [invalid\n---\ninvalid');
    expect(await f.availability()).toMatchObject({ discovery: 'unknown', reason: 'definition-unverified' });
  });
});


test('availability binds skills identity before the initial inventory await', async () => {
  await fixture(async f => {
    f.change('inventory-skills');
    await expect(f.availability()).rejects.toThrow('Session changed');
  });
});


test('availability always disposes its exact provider after context invalidation during file loading', async () => {
  await fixture(async f => {
    const dispose = spyOn(FileSystemSkillProvider.prototype, 'dispose');
    try {
      f.change('provider-skills');
      await expect(f.availability()).rejects.toThrow('Session changed');
      expect(dispose).toHaveBeenCalledTimes(1);
    } finally { dispose.mockRestore(); }
  });
});
test('missing managed definition preserves selected installation identity without inventing a call name', async () => {
  await fixture(async f => {
    await rm(join(f.directory, 'SKILL.md'));
    const result = await f.availability();
    expect(result.name).toBeUndefined();
    expect(result.installation).toEqual({ namespace: 'demo', slug: 'market-slug', version: '1.2.0', scope: 'user' });
    expect(result.reason).toBe('files-missing');
    expect((await f.named()).diagnosis.installation).toBeUndefined();
  });
});


test('coordinate selection observes the real native winner and rejects incomplete discovery', async () => {
  await fixture(async f => {
    expect(await f.select()).toEqual({ status: 'ready', itemKey: f.key });
    f.change('incomplete');
    expect(await f.select()).toEqual({ status: 'blocked', reason: 'selection-unverified' });
  });
});

test('coordinate selection rejects external winners and context changes during native reads', async () => {
  await fixture(async f => {
    f.setLookup(async loaded => ({ ...loaded, resourceBase: { kind: 'directory', path: '/external' } }));
    expect(await f.select()).toEqual({ status: 'blocked', reason: 'no-usable-installation' });
    f.setLookup(async loaded => { f.change('skills'); return loaded; });
    await expect(f.select()).rejects.toThrow('changed');
  });
});

test('coordinate selection preserves session, source, filesystem and runtime identity fences', async () => {
  for (const kind of ['source', 'session', 'cwd', 'agent', 'fs', 'skills', 'runtime', 'dispose']) {
    await fixture(async f => {
      f.setLookup(async loaded => { f.change(kind); return loaded; });
      await expect(f.select()).rejects.toThrow();
    });
  }
  for (const kind of ['record', 'version']) {
    await fixture(async f => {
      f.setLookup(async loaded => { f.change(kind); return loaded; });
      expect(await f.select()).toEqual({ status: 'blocked', reason: 'selection-unverified' });
    });
  }
});
