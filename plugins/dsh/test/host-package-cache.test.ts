import { expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import { PluginPackages } from '@deepseek-ai/dsh-app-boot';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { assertRuntimeCompatibility, runtimePackageBaseline } from '../src/host/runtime-compatibility.js';

test('official package service caches metadata while disk verification rejects replacement', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-official-package-cache-'));
  const ctx = new Context();
  const fiber = ctx.plugin(PluginPackages);
  try {
    await fiber;
    const parent = pathToFileURL(join(root, 'plugin.js')).href;
    for (const [name, version] of Object.entries(runtimePackageBaseline)) {
      const directory = join(root, 'node_modules', name);
      await mkdir(directory, { recursive: true });
      await writeFile(join(directory, 'package.json'), JSON.stringify({ name, version }));
    }
    const services = {
      packageOf: (name: string, parentURL: string) => ctx.pluginPackages.packageOf(name, parentURL),
      clientPath: (name: string) => `/fixture/${name}/client.js`,
    };
    const check = () => assertRuntimeCompatibility(services, parent, (_file, name) => ({ name, version: '0.2.0-rc.2' }));
    expect(check).not.toThrow();
    for (const [name, version] of Object.entries(runtimePackageBaseline)) {
      const original = ctx.pluginPackages.packageOf(name, parent)!;
      const path = join(root, 'node_modules', name, 'package.json');
      await writeFile(path, JSON.stringify({ name, version: '0.0.0-test-mismatch' }));
      expect(ctx.pluginPackages.packageOf(name, parent)).toBe(original);
      expect(original.version).toBe(version);
      expect(check).toThrow('local writes are disabled');
      await rm(path);
      expect(check).toThrow('local writes are disabled');
      await writeFile(path, JSON.stringify({ name, version }));
      expect(check).not.toThrow();
    }
  } finally {
    await fiber.dispose();
    await rm(root, { recursive: true, force: true });
  }
});
