import { describe, expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import { access, readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { createRequire, isBuiltin } from 'node:module';
import SkillHubService from '@astron-team/skillhub-dsh';
import { TYPERT_REMOTE } from '../lib/typert.remote-client.js';

describe('native M0 package', () => {
  test('bundle patch uses an owned loader ID instead of the reference plugin ID', async () => {
    const patch = await readFile(new URL('../cordis.patch.yml', import.meta.url), 'utf8');
    const entries = [...patch.matchAll(/^\s*- id: (\S+)$/gm)].map(match => match[1]);
    expect(entries).toEqual(['astron-skillhub']);
    expect(entries).not.toContain('skillhub');
    expect(patch).toContain('name: "@astron-team/skillhub-dsh"');
  });

  test('Host and generated Remote imports use only declared runtime dependencies', async () => {
    const manifest = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8')) as {
      dependencies: Record<string, string>;
      peerDependencies: Record<string, string>;
    };
    const dependencies = new Set([...Object.keys(manifest.dependencies), ...Object.keys(manifest.peerDependencies)]);
    const scanner = new Bun.Transpiler({ loader: 'js' });
    const lib = new URL('../lib/', import.meta.url);
    for (const artifact of ['index.js', 'local-core.js', 'typert.host.js', 'typert.remote-client.js']) {
      const url = new URL(artifact, lib);
      const source = await readFile(url, 'utf8');
      for (const entry of scanner.scanImports(source)) {
        if (isBuiltin(entry.path)) continue;
        if (entry.path.startsWith('.')) {
          const target = new URL(entry.path, url);
          expect(target.href.startsWith(lib.href), `Import escapes published lib: ${entry.path}`).toBe(true);
          await access(target);
          continue;
        }
        const name = entry.path.split('/').slice(0, entry.path.startsWith('@') ? 2 : 1).join('/');
        expect(dependencies.has(name), `${artifact} imports undeclared runtime dependency ${entry.path}`).toBe(true);
        expect(artifact === 'local-core.js', 'Bundled core must use only Node built-ins').toBe(false);
      }
    }
  });

  test('actual Cordis Host service exposes read-only status and disposes', async () => {
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService);
    await fiber;
    expect(ctx.skillhub.getStatus()).toBe('SkillHub: connected');
    expect(ctx.skillhub.typertRemote.namespace).toBe('skillhub');
    await expect(ctx.skillhub.listInstallTargets('forged-detail', new AbortController().signal)).rejects.toThrow('Conversation unavailable');
    await expect(ctx.skillhub.getOperation('forged', 'forged')).rejects.toThrow('Conversation unavailable');
    await fiber.dispose();
    expect(ctx.get('skillhub')).toBeUndefined();
  });

  test('official generator publishes the declared endpoints with scoped writes and strict codec', () => {
    expect(TYPERT_REMOTE.descriptors.map(item => item.method).sort()).toEqual(['acknowledgeConversationDetail', 'checkUpdate', 'checkVersionUpdate', 'executePlan', 'getCompatibility', 'getContext', 'getInstalledDetail', 'getOperation', 'getSkillDetail', 'getStatus', 'listCatalog', 'listCatalogByLabel', 'listInstallTargets', 'listInstalled', 'listLabels', 'listReviews', 'listSkills', 'listVersions', 'openConversationDetail', 'prepareUninstall', 'prepareUpgrade', 'prepareVersionUpgrade', 'recoverOperation', 'reopenConversationDetail', 'startInstall', 'testRegistryConnection']);
    for (const method of ['openConversationDetail', 'reopenConversationDetail', 'acknowledgeConversationDetail', 'listInstallTargets', 'listInstalled', 'getInstalledDetail', 'checkUpdate', 'checkVersionUpdate', 'prepareUninstall', 'prepareUpgrade', 'prepareVersionUpgrade', 'executePlan', 'startInstall', 'getOperation', 'recoverOperation']) {
      const scoped = TYPERT_REMOTE.descriptors.find(item => item.method === method)!;
      expect(scoped.invocation).toMatchObject({ kind: 'context', context: 'agent', wire: 'agentId' });
    }
    const result = TYPERT_REMOTE.descriptors.find(item => item.method === 'getStatus')!.result;
    expect(result?.mode).toBe('strict');
    if (result?.mode !== 'strict') throw new Error('Expected strict result codec');
    expect(result.create().parse('connected')).toBe('connected');
    expect(() => result.create().parse({ path: '/private' })).toThrow();
  });

  test('browser artifact registers a lazy ModuleLoader factory, no top-level UI execution', async () => {
    type ClientModule = { apply: (ctx: unknown) => Promise<() => Promise<void>> };
    type ModuleEntry = { id: string; factory: (require: (id: string) => unknown) => ClientModule };
    const entries: ModuleEntry[] = [];
    const elements: string[] = [];
    runInNewContext(await readFile(new URL('../lib/client.js', import.meta.url), 'utf8'), {
      window: { __ModuleLoader__: { load: (entry: ModuleEntry) => entries.push(entry) } },
      // Markdown's browser entity decoder allocates an inert element at factory time.
      // Actual decoding/rendering is covered in the browser, not by this loading test.
      document: { createElement: (tag: string) => { elements.push(tag); return {}; } },
    });
    expect(elements).toHaveLength(0);
    expect(entries).toHaveLength(1);
    expect(entries[0]?.id).toBe('@astron-team/skillhub-dsh');
    expect(typeof entries[0]?.factory).toBe('function');
    const require = createRequire(import.meta.url);
    const module = entries[0]!.factory(id => {
      if (!['react', 'react/jsx-runtime', 'react-dom'].includes(id)) throw new Error(`Undeclared Client external: ${id}`);
      return require(id);
    });
    expect(typeof module.apply).toBe('function');
    const formEntries: string[] = [];
    const settings = {
      configForms: { get: (entryId: string) => { formEntries.push(entryId); return {}; } },
      slots: { inject: () => () => {} },
    };
    const dispose = await module.apply({
      remote: { $mount: async () => async () => {} },
      inject: (dependencies: string[], setup: (ctx: typeof settings) => void) => {
        if (dependencies.includes('configForms')) setup(settings);
        return { dispose: async () => {} };
      },
    });
    const patch = await readFile(new URL('../cordis.patch.yml', import.meta.url), 'utf8');
    const hostEntryIds = [...patch.matchAll(/^\s*- id: (\S+)$/gm)].map(match => match[1]);
    expect(formEntries).toEqual(hostEntryIds);
    await dispose();
  });
});
