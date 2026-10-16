import { forwardRef } from 'react';
import type { MouseEventHandler, ReactNode } from 'react';
import { Avatar, AvatarImage, AvatarFallback, Button, Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from './ui.js';
import { formatCount, skillInitial, skillTone, validCount, validRating } from './skill-presentation.js';

export function Icon({ name }: { name: 'download' | 'bookmark' | 'star' | 'arrow' | 'search' | 'grid' | 'info' | 'close' }) {
  const paths = {
    grid: 'M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z',
    info: 'M12 11v6M12 7h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
    close: 'm6 6 12 12M6 18 18 6',
    download: 'M12 3v12m-4-4 4 4 4-4M4 15v5h16v-5',
    bookmark: 'M6 3h12v18l-6-4-6 4V3Z',
    star: 'm12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9L12 3Z',
    arrow: 'M6 18 18 6M6 6h12v12',
    search: 'm16 16 5 5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  };
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
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
export function SkillMetrics({ item }: { item: SkillPresentation }) {
  return <div className="astron-sh-metrics">
    {validCount(item.downloadCount) && <span title={`下载量：${item.downloadCount.toLocaleString('zh-CN')}`} aria-label={`下载量 ${item.downloadCount}`}><Icon name="download" />{formatCount(item.downloadCount)}</span>}
    {validCount(item.starCount) && <span title={`收藏数：${item.starCount.toLocaleString('zh-CN')}`} aria-label={`收藏数 ${item.starCount}`}><Icon name="bookmark" />{formatCount(item.starCount)}</span>}
    {validRating(item.ratingAvg) && <span title={`评分：${item.ratingAvg} / 5${validCount(item.ratingCount) ? `，${item.ratingCount} 人评分` : ''}`} aria-label={`评分 ${item.ratingAvg} / 5`}><Icon name="star" />{item.ratingAvg.toFixed(1)}</span>}
  </div>;
}
export const SkillCard = forwardRef<HTMLButtonElement, {
  item: SkillPresentation; version: string; onOpen: MouseEventHandler<HTMLButtonElement>; status?: ReactNode;
  actionLabel?: string; accessibleLabel?: string; descriptionId?: string;
}>(({ item, version, onOpen, status, actionLabel = '详情', accessibleLabel, descriptionId }, ref) =>
  <Card className="astron-sh-skill-card">
    <CardHeader className="astron-sh-skill-heading"><SkillAvatar name={item.displayName} slug={item.slug} namespace={item.namespace} />
      <div><CardTitle>{item.displayName || item.slug}</CardTitle><CardDescription title={`@${item.namespace}/${item.slug}`}>@{item.namespace}/{item.slug}</CardDescription></div>
    </CardHeader>
    <CardContent className="astron-sh-skill-body"><p id={descriptionId} className="astron-sh-skill-summary" title={item.summary}>{item.summary || '暂无摘要'}</p>
      <div className="astron-sh-skill-meta"><span title={version}>v{version}</span>{status}</div></CardContent>
    <CardFooter><SkillMetrics item={item} /><Button ref={ref} className="astron-sh-card-open" variant="ghost" onClick={onOpen}
      aria-label={accessibleLabel || `查看 ${item.displayName || item.slug} @${item.namespace}/${item.slug} ${version} 详情`} aria-describedby={descriptionId}>{actionLabel}<Icon name="arrow" /></Button></CardFooter>
  </Card>);
SkillCard.displayName = 'SkillCard';
