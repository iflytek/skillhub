import type { InstalledSkillList } from '../host/types.js';
import { CardDescription } from './ui.js';

type Item = InstalledSkillList['items'][number];
export function installedCardVersions(items: Item[]) {
  if (items[0]?.sourceKind === 'local-import') {
    const scopes = [...new Set(items.map(item => item.scope === 'user' ? '所有项目' : '仅此项目'))].join(' · ');
    return { label: scopes, title: scopes };
  }
  const versions = [...new Set(items.map(item => item.version))];
  return { label: versions.length === 1 ? `v${versions[0]}` : `多个版本 · ${versions.map(version => `v${version}`).join(' / ')}`,
    title: items.map(item => `${item.scope === 'user' ? '所有项目' : '仅此项目'} · v${item.version}`).join('；') };
}
export function InstalledCardMetadata({ items }: { items: Item[] }) {
  const identity = items[0];
  if (!identity) return null;
  const versions = installedCardVersions(items);
  return <CardDescription className="skillhub-installed-version" title={identity.sourceKind === 'local-import' ? versions.title : `@${identity.namespace}/${identity.slug} · ${versions.title}`}>
    {identity.sourceKind === 'local-import' ? versions.label : `${versions.label} · @${identity.namespace}/${identity.slug}`}
  </CardDescription>;
}
