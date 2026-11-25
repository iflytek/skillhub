import { expect, test } from 'bun:test';
import { createHash } from 'node:crypto';
import { chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { AccountCredentials } from '../src/host/account-credentials.ts';

const source = 'https://registry.example/team';
const other = 'https://registry.example/another-prefix';
const fixture = { token: 'fixture-not-a-real-api-token', identity: { handle: '用户-fixture', displayName: '团队成员 🐈' } };
const file = (profile: string, registry = source) => join(profile, 'skillhub-accounts', createHash('sha256').update(registry).digest('hex') + '.json');

test('accounts survive a new store, isolate sources, and logout remains forgotten', () => {
  const profile = mkdtempSync(join(tmpdir(), 'skillhub-凭据-'));
  try {
    new AccountCredentials(profile).set(source, fixture);
    const restored = new AccountCredentials(profile);
    expect(restored.get(source)).toEqual(fixture);
    expect(restored.get(other)).toBeUndefined();
    writeFileSync(file(profile), 'broken');
    expect(restored.get(source)).toEqual(fixture);
    restored.set(source);
    expect(new AccountCredentials(profile).get(source)).toBeUndefined();
    expect(readdirSync(join(profile, 'skillhub-accounts'))).toEqual([]);
  } finally { rmSync(profile, { recursive: true, force: true }); }
}, 30000);

test('a failed replacement preserves the previously committed account and no temporary file', () => {
  const profile = mkdtempSync(join(tmpdir(), 'skillhub-replace-'));
  try {
    const store = new AccountCredentials(profile);
    store.set(source, fixture);
    const original = readFileSync(file(profile));
    expect(() => store.set(source, { ...fixture, token: 'x'.repeat(9000) })).toThrow();
    expect(readFileSync(file(profile))).toEqual(original);
    expect(store.get(source)).toEqual(fixture);
    expect(new AccountCredentials(profile).get(source)).toEqual(fixture);
    expect(readdirSync(join(profile, 'skillhub-accounts'))).toHaveLength(1);
  } finally { rmSync(profile, { recursive: true, force: true }); }
}, 30000);

test('failed deletion blocks reloading in this process and can be retried', () => {
  const profile = mkdtempSync(join(tmpdir(), 'skillhub-clear-'));
  try {
    const store = new AccountCredentials(profile);
    store.set(source, fixture);
    const original = readFileSync(file(profile));
    rmSync(file(profile)); mkdirSync(file(profile));
    writeFileSync(join(file(profile), 'block-unlink'), 'fixture');
    expect(() => store.set(source)).toThrow();
    expect(store.get(source)).toBeUndefined();
    expect(store.hasRemovalFailure(source)).toBe(true);
    expect(store.hasRemovalFailure(other)).toBe(false);
    rmSync(file(profile), { recursive: true });
    writeFileSync(file(profile), original, { mode: 0o600 });
    expect(store.get(source)).toBeUndefined();
    store.set(source);
    expect(store.hasRemovalFailure(source)).toBe(false);
    expect(new AccountCredentials(profile).get(source)).toBeUndefined();
  } finally { rmSync(profile, { recursive: true, force: true }); }
}, 30000);

test('malformed and oversized records cannot restore an account', () => {
  const profile = mkdtempSync(join(tmpdir(), 'skillhub-invalid-'));
  try {
    mkdirSync(join(profile, 'skillhub-accounts'), { mode: 0o700 });
    for (const content of ['not JSON', 'null', '{}', JSON.stringify({ version: 99 }), 'x'.repeat(16385)]) {
      writeFileSync(file(profile), content, { mode: 0o600 });
      expect(new AccountCredentials(profile).get(source)).toBeUndefined();
    }
  } finally { rmSync(profile, { recursive: true, force: true }); }
});

test.skipIf(process.platform === 'win32')('POSIX permissions and symbolic links cannot expose another credential', () => {
  const profile = mkdtempSync(join(tmpdir(), 'skillhub-private-'));
  try {
    const store = new AccountCredentials(profile);
    store.set(source, fixture);
    chmodSync(file(profile), 0o644);
    expect(new AccountCredentials(profile).get(source)).toBeUndefined();
    chmodSync(file(profile), 0o600);
    const original = file(profile) + '.original';
    copyFileSync(file(profile), original);
    rmSync(file(profile)); symlinkSync(original, file(profile));
    expect(new AccountCredentials(profile).get(source)).toBeUndefined();
  } finally { rmSync(profile, { recursive: true, force: true }); }
});

test.skipIf(process.platform !== 'win32')('real Windows Node processes restore DPAPI, reject copied ciphertext, and forget logout', async () => {
  const root = mkdtempSync(join(tmpdir(), 'skillhub-Windows-凭据-'));
  const profile = join(root, '中文-profile');
  const second = join(root, '另一-profile');
  const module = join(root, 'store.mjs');
  try {
    const build = await Bun.build({ entrypoints: [join(import.meta.dir, '../src/host/account-credentials.ts')], target: 'node', format: 'esm' });
    expect(build.success).toBe(true);
    writeFileSync(module, await build.outputs[0]!.text());
    const child = `import { pathToFileURL } from 'node:url';process.stdin.setEncoding('utf8');let input='';for await(const chunk of process.stdin)input+=chunk;const r=JSON.parse(input);const {AccountCredentials}=await import(pathToFileURL(r.module).href);const store=new AccountCredentials(r.profile);try{if(r.action==='save')store.set(r.source,r.fixture);if(r.action==='clear')store.set(r.source);const value=store.get(r.source);console.log(JSON.stringify({matched:JSON.stringify(value)===JSON.stringify(r.fixture),absent:value===undefined}));}catch{console.log(JSON.stringify({rejected:true}));}`;
    const run = (action: string, directory = profile, registry = source, restricted = false) => {
      const result = spawnSync('node', [...(restricted ? ['--permission', '--allow-fs-read=*'] : []), '--input-type=module', '-e', child], {
        input: JSON.stringify({ module, profile: directory, source: registry, fixture, action }), encoding: 'utf8', timeout: 15000,
      });
      expect(result.error).toBeUndefined(); expect(result.status).toBe(0);
      return JSON.parse(result.stdout.trim());
    };
    expect(run('save').matched).toBe(true);
    expect(run('read').matched).toBe(true);
    const alias = (profile[0] === profile[0]!.toUpperCase() ? profile[0]!.toLowerCase() : profile[0]!.toUpperCase()) + profile.slice(1);
    expect(run('read', alias).matched).toBe(true);
    const original = readFileSync(file(profile));
    expect(original.includes(Buffer.from(fixture.token))).toBe(false);
    expect(original.includes(Buffer.from(fixture.identity.displayName))).toBe(false);
    expect(run('read', profile, source, true).rejected).toBe(true);
    expect(readFileSync(file(profile))).toEqual(original);
    expect(run('read').matched).toBe(true);
    copyFileSync(file(profile), file(profile, other));
    expect(run('read', profile, other).rejected).toBe(true);
    mkdirSync(join(second, 'skillhub-accounts'), { recursive: true });
    copyFileSync(file(profile), file(second));
    expect(run('read', second).rejected).toBe(true);
    expect(run('clear').absent).toBe(true);
    expect(existsSync(file(profile))).toBe(false);
    expect(run('read').absent).toBe(true);
    expect(readdirSync(join(profile, 'skillhub-accounts'))).toEqual([file(profile, other).split(/[\\/]/u).at(-1)!]);
  } finally { rmSync(root, { recursive: true, force: true }); }
}, 90000);
