import { useState } from 'react';
import { Tabs, TabsList, TabsTrigger, TabsContent, Card, Button } from './ui.js';
import { SkillAvatar, Icon } from './skill-card.js';
import { readableDate, validRating, validCount } from './skill-presentation.js';
import { useQuery } from '@tanstack/react-query';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { SkillDetail, SkillHubConfig } from '../host/types.js';
import type { ReactNode } from 'react';
import '../../lib/typert.remote-client.js';

type Remote = TypertRemoteNamespaceMap['skillhub'];
type Tab = 'details' | 'versions' | 'securityAudit' | 'reviews';
const names: Record<Tab, string> = { details: '详情', versions: '版本', securityAudit: '安全审核', reviews: '评论' };
export function DetailTabs({ detail, modes, remote, children, onSelectVersion, versionActionLabel }: {
  detail: SkillDetail; modes: SkillHubConfig['detailTabs']; remote: Remote; children: ReactNode; onSelectVersion?(version: string): void; versionActionLabel?: string;
}) {
  const [selected, setSelected] = useState<Tab>('details');
  const visible = (Object.keys(names) as Tab[]).filter(tab => tab === 'details'
    || modes[tab] === 'show' || (modes[tab] === 'auto' && detail.tabs[tab] === 'available'));
  const active = visible.includes(selected) ? selected : 'details';
  return <Card className="astron-sh-reading-card"><Tabs value={active} onValueChange={value => setSelected(value as Tab)}>
    <TabsList aria-label="技能详情">{visible.map(tab => <TabsTrigger key={tab} value={tab}>{names[tab]}</TabsTrigger>)}</TabsList>
    {visible.map(tab => <TabsContent key={tab} value={tab}>
      {active !== tab ? null : tab === 'details' ? <><div className="astron-sh-reading-body">{children}</div><footer className="astron-sh-panel-footer"><span>SKILL.md</span><span>当前版本 {detail.version}</span></footer></> : tab === 'securityAudit' ? <><div className="astron-sh-reading-body astron-sh-audit-empty"><Icon name="info" /><h3>当前来源未提供公开审核信息</h3><p>当前来源的审核信息不支持匿名读取。</p><p>请检查技能内容与来源，不能据此判断技能已通过安全审核。</p></div><footer className="astron-sh-panel-footer">审核状态取决于当前来源提供的信息</footer></>
        : <>
          <ModulePage key={`${detail.detailToken}/${tab}`} tab={tab} token={detail.detailToken} remote={remote} detail={detail} onSelectVersion={onSelectVersion} versionActionLabel={versionActionLabel} /></>}
    </TabsContent>)}
  </Tabs></Card>;
}

function ModulePage({ tab, token, remote, detail, onSelectVersion, versionActionLabel }: { tab: 'versions' | 'reviews'; token: string; remote: Remote; detail: SkillDetail; onSelectVersion?(version: string): void; versionActionLabel?: string }) {
  const [page, setPage] = useState(0);
  const result = useQuery({ queryKey: ['skillhub-module', token, tab, page], retry: false,
    queryFn: async ({ signal }) => {
      const response = tab === 'versions' ? await remote.listVersions(token, page, signal) : await remote.listReviews(token, page, signal);
      if (!response.ok) {
        const error = response.error;
        if (error.code === 'skillhub/registry-error') {
          if (error.details.reason === 'ANONYMOUS_DENIED') throw new Error('当前来源不允许匿名读取此项信息。');
          if (error.details.reason === 'NOT_FOUND') throw new Error('未找到此项信息，无法判断是内容不存在还是来源尚未支持。');
        }
        throw new Error('此项信息暂时无法读取，请重试；若来源或配置已变化，请重新打开详情。');
      }
      return response.value;
    },
  });
  if (result.isPending) return <div className="astron-sh-reading-body" role="status">正在读取{names[tab]}…</div>;
  if (result.isError) return <div className="astron-sh-reading-body" role="alert"><p>{result.error.message}</p><Button onClick={() => { void result.refetch(); }}>重试</Button></div>;
  const data = result.data;
  return <>
    <div className="astron-sh-reading-body">
      {tab === 'reviews' && <div className="astron-sh-review-overview"><div><strong>{validRating(detail.ratingAvg) ? detail.ratingAvg.toFixed(1) : '—'}</strong><div>
        {validRating(detail.ratingAvg) && <Rating score={detail.ratingAvg} />}
        <p>{validCount(detail.ratingCount) ? `${detail.ratingCount} 人评分` : '评分人数未提供'} · 共 {data.total} 条评论</p>
      </div></div><span className="astron-sh-read-only">只读展示</span></div>}
      {data.items.length === 0 ? <div className="astron-sh-module-empty"><Icon name="info" /><h3>暂无{names[tab]}信息。</h3><p>{tab === 'reviews' ? '此技能的使用反馈将显示在这里。' : '当前来源暂未返回版本记录。'}</p></div> : data.items.map(item => 'version' in item
        ? <article key={item.version} className="astron-sh-version-row"><span className="astron-sh-version-dot" /><div><h3>{item.version}{item.version === detail.version && <span className="astron-sh-version-current">当前查看</span>}</h3>
          <p className="astron-sh-review-text">{item.changelog || '暂无发布说明'}</p><p className="astron-sh-version-meta"><time dateTime={item.publishedAt ?? undefined}>{readableDate(item.publishedAt)}</time> · {item.downloadAvailable ? '可下载' : '不可下载'}</p>{onSelectVersion && <Button variant="outline" disabled={!item.downloadAvailable || (!versionActionLabel && item.version === detail.version)} onClick={() => onSelectVersion(item.version)}>{versionActionLabel || (item.version === detail.version ? '当前查看版本' : '查看此版本')}</Button>}</div></article>
        : <article key={item.id} className="astron-sh-review"><SkillAvatar name={item.displayName || '用户'} slug={String(item.id)} namespace="reviews" /><div>
          <header><strong>{item.displayName || '用户'}</strong><time dateTime={item.createdAt}>{readableDate(item.createdAt)}</time></header>
          <Rating score={item.score} /><p className="astron-sh-review-text">{item.reviewText || '未填写评论内容'}</p>
        </div></article>)}
      {tab === 'reviews' && <p className="astron-sh-review-note"><Icon name="info" />评论针对整个技能，不限定当前版本；不代表安全审核结论。</p>}
    </div>
    <nav aria-label={`${names[tab]}分页`} className="astron-sh-panel-footer">
      <span role="status">第 {page + 1} 页 · 共 {data.total} 项</span><div>
      <Button variant="ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>上一页</Button>
      <Button variant="ghost" disabled={(page + 1) * data.size >= data.total} onClick={() => setPage(page + 1)}>下一页</Button>
      </div>
    </nav>
  </>;
}
function Rating({ score }: { score: number }) {
  return <span className="astron-sh-stars" aria-label={`评分 ${score} / 5`} title={`${score} / 5`}>
    {[1, 2, 3, 4, 5].map(value => <span key={value} data-filled={value <= Math.round(score)}><Icon name="star" /></span>)}
    <span className="astron-sh-rating-number">{score.toFixed(1)}</span>
  </span>;
}
