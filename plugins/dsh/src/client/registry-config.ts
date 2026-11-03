import type { SkillHubConfig } from '../host/types.js';

export type SavedRegistry = NonNullable<SkillHubConfig['savedRegistries']>[number];

/** Match RegistryAdapter's canonical URL without inventing additional source aliases. */
export function registryAddress(value: string): string {
  let url: URL;
  try { url = new URL(value.trim()); } catch { throw new Error('请输入有效的 HTTP 或 HTTPS 实例地址。'); }
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('请输入 HTTP 或 HTTPS 实例地址，不包含凭证、查询参数或片段。');
  }
  return url.href.replace(/\/+$/u, '');
}
export function sameRegistry(left: string | undefined, right: string | undefined): boolean {
  if (!left?.trim() || !right?.trim()) return false;
  try { return registryAddress(left) === registryAddress(right); } catch { return left.trim() === right.trim(); }
}
export function savedRegistryOptions(config: { registryUrl: string; savedRegistries?: readonly SavedRegistry[] }): SavedRegistry[] {
  const entries = [...(config.savedRegistries ?? [])];
  if (config.registryUrl.trim() && !entries.some(entry => sameRegistry(entry.url, config.registryUrl))) {
    entries.unshift({ name: '当前来源', url: config.registryUrl.trim() });
  }
  return entries;
}
/** Indexes always refer to savedRegistries; the legacy current address has no saved index. */
export function registryChoices(config: Pick<SkillHubConfig, 'registryUrl' | 'savedRegistries'>): (SavedRegistry & { savedIndex: number | null })[] {
  const entries = (config.savedRegistries ?? []).map((entry, savedIndex) => ({ ...entry, savedIndex }));
  return config.registryUrl.trim() && !entries.some(entry => sameRegistry(entry.url, config.registryUrl))
    ? [{ name: '未命名来源', url: config.registryUrl.trim(), savedIndex: null }, ...entries] : entries;
}
/** Preserve a legacy address only when the user edits/selects, never on initial read. */
export function preserveCurrentRegistry(config: SkillHubConfig): SkillHubConfig {
  const legacy = registryChoices(config).find(entry => entry.savedIndex === null);
  return legacy ? { ...config, savedRegistries: [...(config.savedRegistries ?? []), { name: legacy.name, url: legacy.url }] } : config;
}
export class RegistryConfigError extends Error {
  constructor(message: string, readonly savedIndex?: number) { super(message); }
}
export function editSavedRegistry(config: SkillHubConfig, index: number, next?: SavedRegistry): SkillHubConfig {
  const entries = [...(config.savedRegistries ?? [])];
  const current = entries[index];
  if (!current) return config;
  if (next) entries[index] = next;
  else entries.splice(index, 1);
  return { ...config, savedRegistries: entries,
    registryUrl: sameRegistry(config.registryUrl, current.url) ? next?.url ?? '' : config.registryUrl };
}
export function validateRegistryConfig(config: SkillHubConfig): SkillHubConfig {
  const registryUrl = config.registryUrl.trim();
  const seen = new Set<string>();
  const savedRegistries = (config.savedRegistries ?? []).map((entry, savedIndex) => {
    try {
    const name = entry.name.trim();
    if (!name) throw new Error('请填写来源名称。');
    const url = registryAddress(entry.url);
    if (seen.has(url)) throw new Error('此来源地址已存在，请保留一个条目。');
    seen.add(url);
    return { name, url };
    } catch (error) { throw new RegistryConfigError(error instanceof Error ? error.message : '请检查来源地址。', savedIndex); }
  });
  if (registryUrl) {
    try { registryAddress(registryUrl); } catch (error) { throw new RegistryConfigError(error instanceof Error ? error.message : '请检查来源地址。'); }
  }
  return { ...config, registryUrl, savedRegistries };
}
