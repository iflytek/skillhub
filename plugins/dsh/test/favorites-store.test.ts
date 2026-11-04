import { describe, expect, test } from 'bun:test';
import { mkdtemp, mkdir, readFile, rm, stat, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { withFileLock } from '@deepseek-ai/dsh-atomic-write';
import { FavoritesStore, favoriteLimits } from '../src/host/favorites-store.ts';

const source = 'https://registry.example/team';
const item = { namespace: 'team', slug: 'writer', displayName: 'Writer', summary: 'Write documents.' };
async function fixture(run: (root: string, store: FavoritesStore, file: string) => Promise<void>) {
  const root = await mkdtemp(join(tmpdir(), 'dsh-favorites-'));
  try { await run(root, new FavoritesStore(root), join(root, 'skillhub/favorites.json')); }
  finally { await rm(root, { recursive: true, force: true }); }
}

describe('local favorites preferences', () => {
  test('source-isolated snapshots survive new stores, explicit retries and inventory remains unchanged', () => fixture(async (root, store, file) => {
    expect(await store.list(source)).toEqual({ items: [] });
    const inventory = join(root, 'inventory.json'); await writeFile(inventory, 'inventory sentinel');
    await store.set('HTTPS://REGISTRY.EXAMPLE:443/team///', item, true);
    await store.set(source, item, true);
    await store.set('https://registry.example/other', item, true);
    await store.set(source, { ...item, namespace: 'other' }, true);
    const saved = JSON.parse(await readFile(file, 'utf8'));
    expect(saved.version).toBe(1); expect(saved.items).toHaveLength(3);
    expect(saved.items.map((record: { registry: string; namespace: string }) => [record.registry, record.namespace])).toEqual([
      [source, 'other'], ['https://registry.example/other', 'team'], [source, 'team'],
    ]);
    expect(await new FavoritesStore(root).list(source)).toEqual({ items: [{ ...item, namespace: 'other' }, item] });
    expect(await new FavoritesStore(root).list('https://registry.example/other')).toEqual({ items: [item] });
    await store.set(source, item, false); await store.set(source, item, false);
    expect(await store.list(source)).toEqual({ items: [{ ...item, namespace: 'other' }] });
    expect(await readFile(inventory, 'utf8')).toBe('inventory sentinel');
    if (process.platform !== 'win32') {
      expect((await stat(file)).mode & 0o777).toBe(0o600);
      expect((await stat(join(root, 'skillhub'))).mode & 0o777).toBe(0o700);
    }
  }));

  test.each([
    '{broken', JSON.stringify({ version: 2, items: [] }), JSON.stringify({ version: 1, items: [{ registry: source, namespace: 'team' }] }),
    JSON.stringify({ version: 1, items: [{ registry: source, ...item }, { registry: source, ...item }] }),
  ])('damaged or unknown stores are not silently replaced (%s)', content => fixture(async (root, store, file) => {
    await mkdir(join(root, 'skillhub')); await writeFile(file, content);
    await expect(store.list(source)).rejects.toMatchObject({ reason: 'INVALID_STORE' });
    await expect(store.set(source, item, true)).rejects.toMatchObject({ reason: 'INVALID_STORE' });
    expect(await readFile(file, 'utf8')).toBe(content);
  }));

  test('invalid inputs and collection/byte limits preserve original bytes', () => fixture(async (_root, store, file) => {
    await store.set(source, item, true); const before = await readFile(file);
    for (const invalid of [{ ...item, namespace: '..' }, { ...item, slug: '' }, { ...item, slug: 'x'.repeat(257) }, { ...item, summary: 'x'.repeat(1025) }]) {
      await expect(store.set(source, invalid, true)).rejects.toMatchObject({ reason: 'INVALID_INPUT' });
    }
    await expect(store.set('https://user:credential@registry.example', item, true)).rejects.toMatchObject({ reason: 'INVALID_INPUT' });
    expect(await readFile(file)).toEqual(before);
    const full = JSON.stringify({ version: 1, items: Array.from({ length: favoriteLimits.items }, (_, index) => ({ registry: source, namespace: 'team', slug: `skill-${index}` })) });
    await writeFile(file, full);
    await expect(store.set(source, item, true)).rejects.toMatchObject({ reason: 'LIMIT_REACHED' });
    expect(await readFile(file, 'utf8')).toBe(full);
    await store.set(source, { namespace: 'team', slug: 'skill-0' }, false);
    expect(JSON.parse(await readFile(file, 'utf8')).items).toHaveLength(favoriteLimits.items - 1);
    await writeFile(file, ' '.repeat(favoriteLimits.bytes + 1));
    await expect(store.set(source, item, true)).rejects.toMatchObject({ reason: 'INVALID_STORE' });
    expect((await stat(file)).size).toBe(favoriteLimits.bytes + 1);
  }));

  test.skipIf(process.platform === 'win32')('symlink files and directories cannot redirect preference reads or writes', () => fixture(async (root, store, file) => {
    const outside = join(root, 'outside'); await mkdir(outside);
    const sentinel = join(outside, 'sentinel'); await writeFile(sentinel, 'private sentinel');
    await mkdir(join(root, 'skillhub')); await symlink(sentinel, file);
    await expect(store.list(source)).rejects.toMatchObject({ reason: 'INVALID_STORE' });
    await expect(store.set(source, item, true)).rejects.toMatchObject({ reason: 'INVALID_STORE' });
    expect(await readFile(sentinel, 'utf8')).toBe('private sentinel');
    await rm(join(root, 'skillhub'), { recursive: true }); await symlink(outside, join(root, 'skillhub'), 'dir');
    await expect(store.set(source, item, true)).rejects.toMatchObject({ reason: 'INVALID_STORE' });
    expect(await readFile(sentinel, 'utf8')).toBe('private sentinel');
  }));

  test('waiting for an official lock rechecks context before committing', () => fixture(async (_root, store, file) => {
    await store.set(source, item, true); const before = await readFile(file);
    const held = Promise.withResolvers<void>(); const release = Promise.withResolvers<void>();
    const lock = withFileLock(file, async () => { held.resolve(); await release.promise; }); await held.promise;
    let valid = true; let checks = 0; const expected = new Error('context changed');
    const result = store.set(source, { ...item, slug: 'next' }, true, () => { checks++; if (!valid) throw expected; }).then(() => undefined, error => error);
    valid = false; release.resolve(); await lock;
    expect(await result).toBe(expected); expect(checks).toBeGreaterThanOrEqual(2);
    expect(await readFile(file)).toEqual(before);
  }));

  test('a live lock timeout preserves bytes, then a released lock permits a later retry', () => fixture(async (_root, store, file) => {
    await store.set(source, item, true); const before = await readFile(file);
    const held = Promise.withResolvers<void>(), release = Promise.withResolvers<void>();
    const lock = withFileLock(file, async () => { held.resolve(); await release.promise; }); await held.promise;
    try {
      await expect(store.set(source, { ...item, slug: 'after-lock' }, true)).rejects.toMatchObject({ reason: 'UNAVAILABLE', message: 'UNAVAILABLE' });
      expect(await readFile(file)).toEqual(before);
    } finally { release.resolve(); await lock; }
    await store.set(source, { ...item, slug: 'after-lock' }, true);
    expect(JSON.parse(await readFile(file, 'utf8')).items.map((entry: { slug: string }) => entry.slug).sort()).toEqual(['after-lock', 'writer']);
  }), 10000);

  test('cancellation while waiting for a lock rejects before publishing', () => fixture(async (_root, store, file) => {
    await store.set(source, item, true); const before = await readFile(file);
    const held = Promise.withResolvers<void>(), release = Promise.withResolvers<void>();
    const lock = withFileLock(file, async () => { held.resolve(); await release.promise; }); await held.promise;
    const abort = new AbortController();
    const outcome = store.set(source, { ...item, slug: 'cancelled' }, true, () => abort.signal.throwIfAborted()).then(() => null, (error: unknown) => error);
    abort.abort(); release.resolve(); await lock;
    expect(await outcome).toBe(abort.signal.reason);
    expect(await readFile(file)).toEqual(before);
  }));

  test('non-file or unavailable storage exposes stable reasons without raw diagnostics', () => fixture(async (root, store, file) => {
    await mkdir(join(root, 'skillhub')); await mkdir(file);
    await expect(store.set(source, item, true)).rejects.toMatchObject({ reason: 'INVALID_STORE', message: 'INVALID_STORE' });
    await rm(file, { recursive: true }); await writeFile(file, '{broken');
    const error = await store.list(source).catch(error => error); expect(error.message).not.toContain(root);
    const blocked = new FavoritesStore(file);
    await expect(blocked.set(source, item, true)).rejects.toMatchObject({ reason: 'UNAVAILABLE', message: 'UNAVAILABLE' });
  }));

  test.each(['direct', 'home-alias'] as const)('two Node writers contend behind an explicit lock barrier without losing either record (%s)', variant => fixture(async (root, store, file) => {
    await store.set(source, item, true);
    const build = await Bun.build({ entrypoints: [fileURLToPath(new URL('../src/host/favorites-store.ts', import.meta.url))], target: 'node', format: 'esm', packages: 'external' });
    if (!build.success) throw new AggregateError(build.logs, 'Store test build failed');
    await writeFile(join(root, 'store.mjs'), await build.outputs[0]!.text());
    await symlink(fileURLToPath(new URL('../node_modules', import.meta.url)), join(root, 'node_modules'), process.platform === 'win32' ? 'junction' : 'dir');
    const runner = join(root, 'writer.mjs');
    await writeFile(runner, `import fs from 'node:fs/promises';
import {syncBuiltinESMExports} from 'node:module';
import {join} from 'node:path';
const canonicalHome=await fs.realpath(process.argv[2]);
const lock=join(canonicalHome,'skillhub/favorites.json.lock');
const originalWrite=fs.writeFile;
let observed=false;
// Observe the SDK's real exclusive-create failure, never replace lock behavior.
fs.writeFile=async function(filename,data,options){
  try {return await originalWrite.call(this,filename,data,options);}
  catch(error){
    if(!observed&&String(filename)===lock&&options?.flag==='wx'){
      const contention=error.code==='EEXIST'||(error.code==='EPERM'&&await fs.lstat(lock).then(value=>value.isFile(),()=>false));
      if(contention){observed=true;process.send({type:'contended',code:error.code,canonicalized:canonicalHome!==process.argv[2]});}
    }
    throw error;
  }
};
syncBuiltinESMExports();
const {FavoritesStore}=await import('./store.mjs');
try {const value=await new FavoritesStore(process.argv[2]).set(process.argv[3],{namespace:'team',slug:process.argv[4]},true);process.send({type:'saved',count:value.items.length});process.disconnect();} catch(error){process.send({type:'failed',reason:error.reason??error.name,observed,canonicalized:canonicalHome!==process.argv[2]});process.disconnect();process.exitCode=1;}`);
    const childHome = variant === 'home-alias' ? join(root, 'home-alias') : root;
    if (variant === 'home-alias') await symlink(root, childHome, process.platform === 'win32' ? 'junction' : 'dir');
    const children: ReturnType<typeof spawn>[] = [], counts: number[] = [], canonicalized: boolean[] = [], completions: Promise<void>[] = [], ready: Promise<void>[] = [];
    try {
      await withFileLock(file, async () => {
        for (const slug of ['first', 'second']) {
          const child = spawn('node', [runner, childHome, source, slug], { stdio: ['ignore', 'ignore', 'pipe', 'ipc'] }); children.push(child);
          let stderr = '', reportedFailure = '';
          child.stderr?.setEncoding('utf8');
          child.stderr?.on('data', chunk => { stderr = (stderr + chunk).slice(-4096); });
          child.on('message', message => {
            if (message && typeof message === 'object' && 'type' in message && message.type === 'failed') reportedFailure = JSON.stringify(message);
          });
          const failure = (note: string) => new Error([note, reportedFailure, stderr.trim()].filter(Boolean).join('\n'));
          ready.push(new Promise<void>((resolve, reject) => {
            let contended = false;
            const deadline = setTimeout(() => reject(failure('Writer did not attempt the held lock')), 5000);
            child.on('message', message => {
              if (message && typeof message === 'object' && 'type' in message && message.type === 'contended'
                && 'code' in message && (message.code === 'EEXIST' || message.code === 'EPERM')) {
                  contended = true;
                  canonicalized.push('canonicalized' in message && message.canonicalized === true);
                  clearTimeout(deadline); resolve();
                }
            });
            child.once('error', error => { clearTimeout(deadline); reject(error); });
            child.once('close', code => { clearTimeout(deadline); if (!contended) reject(failure(`Writer exited ${code} before attempting the held lock`)); });
          }));
          const completion = new Promise<void>((resolve, reject) => {
            child.on('message', message => { if (message && typeof message === 'object' && 'type' in message && message.type === 'saved' && 'count' in message && typeof message.count === 'number') counts.push(message.count); });
            child.once('error', reject); child.once('close', code => code === 0 ? resolve() : reject(failure(`Writer exited ${code}`)));
          });
          void completion.catch(() => {});
          completions.push(completion);
        }
        await Promise.all(ready);
        if (variant === 'home-alias') expect(canonicalized).toEqual([true, true]);
        expect(JSON.parse(await readFile(file, 'utf8')).items.map((entry: { slug: string }) => entry.slug)).toEqual(['writer']);
      });
      await Promise.all(completions);
      expect(counts.sort()).toEqual([2, 3]);
      expect(JSON.parse(await readFile(file, 'utf8')).items.map((entry: { slug: string }) => entry.slug).sort()).toEqual(['first', 'second', 'writer']);
    } finally { for (const child of children) if (child.exitCode === null) child.kill(); }
  }), 15000);
});
