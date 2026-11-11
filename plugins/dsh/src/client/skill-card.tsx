import { forwardRef } from 'react';
import type { MouseEventHandler, ReactNode } from 'react';
import { Avatar, AvatarImage, AvatarFallback, Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from './ui.js';
import { formatCount, skillInitial, skillTone, validCount, validRating } from './skill-presentation.js';

const iconPaths = {
  grid: 'M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z',
  info: 'M12 11v6M12 7h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
  close: 'm6 6 12 12M6 18 18 6',
  download: 'M12 3v12m-4-4 4 4 4-4M4 15v5h16v-5',
  bookmark: 'M6 3h12v18l-6-4-6 4V3Z',
  heart: 'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z',
  star: 'm12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9L12 3Z',
  arrow: 'M6 18 18 6M6 6h12v12',
  'external-link': 'M15 3h6v6m0-6-9 9M9 3H4a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-5',
  'arrow-right': 'M4 12h16m-6-6 6 6-6 6',
  'arrow-left': 'M20 12H4m6-6-6 6 6 6',
  search: 'm16 16 5 5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  upload: 'M12 16V3m-5 5 5-5 5 5M4 15v5h16v-5',
  refresh: 'M20 7v5h-5M4 17v-5h5M6 6a8 8 0 0 1 13 2l1 4M4 12l1 4a8 8 0 0 0 13 2',
  'chevron-down': 'm6 9 6 6 6-6',
  'chevron-up': 'm6 15 6-6 6 6',
  'chevron-right': 'm9 6 6 6-6 6',
  check: 'm5 12 4 4L19 6',
  'check-circle': 'm8 12 3 3 5-6M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
  warning: 'M12 3 2 21h20L12 3Zm0 6v5m0 3h.01',
  folder: 'M3 7V4h7l3 3h8v13H3V7Z',
  'folder-open': 'M3 8V4h7l3 3h7v4M3 8v12h17l3-9H8l-3 9',
  file: 'M14 2H5v20h14V7l-5-5Zm0 0v6h5M8 12h8m-8 4h8',
  'file-text': 'M14 2H5v20h14V7l-5-5Zm0 0v6h5M8 12h8m-8 4h8',
  copy: 'M9 9h12v12H9V9ZM5 15H3V3h12v2',
  plus: 'M12 5v14M5 12h14',
  trash: 'M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7',
  library: 'M4 3v18M8 3v18M12 4v17M16 4l5 16',
  settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1 1-3Z',
  'eye-off': 'm3 3 18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9 5.4A12 12 0 0 1 12 5c7 0 10 7 10 7a18 18 0 0 1-3.2 4.2M6 6.5C3.3 8.5 2 12 2 12s3 7 10 7c1.7 0 3.2-.4 4.5-1',
} as const;
export type IconName = keyof typeof iconPaths;

/** Decorative, local SVG geometry; meaningful actions retain text or an accessible button label. */
export function Icon({ name, className }: { name: IconName; className?: string }) {
  return <svg className={className} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d={iconPaths[name]} /></svg>;
}
export function SkillAvatar({ name, slug, namespace, avatarUrl }: { name?: string; slug: string; namespace: string; avatarUrl?: string }) {
  return <Avatar className={`astron-sh-tone-${skillTone(`${namespace}/${slug}`)}`}>
    {avatarUrl && <AvatarImage src={avatarUrl} alt="" />}
    <AvatarFallback>{skillInitial(name, slug)}</AvatarFallback>
  </Avatar>;
}
export interface SkillPresentation {
  namespace: string; slug: string; displayName?: string; summary?: string;
  downloadCount?: number; starCount?: number; ratingAvg?: number; ratingCount?: number;
}
export function SkillMetrics({ item, labeled = false }: { item: SkillPresentation; labeled?: boolean }) {
  return <div className={labeled ? 'astron-sh-metrics astron-sh-metrics-labeled' : 'astron-sh-metrics'}>
    {validCount(item.downloadCount) && <span title={`下载量：${item.downloadCount.toLocaleString('zh-CN')}`} aria-label={`下载量 ${item.downloadCount}`}><Icon name="download" />{labeled && '下载 '}{formatCount(item.downloadCount)}</span>}
    {validCount(item.starCount) && <span title={`市场收藏数：${item.starCount.toLocaleString('zh-CN')}`} aria-label={`市场收藏数 ${item.starCount}`}><Icon name="heart" />{labeled && '市场收藏 '}{formatCount(item.starCount)}</span>}
    {validRating(item.ratingAvg) && <span title={`评分：${item.ratingAvg} / 5${validCount(item.ratingCount) ? `，${item.ratingCount} 人评分` : ''}`} aria-label={`评分 ${item.ratingAvg} / 5`}><Icon name="star" />{labeled && '评分 '}{item.ratingAvg.toFixed(1)}</span>}
  </div>;
}
export const SkillCard = forwardRef<HTMLButtonElement, {
  item: SkillPresentation; version?: string; onOpen: MouseEventHandler<HTMLButtonElement>; status?: ReactNode;
  actions?: ReactNode; favoriteAction?: ReactNode; accessibleLabel?: string; descriptionId?: string;
}>(({ item, version, onOpen, status, actions, favoriteAction, accessibleLabel, descriptionId }, ref) => {
  const hasMetrics = validCount(item.downloadCount) || validCount(item.starCount) || validRating(item.ratingAvg);
  return <Card className="astron-sh-skill-card" data-has-metrics={hasMetrics}>
    <button ref={ref} type="button" className="astron-sh-card-open" onClick={onOpen}
      title={[item.displayName || item.slug, `@${item.namespace}/${item.slug}`, item.summary].filter(Boolean).join('\n')}
      aria-label={accessibleLabel || `查看 ${item.displayName || item.slug} @${item.namespace}/${item.slug}${version ? ` ${version}` : ''} 详情`} aria-describedby={descriptionId} />
    <CardHeader className="astron-sh-skill-heading"><SkillAvatar name={item.displayName} slug={item.slug} namespace={item.namespace} />
      <div><CardTitle title={item.displayName || item.slug}>{item.displayName || item.slug}</CardTitle><CardDescription title={`@${item.namespace}/${item.slug}`}>@{item.namespace}/{item.slug}</CardDescription></div>
      {favoriteAction}
    </CardHeader>
    <CardContent className="astron-sh-skill-body"><p id={descriptionId} className="astron-sh-skill-summary" title={item.summary}>{item.summary || '暂无摘要'}</p>
      {hasMetrics && <SkillMetrics item={item} />}
    </CardContent>
    {(version || status || actions) && <CardFooter>
      {(version || status) && <div className="astron-sh-skill-meta">{version && <span title={version}>v{version}</span>}{status}</div>}
      <div className="astron-sh-card-actions">{actions}</div>
    </CardFooter>}
  </Card>;
});
SkillCard.displayName = 'SkillCard';
