import type { FavoriteSkill, FavoriteVersion, SkillList } from '../host/types.js';

export const favoritesKey = (sourceRevision: string | undefined) => ['skillhub-favorites', sourceRevision] as const;
export const favoriteIdentity = (item: Pick<FavoriteSkill, 'namespace' | 'slug'>) => JSON.stringify([item.namespace, item.slug]);

/** Persist public display text only, never a version, installation token or metrics. */
export function favoriteSnapshot(item: FavoriteSkill): FavoriteSkill {
  return {
    namespace: item.namespace, slug: item.slug,
    ...(item.displayName ? { displayName: item.displayName.slice(0, 256) } : {}),
    ...(item.summary ? { summary: item.summary.slice(0, 1024) } : {}),
  };
}

export function filterFavorites(items: FavoriteSkill[], query: string): FavoriteSkill[] {
  const text = query.trim().normalize('NFKC').toLocaleLowerCase();
  return text ? items.filter(item => [item.namespace, item.slug, item.displayName, item.summary]
    .some(value => value?.normalize('NFKC').toLocaleLowerCase().includes(text))) : items;
}

/** A saved snapshot becomes a catalog selection only after a current-source resolve. */
export function resolvedFavoriteItem(item: FavoriteSkill, version: FavoriteVersion): SkillList['items'][number] {
  if (version.namespace !== item.namespace || version.slug !== item.slug || !version.version.trim()) {
    throw new Error('技能版本暂时无法读取，请重试。');
  }
  return { ...favoriteSnapshot(item), namespace: version.namespace, slug: version.slug, latestVersion: version.version, summary: item.summary ?? '' };
}
