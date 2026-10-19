import { expect, test } from 'bun:test';
import { mkdtemp, mkdir, readFile, realpath, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
// @ts-expect-error The development-only Host observer has no TypeScript declarations.
import { assertProbeEnvironment, assertProbeFile } from './integration/search-installation-fixture.mjs';

async function isolated(run: (root: string, outside: string) => Promise<void>) {
  const parent = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-search-guard-')));
  try {
    const root = join(parent, 'root');
    const outside = join(parent, 'outside');
    await mkdir(root);
    await mkdir(outside);
    await run(root, outside);
  } finally { await rm(parent, { recursive: true, force: true }); }
}

const homes = { HOME: 'native-install-home', DSH_HOME: 'home', DSH_AGENTS_HOME: 'agents' };
const envFor = (root: string) => Object.fromEntries(Object.entries(homes).map(([key, name]) => [key, join(root, name)]));

test('fixture accepts fresh real directories without changing the process environment', async () => {
  await isolated(async root => {
    for (const name of Object.values(homes)) await mkdir(join(root, name));
    expect(() => assertProbeEnvironment(root, envFor(root))).not.toThrow();
    const file = join(root, 'home', 'inventory.json');
    await writeFile(file, '{}');
    expect(() => assertProbeFile(root, file)).not.toThrow();
    expect(() => assertProbeFile(root, join(root, 'home'))).toThrow();
  });
});

for (const [key, redirected] of Object.entries(homes)) {
  test(`fixture rejects redirected ${key} before entering runtime or tool work`, async () => {
    await isolated(async (root, outside) => {
      for (const name of Object.values(homes)) {
        if (name === redirected) await symlink(outside, join(root, name), process.platform === 'win32' ? 'junction' : 'dir');
        else await mkdir(join(root, name));
      }
      await writeFile(join(outside, 'sentinel'), 'unchanged');
      let runtimeEntered = false;
      expect(() => {
        assertProbeEnvironment(root, envFor(root));
        runtimeEntered = true;
      }).toThrow();
      expect(runtimeEntered).toBe(false);
      expect(await readFile(join(outside, 'sentinel'), 'utf8')).toBe('unchanged');
    });
  });
}

for (const name of ['inventory.json', 'metadata.json']) {
  for (const redirectParent of [false, true]) {
    test(`fixture rejects ${name} with ${redirectParent ? 'redirected parent' : 'symlink file'} before writing`, async () => {
      await isolated(async (root, outside) => {
        const external = join(outside, name);
        await writeFile(external, 'external-sentinel');
        const parent = join(root, '.skillhub');
        if (redirectParent) await symlink(outside, parent, process.platform === 'win32' ? 'junction' : 'dir');
        else {
          await mkdir(parent);
          await symlink(external, join(parent, name), 'file');
        }
        let writeAllowed = false;
        expect(() => {
          assertProbeFile(root, join(parent, name));
          writeAllowed = true;
        }).toThrow();
        expect(writeAllowed).toBe(false);
        expect(await readFile(external, 'utf8')).toBe('external-sentinel');
      });
    });
  }
}
