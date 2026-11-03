import { describe, expect, test } from 'bun:test';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { Config } from '../src/host/config.ts';
import { editSavedRegistry, registryChoices, preserveCurrentRegistry, RegistryConfigError, savedRegistryOptions, validateRegistryConfig } from '../src/client/registry-config.ts';
import type { SkillHubConfig } from '../src/host/types.ts';
const base: SkillHubConfig = { registryUrl: 'https://a.example', detailTabs: { versions: 'auto', reviews: 'hide', securityAudit: 'show' },
  savedRegistries: [{ name: 'A', url: 'https://a.example/' }, { name: 'B', url: 'https://b.example' }] };
describe('saved registry configuration', () => {
  test('legacy default, custom source and explicit disconnect survive schema resolution', () => {
    expect(Config({}).get()).toMatchObject({ registryUrl: 'https://skill.xfyun.cn', savedRegistries: [] });
    expect(Config({ registryUrl: '' }).get().registryUrl).toBe('');
    const custom = Config({ registryUrl: 'https://custom.example' }).get();
    expect(savedRegistryOptions(custom)).toEqual([{ name: '当前来源', url: 'https://custom.example' }]);
    expect(custom.savedRegistries).toEqual([]);
  });
  test('editing or deleting the active saved source updates the sole current pointer atomically', () => {
    const edited = editSavedRegistry(base, 0, { name: 'New A', url: 'https://new.example' });
    expect(edited.registryUrl).toBe('https://new.example');
    expect(edited.savedRegistries?.[0]?.url).toBe(edited.registryUrl);
    expect(editSavedRegistry(base, 0).registryUrl).toBe('');
    expect(editSavedRegistry(base, 1).registryUrl).toBe(base.registryUrl);
    expect(base.savedRegistries).toHaveLength(2);
    expect(edited.detailTabs).toEqual(base.detailTabs);
  });
  test('normalizes duplicate addresses without collapsing distinct base paths', () => {
    expect(() => validateRegistryConfig({ ...base, savedRegistries: [...base.savedRegistries!, { name: 'again', url: 'https://A.example///' }] })).toThrow('已存在');
    expect(validateRegistryConfig({ ...base, savedRegistries: [{ name: ' nested ', url: 'https://a.example/team/' }] }).savedRegistries)
      .toEqual([{ name: 'nested', url: 'https://a.example/team' }]);
    expect(savedRegistryOptions(base)).toHaveLength(2);
  });
  test.each(['file:///tmp/skills', 'https://user:pass@a.example', 'https://a.example?token=x', 'https://a.example#fragment', 'invalid'])('rejects unsafe saved address %s', url => {
    expect(() => validateRegistryConfig({ ...base, savedRegistries: [{ name: 'bad', url }] })).toThrow();
  });
  test('Host configuration survives JSON serialization and the settings client rehydrate/validate boundary', () => {
    // SettingsSchemaService uses new Schema(serialized), then schema(value). A fresh process
    // keeps Host lexical dependencies unavailable, matching the client boundary.
    const serialized = JSON.stringify(Config.toJSON());
    expect(serialized).not.toContain('"callback"'); // Browser CSP cannot execute schema callbacks.
    const validation = spawnSync(process.execPath, ['-e', `
      import Schema from '@deepseek-ai/schemastery';
      const { schema, values } = await Bun.stdin.json();
      const hydrated = new Schema(schema);
      for (const value of values) hydrated(value);
    `], { cwd: resolve(import.meta.dir, '..'), encoding: 'utf8', timeout: 10_000,
      input: JSON.stringify({ schema: Config.toJSON(), values: [Config(base).get(), Config({ registryUrl: '' }).get(), Config({}).get()] }),
    });
    expect(validation.stderr).toBe('');
    expect(validation.status).toBe(0);
    expect(() => Config({ savedRegistries: [{ name: ' ', url: 'https://a.example' }] }).get()).toThrow();
  });
  test('empty names are rejected and adding sources never selects them', () => {
    expect(() => validateRegistryConfig({ ...base, savedRegistries: [{ name: ' ', url: 'https://b.example' }] })).toThrow('名称');
    expect(validateRegistryConfig({ ...base, registryUrl: '' }).registryUrl).toBe('');
  });
});

 test('legacy current choices retain saved indexes and persist only after an explicit edit or switch', () => {
   const legacy = { ...base, registryUrl: 'https://legacy.example' };
   expect(registryChoices(legacy).map(entry => entry.savedIndex)).toEqual([null, 0, 1]);
   expect(legacy.savedRegistries).toHaveLength(2);
   const retained = preserveCurrentRegistry(legacy);
   expect(retained.savedRegistries).toHaveLength(3);
   expect(retained.savedRegistries?.[2]).toEqual({ name: '未命名来源', url: legacy.registryUrl });
   expect(registryChoices(retained).map(entry => entry.savedIndex)).toEqual([0, 1, 2]);
   expect(editSavedRegistry(retained, 1).registryUrl).toBe(legacy.registryUrl);
   expect(editSavedRegistry(retained, 2).registryUrl).toBe('');
   expect(preserveCurrentRegistry(retained)).toBe(retained);
 });
 test('source validation identifies the saved editor independently of duplicate names', () => {
   const choices = { ...base, savedRegistries: [{ name: 'Same', url: 'https://a.example' }, { name: 'Same', url: 'https://a.example/' }] };
   try { validateRegistryConfig(choices); throw new Error('expected rejection'); }
   catch (error) { expect(error).toBeInstanceOf(RegistryConfigError); expect((error as RegistryConfigError).savedIndex).toBe(1); }
 });
