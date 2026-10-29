import { describe, expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import { access, readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { createRequire, isBuiltin } from 'node:module';
import SkillHubService from '@astron-team/skillhub-dsh';
import { TYPERT } from '../lib/typert.host.js';
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
    expect(TYPERT_REMOTE.descriptors.map(item => item.method).sort()).toEqual(['acknowledgeConversationDetail', 'beginConversationManagement', 'beginInstalledUpdateScan', 'checkAvailability', 'checkInstalledUpdateScan', 'checkUpdate', 'checkVersionUpdate', 'confirmConversationManagement', 'endInstalledUpdateScan', 'executePlan', 'getCompatibility', 'getContext', 'getInstalledDetail', 'getOperation', 'getSkillDetail', 'getStatus', 'listCatalog', 'listCatalogByLabel', 'listInstallTargets', 'listInstalled', 'listLabels', 'listReviews', 'listSkills', 'listVersions', 'openConversationDetail', 'prepareSkillUse', 'prepareToggle', 'prepareUninstall', 'prepareUpgrade', 'prepareVersionUpgrade', 'recoverOperation', 'reopenConversationDetail', 'selectSkillUse', 'startInstall', 'testRegistryConnection']);
    for (const method of ['beginInstalledUpdateScan', 'checkInstalledUpdateScan', 'endInstalledUpdateScan', 'checkAvailability', 'beginConversationManagement', 'confirmConversationManagement', 'openConversationDetail', 'reopenConversationDetail', 'acknowledgeConversationDetail', 'listInstallTargets', 'listInstalled', 'getInstalledDetail', 'checkUpdate', 'checkVersionUpdate', 'prepareSkillUse', 'prepareToggle', 'prepareUninstall', 'prepareUpgrade', 'prepareVersionUpgrade', 'executePlan', 'startInstall', 'getOperation', 'recoverOperation']) {
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


test('shared RPC ABI keeps Host/Client descriptors, cancellation, argument order and historical receipts', async () => {
  if (!TYPERT || typeof TYPERT !== 'object' || !('package' in TYPERT)
    || typeof TYPERT.package !== 'string' || !('invocations' in TYPERT) || !Array.isArray(TYPERT.invocations)) throw new Error('Expected shared Host reflection');
  expect(TYPERT.package).toBe('@astron-team/skillhub-dsh');
  expect(TYPERT_REMOTE.package).toBe(TYPERT.package);
  const wireContract = (descriptor: typeof TYPERT_REMOTE.descriptors[number]) => ({
    id: descriptor.id, service: descriptor.service, namespace: descriptor.namespace, method: descriptor.method,
    invocation: JSON.parse(JSON.stringify(descriptor.invocation)),
    parameters: descriptor.parameters.map(parameter => ({ name: parameter.name, wire: parameter.wire, source: parameter.source })),
    cancellation: descriptor.cancellation, result: descriptor.result?.mode,
  });
  expect(TYPERT.invocations.map(wireContract)).toEqual(TYPERT_REMOTE.descriptors.map(wireContract));
  const operation = TYPERT_REMOTE.descriptors.find(descriptor => descriptor.method === 'getOperation')!;
  expect(operation.invocation).toMatchObject({ kind: 'context', context: 'agent', wire: 'agentId' });
  expect(operation.parameters.map(parameter => parameter.name)).toEqual(['operationId', 'hostInstanceId']);
  for (const method of ['startInstall', 'executePlan', 'prepareUpgrade', 'prepareUninstall']) {
    expect(TYPERT_REMOTE.descriptors.find(descriptor => descriptor.method === method)?.cancellation).toEqual({ parameter: 'signal' });
  }
  const historical = { operationId: 'operation', revision: 1, kind: 'install', namespace: 'demo', slug: 'writer',
    version: '1.0', targetLabel: 'Project', context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry',
      sourceRevision: 'revision', hostLabel: 'Host', initiatedFrom: 'plaza', hostInstanceId: 'host', createdAt: 'then', updatedAt: 'then' },
    phase: 'completed', files: 'installed', discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0 };
  if (operation.result?.mode !== 'strict') throw new Error('Expected shared strict operation codec');
  const operationCodec = operation.result.create();
  expect(operationCodec.parse(historical)).toEqual(historical);
  expect(operationCodec.parse({ ...historical, unexpected: 'field' })).toEqual(historical);
  expect(() => operationCodec.parse({ ...historical, phase: 'unrecognized' })).toThrow();
  for (const artifact of ['typert.host.js', 'typert.remote-client.js', 'client.js']) {
    expect(await readFile(new URL(`../lib/${artifact}`, import.meta.url), 'utf8')).not.toContain('dsh-typert-protocol/remote-info');
  }
  const host = await readFile(new URL('../lib/index.js', import.meta.url), 'utf8');
  const imports = new Bun.Transpiler({ loader: 'js' }).scanImports(host).map(entry => entry.path);
  expect(imports).toContain('@deepseek-ai/cordis');
  expect(imports).toContain('@deepseek-ai/dsh-typert-protocol');
});


test('skill-use RPC exposes only an agent-bound cancellable read and a restricted handoff projection', () => {
  const use = TYPERT_REMOTE.descriptors.find(item => item.method === 'prepareSkillUse')!;
  expect(use.invocation).toMatchObject({ kind: 'context', context: 'agent', wire: 'agentId' });
  expect(use.parameters.map(item => ({ name: item.name, wire: item.wire, source: item.source }))).toEqual([
    { name: 'sourceRevision', wire: 'sourceRevision', source: 'json' },
    { name: 'itemKey', wire: 'itemKey', source: 'json' },
  ]);
  expect(use.cancellation).toEqual({ parameter: 'signal' });
  for (const parameter of use.parameters) {
    const parameterCodec = parameter.codec;
    if (parameterCodec.mode !== 'strict') throw new Error('Expected strict parameter codec');
    expect(() => parameterCodec.create().parse({ path: '/caller-controlled' })).toThrow();
  }
  if (use.result?.mode !== 'strict') throw new Error('Expected strict skill-use result codec');
  const codec = use.result.create();
  const ready = { status: 'ready', name: 'actual-name', version: '1.2.0', scope: 'project', examples: { state: 'none' } };
  expect(codec.parse(ready)).toEqual(ready);
  const withExamples = { ...ready, examples: { state: 'ready', digest: '0'.repeat(64), defaultLocale: 'zh-CN', items: {
    'zh-CN': [{ id: 'summary', title: '整理会议', prompt: '使用 {{skill}} 整理。' }],
    en: [{ id: 'summary', title: 'Summary', prompt: 'Use {{skill}}.' }],
  } } };
  expect(codec.parse(withExamples)).toEqual(withExamples);
  expect(() => codec.parse({ ...ready, examples: { state: 'ready', items: {} } })).toThrow();
  expect(codec.parse({ ...ready, path: '/private', content: 'private skill body' })).toEqual(ready);
  expect(codec.parse({ status: 'blocked', reason: 'user-invocation-disabled' })).toEqual({ status: 'blocked', reason: 'user-invocation-disabled' });
  expect(() => codec.parse({ ...ready, scope: 'arbitrary' })).toThrow();
  expect(() => codec.parse({ status: 'blocked', reason: 'arbitrary' })).toThrow();
});
