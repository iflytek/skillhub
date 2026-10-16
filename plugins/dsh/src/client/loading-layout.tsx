import { Button, Card, Input, SelectField, Skeleton, Tabs, TabsContent, TabsList, TabsTrigger } from './ui.js';

export function CardSkeletons({ message = '正在加载技能…' }: { message?: string }) {
  return <div><p role="status" className="astron-sh-loading-status">{message}</p>
    <div className="astron-sh-card-grid" aria-busy="true" aria-label="正在加载技能目录">
      {Array.from({ length: 6 }, (_, index) => <Card className="astron-sh-skeleton-card" aria-hidden="true" key={index}>
        <div className="astron-sh-skeleton-identity"><Skeleton className="astron-sh-skeleton-avatar" /><div><Skeleton className="astron-sh-skeleton-title" /><Skeleton className="astron-sh-skeleton-coordinate" /></div></div>
        <div className="astron-sh-skeleton-copy"><Skeleton /><Skeleton className="astron-sh-skeleton-short" /></div>
        <div className="astron-sh-skeleton-bottom"><Skeleton /><Skeleton /></div>
      </Card>)}
    </div></div>;
}

export function CatalogSkeleton({ tab, draft }: { tab: 'browse' | 'installed'; draft: string }) {
  return <Tabs value={tab}><TabsList className="astron-sh-directory-tabs" aria-label="技能目录">
    <TabsTrigger disabled value="browse">浏览技能</TabsTrigger><TabsTrigger disabled value="installed">已安装</TabsTrigger>
  </TabsList><TabsContent value={tab}>
    <div className="astron-sh-search-form"><div className="astron-sh-search-field"><Input disabled aria-label="搜索技能" placeholder="搜索技能名称或用途" value={draft} /></div><Button disabled>搜索</Button><Button disabled>清空</Button></div>
    <div className="astron-sh-result-bar"><span>技能目录</span><label>排序<SelectField label="技能排序" value="newest" disabled onValueChange={() => {}} options={[{ value: 'newest', label: '最新发布' }]} /></label></div>
    <CardSkeletons message="正在读取来源…" />
  </TabsContent></Tabs>;
}

export function DetailSkeleton({ message = '正在读取技能详情…' }: { message?: string }) {
  return <div><p role="status" className="astron-sh-loading-status">{message}</p>
    <div className="astron-sh-detail-layout" aria-busy="true" aria-label="正在加载技能详情">
      <Card className="astron-sh-detail-main astron-sh-skeleton-document" aria-hidden="true"><div className="astron-sh-skeleton-tabs"><Skeleton /><Skeleton /><Skeleton /><Skeleton /></div>
        <div className="astron-sh-skeleton-document-copy"><Skeleton className="astron-sh-skeleton-title" />{Array.from({ length: 7 }, (_, index) => <Skeleton key={index} className={index % 3 === 2 ? 'astron-sh-skeleton-short' : undefined} />)}</div>
      </Card><aside className="astron-sh-detail-aside" aria-hidden="true"><Card className="astron-sh-skeleton-install"><Skeleton className="astron-sh-skeleton-title" /><Skeleton /><Skeleton className="astron-sh-skeleton-input" /><Skeleton className="astron-sh-skeleton-input" /></Card></aside>
    </div></div>;
}
