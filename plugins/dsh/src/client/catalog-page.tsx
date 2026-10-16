import { CardSkeletons, CatalogSkeleton, DetailSkeleton } from './loading-layout.js';
import { UiAvailabilityNotice, type UiAvailability } from './ui-availability.js';
import { NativeControlStyles } from './native-control-styles.js';
import { catalogErrorMessage } from './catalog-error.js';
import { QueryClient, QueryClientProvider, useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLayoutEffect, useRef, useState, useSyncExternalStore } from 'react';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import { installedNavigationFor, type InstalledNavigation } from './installed-navigation.js';
import { InstalledSkills } from './installed-skills.js';
import type { InstalledServices } from './installed-skills.js';
import { DetailContent } from './detail-content.js';
import { InstallControl } from './install-control.js';
import type { InstallationServices } from './install-control.js';
import { SelectField, Button, Input, Tabs, TabsContent, TabsList, TabsTrigger } from './ui.js';
import { Icon, SkillAvatar, SkillCard, SkillMetrics } from './skill-card.js';
import type { InfiniteData } from '@tanstack/react-query';
import type { CatalogPageResult, SkillList } from '../host/types.js';
import '../../lib/typert.remote-client.js';

type Item = SkillList['items'][number];
type Remote = TypertRemoteNamespaceMap['skillhub'];
type Sort = 'newest' | 'downloads' | 'rating';
export interface CatalogNavigation {
  installedNavigation?: InstalledNavigation;
  installedOrigin?: { sessionId: string; sourceRevision: string };
  tab: 'browse' | 'installed'; query: string; draft: string; sort?: Sort;
  labelSelection?: { sourceRevision: string; slug: string };
  selected: Item | null; sourceRevision: string | null; scrollTop: number;
}
export const createCatalogNavigation = (): CatalogNavigation => ({
  tab: 'browse', query: '', draft: '', selected: null, sourceRevision: null, scrollTop: 0,
});
function unwrap<T>(result: { ok: true; value: T } | { ok: false; error: { code: string; details?: unknown } }): T {
  if (result.ok) return result.value;
  throw new Error(catalogErrorMessage(result.error));
}
export function CatalogPage({ remote, navigation, client, onReturn, installation, installed, availability }: {
  remote: Remote; navigation: CatalogNavigation; client: QueryClient; onReturn: () => void; installation?: InstallationServices; installed?: InstalledServices; availability: UiAvailability;
}) {
  return <QueryClientProvider client={client}><Catalog remote={remote} navigation={navigation} onReturn={onReturn} installation={installation} installed={installed} availability={availability} /></QueryClientProvider>;
}
function Catalog({ remote, navigation, onReturn, installation, installed, availability }: {
  remote: Remote; navigation: CatalogNavigation; onReturn: () => void; installation?: InstallationServices; installed?: InstalledServices; availability: UiAvailability;
}) {
  const queryClient = useQueryClient();
  const entries = useSyncExternalStore(availability.subscribe, availability.getSnapshot);
  const [sourceOpen, setSourceOpen] = useState(false);
  const [tab, setTab] = useState(navigation.tab);
  const [installedOrigin, setInstalledOrigin] = useState(navigation.installedOrigin);
  const [draft, setDraft] = useState(navigation.draft);
  const [query, setQuery] = useState(navigation.query);
  const [sort, setSort] = useState<Sort>(navigation.sort ?? 'newest');
  const [labelSelection, setLabelSelection] = useState(navigation.labelSelection);
  const [selected, setSelected] = useState(navigation.selected);
  const scroller = useRef<HTMLElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const lastCard = useRef<HTMLButtonElement | null>(null);
  const lastSelected = useRef(selected);
  const context = useQuery({ queryKey: ['skillhub-context'], queryFn: async () => unwrap(await remote.getContext()), retry: false });
  const compatibility = useQuery({ queryKey: ['skillhub-compatibility'], queryFn: async () => unwrap(await remote.getCompatibility()), retry: false });
  const revision = context.data?.sourceRevision;
  const label = labelSelection && labelSelection.sourceRevision === revision ? labelSelection.slug : '';
  const labels = useQuery({ queryKey: ['skillhub-labels', revision], enabled: !!context.data?.configured, retry: false,
    queryFn: async ({ signal }) => unwrap(await remote.listLabels(revision!, signal)) });
  useLayoutEffect(() => {
    if (revision && labelSelection && labelSelection.sourceRevision !== revision) {
      navigation.labelSelection = undefined; setLabelSelection(undefined);
      navigation.scrollTop = 0; if (scroller.current) scroller.current.scrollTop = 0;
    }
  }, [revision, labelSelection, navigation]);
  const installedNavigation = installed && revision ? installedNavigationFor(navigation, installed.sessionId, revision) : undefined;
  const sourceMatches = !selected || revision === navigation.sourceRevision;
  const listing = useInfiniteQuery({
    queryKey: ['skillhub-catalog', revision, query, sort, label], enabled: !!context.data?.configured && tab === 'browse',
    initialPageParam: 0, retry: false,
    queryFn: async ({ signal, pageParam }) => unwrap(await (label ? remote.listCatalogByLabel(revision!, query, pageParam, sort, label, signal)
      : remote.listCatalog(revision!, query, pageParam, sort, signal))),
    getNextPageParam: last => last.mode === 'paged' && last.hasMore ? last.page + 1 : undefined,
  });
  const pages = listing.data?.pages;
  const items = Array.from(new Map((pages?.flatMap(page => page.items) ?? []).map(item => [`${item.namespace}/${item.slug}`, item])).values());
  const limited = pages?.[0]?.mode === 'limited';
  const detail = useQuery({
    queryKey: ['skillhub-detail', revision, selected?.namespace, selected?.slug, selected?.latestVersion],
    enabled: !!selected && !!context.data?.configured && sourceMatches,
    queryFn: async ({ signal }) => unwrap(await remote.getSkillDetail(revision!, selected!.namespace, selected!.slug, selected!.latestVersion, signal)), retry: false,
  });
  useLayoutEffect(() => {
    if (tab !== 'browse') return;
    if (scroller.current) scroller.current.scrollTop = selected ? 0 : navigation.scrollTop;
    if (selected) heading.current?.focus();
    else lastCard.current?.focus({ preventScroll: true });
  }, [selected, navigation, tab]);
  function back() { navigation.selected = null; setSelected(null); }
  function error(message: string, retry: () => void) {
    return <div role="alert" className="astron-sh-error"><p>{message}</p><Button onClick={retry}>重试</Button></div>;
  }
  function changeTab(value: string) {
    if (tab === 'browse') navigation.scrollTop = scroller.current?.scrollTop ?? 0;
    navigation.installedOrigin = undefined; setInstalledOrigin(undefined);
    navigation.tab = value as typeof tab; setTab(value as typeof tab);
  }
  function resetListing(next: string, nextSort: Sort, nextLabel = label) {
    queryClient.setQueryData<InfiniteData<CatalogPageResult>>(['skillhub-catalog', revision, next, nextSort, nextLabel], data =>
      data ? { pages: data.pages.slice(0, 1), pageParams: data.pageParams.slice(0, 1) } : undefined);
    navigation.scrollTop = 0; if (scroller.current) scroller.current.scrollTop = 0;
  }
  function changeLabel(slug: string) {
    if (!revision || slug === label) return;
    resetListing(query, sort, slug);
    const next = { sourceRevision: revision, slug };
    navigation.labelSelection = next; setLabelSelection(next);
  }
  function search(next: string) {
    resetListing(next, sort);
    navigation.query = next; navigation.scrollTop = 0; setQuery(next);
    if (next === query) void listing.refetch();
  }
  return <section ref={scroller} className="skillhub-controls astron-sh-catalog" aria-label="SkillHub 技能广场">
    <NativeControlStyles /><UiAvailabilityNotice availability={availability} />
    <header className="astron-sh-catalog-header"><div className="astron-sh-location"><Icon name="grid" /><strong>SkillHub</strong><span>/</span><span>{selected ? '技能详情' : '技能广场'}</span></div>
      <div className="astron-sh-toolbar-actions"><Button variant="ghost" className="astron-sh-icon-button" aria-label="查看当前来源" title={context.data?.registryUrl || '尚未配置来源'} onClick={() => setSourceOpen(value => !value)}><Icon name="info" /></Button>
      {(installation || installed) && <Button variant="ghost" className="astron-sh-icon-button" aria-label="返回会话" title="关闭 SkillHub，返回会话" onClick={() => { if (!selected && tab === 'browse') navigation.scrollTop = scroller.current?.scrollTop ?? 0; onReturn(); }}><Icon name="close" /></Button>}</div>
    </header>
    {sourceOpen && <p className="astron-sh-source-info" role="status">当前来源：{context.data?.registryUrl || '尚未配置来源'}</p>}
    {(compatibility.isError || compatibility.data?.dependencyBaseline === 'unverified') && <div role="alert" className="astron-sh-error">
      <p>{compatibility.isError ? '无法读取 DSH 依赖版本核验结果。' : `当前 DSH 依赖组合未通过核验，安装、升级、卸载和恢复写入已禁用。请使用 ${compatibility.data?.candidateDshVersion} 对应的完整发行依赖。`}</p>
      <Button onClick={() => { void compatibility.refetch(); }}>重新核验版本</Button></div>}
    {context.isPending ? <CatalogSkeleton tab={tab} draft={draft} /> : context.isError ? error(context.error.message, () => { void context.refetch(); })
      : !context.data.configured ? <div className="astron-sh-empty"><h2>连接 SkillHub</h2><p>{entries.configuration ? '在 DSH 的插件页面打开 SkillHub 配置，填写来源地址并保存。' : '尚未配置技能来源，且当前配置入口不可用。请先恢复 DSH 的插件配置功能，再连接 SkillHub。'}</p><Button onClick={() => { void context.refetch(); }}>刷新连接</Button></div>
        : selected ? <>
          <Button variant="ghost" onClick={back}>← 返回技能广场</Button>
          <div className="astron-sh-detail-heading"><SkillAvatar name={sourceMatches ? detail.data?.title || selected.displayName : selected.displayName} slug={selected.slug} namespace={selected.namespace} />
            <div><h2 ref={heading} tabIndex={-1}>{sourceMatches ? detail.data?.title || selected.displayName || selected.slug : selected.displayName || selected.slug}</h2>
              <p>@{selected.namespace}/{selected.slug} · {selected.latestVersion}</p></div></div>
          <p className="astron-sh-detail-summary">{sourceMatches ? detail.data?.summary ?? selected.summary : selected.summary}</p>
          {!sourceMatches ? error('来源已变化，请返回技能广场重新选择。', back)
            : detail.isPending || detail.isFetching ? <DetailSkeleton />
              : detail.isError ? error(detail.error.message, () => { void detail.refetch(); })
                : <><SkillMetrics item={detail.data} /><div className="astron-sh-detail-layout"><article className="astron-sh-detail-main astron-sh-documentation" aria-label="技能说明">
                  <DetailContent detail={detail.data} modes={context.data.detailTabs} remote={remote} retry={() => { void detail.refetch(); }}
                    onSelectVersion={version => { const next = { ...selected, latestVersion: version }; navigation.selected = next; setSelected(next); }} />
                </article><aside className="astron-sh-detail-aside">
                  {installation ? <InstallControl key={installation.sessionId} detail={detail.data} sourceRevision={revision!} services={installation} initiatedFrom="plaza" />
                    : <div className="astron-sh-empty"><p>请先打开一个 DSH 会话，再选择安装范围。</p><Button onClick={onReturn}>前往会话</Button></div>}
                </aside></div></>}
        </> : <>
          <Tabs value={tab} onValueChange={changeTab}><TabsList className="astron-sh-directory-tabs" aria-label="技能目录"><TabsTrigger value="browse">浏览技能</TabsTrigger><TabsTrigger value="installed" onClick={() => { navigation.installedOrigin = undefined; setInstalledOrigin(undefined); }}>已安装</TabsTrigger></TabsList>
          <TabsContent value="installed">{installedOrigin && (installedOrigin.sessionId !== installed?.sessionId || installedOrigin.sourceRevision !== revision)
            ? <p role="alert">会话或来源已变化，请点击“已安装”查看当前上下文的记录。</p>
            : installed ? <InstalledSkills key={`${installed.sessionId}:${revision}`} services={installed} navigation={installedNavigation!} source={context.data.registryUrl} sourceRevision={revision!} remote={remote} modes={context.data.detailTabs} scrollContainer={scroller} />
              : <div className="astron-sh-empty"><p>请先打开一个 DSH 会话，再查看该用户和项目的安装记录。</p><Button onClick={onReturn}>前往会话</Button></div>
            }</TabsContent><TabsContent value="browse">
              <form className="astron-sh-search-form" onSubmit={event => { event.preventDefault(); search(draft.trim()); }}>
                <div className="astron-sh-search-field"><Icon name="search" /><Input aria-label="搜索技能" placeholder="搜索技能名称或用途" value={draft} onChange={event => { navigation.draft = event.target.value; setDraft(event.target.value); }} /></div>
                <Button type="submit">搜索</Button><Button onClick={() => { navigation.draft = ''; setDraft(''); search(''); }}>清空</Button>
              </form>
              {(label || !!labels.data?.items.length) && <div className="astron-sh-label-filter" role="group" aria-label="技能标签">
                <span>标签</span><Button size="sm" variant="ghost" aria-pressed={!label} onClick={() => changeLabel('')}>全部</Button>
                {labels.data?.items.map(item => <Button key={item.slug} size="sm" variant="ghost" aria-pressed={label === item.slug} onClick={() => changeLabel(item.slug)}>{item.displayName || item.slug}</Button>)}
                {label && !labels.data?.items.some(item => item.slug === label) && <Button size="sm" variant="ghost" aria-pressed="true" onClick={() => changeLabel('')}>{label} · 清除</Button>}
              </div>}
              {labels.isError && <p className="astron-sh-label-note" role="status">标签暂时无法读取。<Button size="sm" variant="ghost" disabled={labels.isFetching} onClick={() => { void labels.refetch(); }}>重试标签</Button>{label && <Button size="sm" variant="ghost" onClick={() => changeLabel('')}>清除标签筛选</Button>}</p>}
              <div className="astron-sh-result-bar"><p role="status">{pages ? `共匹配 ${pages[0]!.total} 项，当前显示 ${items.length} 项。` : '技能目录'}</p>
                {!limited && <label>排序<SelectField label="技能排序" value={sort} onValueChange={next => { const value = next as Sort; resetListing(query, value); navigation.sort = value; setSort(value); }} options={[{ value: 'newest', label: '最新发布' }, { value: 'downloads', label: '下载量' }, { value: 'rating', label: '评分' }]} /></label>}
              </div>
              {limited && <p role="status" className="astron-sh-source">当前来源仅支持首批搜索，不支持分页与排序。可输入更具体的关键词缩小范围。</p>}
              {listing.isPending ? <CardSkeletons /> : listing.isError && !pages ? error(listing.error.message, () => { void listing.refetch(); }) : <>
                {items.length === 0 ? <p className="astron-sh-empty">{listing.hasNextPage ? '此批没有可展示的已发布技能，可继续加载。' : '没有找到匹配的技能，请尝试其他关键词。'}</p>
                  : <div className="astron-sh-card-grid">{items.map(item => <SkillCard key={`${item.namespace}/${item.slug}`} item={item} version={item.latestVersion}
                    ref={node => { if (lastSelected.current?.namespace === item.namespace && lastSelected.current?.slug === item.slug) lastCard.current = node; }}
                    onOpen={event => { lastSelected.current = item; lastCard.current = event.currentTarget; navigation.scrollTop = scroller.current?.scrollTop ?? 0;
                      navigation.selected = item; navigation.sourceRevision = revision!; setSelected(item); }} />)}</div>}
                {listing.isRefetchError && <div role="alert" className="astron-sh-error"><p>刷新失败，当前保留上次加载的结果，尚未确认是否为最新内容。{listing.error.message}</p><Button disabled={listing.isFetching} onClick={() => { void listing.refetch(); }}>重新刷新</Button></div>}
                {listing.isFetchNextPageError && <p role="alert">下一批暂时无法读取，已加载的技能保留。请重试。</p>}
                {listing.hasNextPage && <div className="astron-sh-load-more"><span>已显示 {items.length} 项</span><Button disabled={listing.isFetching} onClick={() => { void listing.fetchNextPage(); }}>{listing.isFetchingNextPage ? '正在加载…' : listing.isFetchNextPageError ? '重新加载下一批' : '加载更多'}</Button></div>}
              </>}
            </TabsContent></Tabs>
        </>}
  </section>;
}
