import { expect, test } from 'bun:test';
import { releasePolicy } from '../scripts/release-policy.ts';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const pkg = { name: '@astron-team/skillhub-dsh', version: '0.1.0', private: false };
test('release requires an exact independent tag and explicit publishability', () => {
  expect(releasePolicy(pkg, 'dsh-v0.1.0', true)).toMatchObject({ dist_tag: 'latest', version: '0.1.0' });
  for (const tag of ['cli-v0.1.0', 'v0.1.0', 'dsh-v0.2.0', 'dsh-v0.1.0\n']) {
    expect(() => releasePolicy(pkg, tag, true)).toThrow();
  }
  for (const privateValue of [true, undefined]) {
    expect(() => releasePolicy({ ...pkg, private: privateValue }, 'dsh-v0.1.0', true)).toThrow();
  }
  expect(releasePolicy({ ...pkg, private: true }, 'dsh-v0.1.0', false)).toMatchObject({ version: '0.1.0' });
  expect(() => releasePolicy({ ...pkg, name: '@cocofhu/skillhub' }, 'dsh-v0.1.0', true)).toThrow();
});
test('prereleases use next and malformed versions cannot reach publishing', () => {
  expect(releasePolicy({ ...pkg, version: '0.1.0-rc.2' }, 'dsh-v0.1.0-rc.2', true)).toMatchObject({ dist_tag: 'next' });
  for (const version of ['01.0.0', '1.0', '1.0.0-01', '1.0.0-', '1.0.0+build', '1.0.0\n', '$(command)']) {
    expect(() => releasePolicy({ ...pkg, version }, `dsh-v${version}`, true)).toThrow();
  }
});

test('Node entry validates the packed manifest and emits no outputs on rejection', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'dsh-release-entry-'));
  const manifest = join(directory, 'package.json');
  const output = join(directory, 'outputs');
  const script = fileURLToPath(new URL('../scripts/release-policy.ts', import.meta.url));
  try {
    await writeFile(manifest, JSON.stringify(pkg));
    const run = (overrides: Record<string, string> = {}) => spawnSync('node', [script, manifest], {
      env: { ...process.env, GITHUB_REF_TYPE: 'tag', RELEASE_TAG: 'dsh-v0.1.0',
        PUBLISH: 'true', GITHUB_OUTPUT: output, ...overrides }, encoding: 'utf8', timeout: 10000,
    });
    const accepted = run();
    expect(accepted.status).toBe(0);
    const original = await readFile(output, 'utf8');
    expect(original).toBe('version=0.1.0\npackage_name=@astron-team/skillhub-dsh\ndist_tag=latest\n');
    const rejectedContexts: Record<string, string>[] = [{ GITHUB_REF_TYPE: 'branch' }, { RELEASE_TAG: 'dsh-v9.9.9' }];
    for (const overrides of rejectedContexts) {
      expect(run(overrides).status).not.toBe(0);
      expect(await readFile(output, 'utf8')).toBe(original);
    }
    for (const rejected of [{ ...pkg, version: '0.2.0' }, { ...pkg, private: true },
      { ...pkg, name: '@unrelated/package' }]) {
      await writeFile(manifest, JSON.stringify(rejected));
      expect(run().status).not.toBe(0);
      expect(await readFile(output, 'utf8')).toBe(original);
    }
  } finally { await rm(directory, { recursive: true, force: true }); }
});
