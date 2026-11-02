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
  if (registryUrl) registryAddress(registryUrl);
  const seen = new Set<string>();
  const savedRegistries = (config.savedRegistries ?? []).map(entry => {
    const name = entry.name.trim();
    if (!name) throw new Error('请填写来源名称。');
    const url = registryAddress(entry.url);
    if (seen.has(url)) throw new Error('此来源地址已存在，请保留一个条目。');
    seen.add(url);
    return { name, url };
  });
  return { ...config, registryUrl, savedRegistries };
}
