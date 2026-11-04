import { sameRegistry } from './registry-config.js';
import { SkillWebsiteLink } from './skill-website-link.js';
import { UpgradeControl } from './upgrade-control.js';
import { RemovalControl } from './removal-control.js';
import { SkillInstallDialog } from './install-dialog.js';
import { readCacheOptions } from './read-cache.js';
import { catalogLabel } from './catalog-label.js';
import { InstallationManagement } from './management-tool-view.js';
import { MarketCardActions } from './market-card-actions.js';
import { CardSkeletons, CatalogSkeleton, DetailSkeleton } from './loading-layout.js';
import { UiAvailabilityNotice, type UiAvailability } from './ui-availability.js';
import { NativeControlStyles } from './native-control-styles.js';
import { catalogErrorMessage } from './catalog-error.js';
import { FavoriteButton } from './favorite-button.js';
import { favoriteIdentity, filterFavorites, resolvedFavoriteItem } from './favorites.js';
import { useFavorites } from './use-favorites.js';
import { QueryClient, QueryClientProvider, useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLayoutEffect, useRef, useState, useSyncExternalStore } from 'react';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import { installedNavigationFor, type InstalledNavigation } from './installed-navigation.js';
import { InstalledSkills } from './installed-skills.js';
import type { InstalledServices } from './installed-skills.js';
import { DetailContent } from './detail-content.js';
import type { InstallationServices } from './install-control.js';
import { SelectField, Button, Input, Tabs, TabsContent, TabsList, TabsTrigger } from './ui.js';
import { Icon, SkillAvatar, SkillCard, SkillMetrics } from './skill-card.js';
import type { InfiniteData } from '@tanstack/react-query';
import type { CatalogPageResult, SkillList, InstalledSkillList, FavoriteSkill } from '../host/types.js';
import { skillInstallationSummary } from './skill-primary-actions.js';
import '../../lib/typert.remote-client.js';

type Item = SkillList['items'][number];
type Remote = TypertRemoteNamespaceMap['skillhub'];
type Sort = 'newest' | 'downloads' | 'rating';
export interface CatalogNavigation {
  installedNavigation?: InstalledNavigation;
  installedOrigin?: { sessionId: string; sourceRevision: string };
  tab: 'browse' | 'installed'; query: string; draft: string; sort?: Sort;
  labelSelection?: { sourceRevision: string; slug: string };
  favoriteOnly?: boolean; favoriteQuery?: string; favoriteDraft?: string; favoriteScrollTop?: number;
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
  const [upgradeCandidate, setUpgradeCandidate] = useState<{ item: InstalledSkillList['items'][number]; revision: string; source: string } | null>(null);
  const [removalCandidate, setRemovalCandidate] = useState<{ item: InstalledSkillList['items'][number]; revision: string; source: string } | null>(null);
  const [installCandidate, setInstallCandidate] = useState<{ item: Item; revision: string } | null>(null);
  const [tab, setTab] = useState(navigation.tab);
  const [installedOrigin, setInstalledOrigin] = useState(navigation.installedOrigin);
  const [draft, setDraft] = useState(navigation.draft);
  const [query, setQuery] = useState(navigation.query);
  const [favoriteOnly, setFavoriteOnly] = useState(navigation.favoriteOnly ?? false);
  const [favoriteQuery, setFavoriteQuery] = useState(navigation.favoriteQuery ?? '');
  const [favoriteDraft, setFavoriteDraft] = useState(navigation.favoriteDraft ?? '');
  const [favoriteOpening, setFavoriteOpening] = useState<{ revision: string; key: string } | null>(null);
  const [favoriteOpenError, setFavoriteOpenError] = useState<{ revision: string; item: FavoriteSkill; intent: 'detail' | 'install'; message: string } | null>(null);
  const [sort, setSort] = useState<Sort>(navigation.sort ?? 'newest');
  const [labelSelection, setLabelSelection] = useState(navigation.labelSelection);
  const [selected, setSelected] = useState(navigation.selected);
  const scroller = useRef<HTMLElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const lastCard = useRef<HTMLButtonElement | null>(null);
  const lastSelected = useRef(selected);
  const favoriteFilter = useRef<HTMLButtonElement>(null);
  const previousFavoriteOnly = useRef(favoriteOnly);
  const favoriteRequest = useRef<AbortController | null>(null);
  const context = useQuery({ queryKey: ['skillhub-context'], queryFn: async () => unwrap(await remote.getContext()), retry: false });
  const compatibility = useQuery({ queryKey: ['skillhub-compatibility'], queryFn: async () => unwrap(await remote.getCompatibility()), retry: false });
  const revision = context.data?.sourceRevision;
  const latestRevision = useRef(revision); latestRevision.current = revision;
  const favorites = useFavorites(remote, revision, !!context.data?.configured && tab === 'browse');
  const favoriteItems = useRef(favorites.items); favoriteItems.current = favorites.items;
  useLayoutEffect(() => () => { favoriteRequest.current?.abort(); favoriteRequest.current = null; }, [revision]);
  const previousRevision = useRef(revision);
  useLayoutEffect(() => {
    if (previousRevision.current && revision && previousRevision.current !== revision) {
      setInstallCandidate(null); setUpgradeCandidate(null); setRemovalCandidate(null);
      navigation.selected = null; setSelected(null);
      navigation.installedOrigin = undefined; setInstalledOrigin(undefined);
      setFavoriteOpening(null); setFavoriteOpenError(null);
    }
    previousRevision.current = revision;
  }, [revision, navigation]);
  // Share the installed tab's cache and settled-operation invalidation, without blocking market reads.
  const inventory = useQuery({ queryKey: ['skillhub-installed', installed?.sessionId, revision],
    enabled: !!installed && !!revision && !!context.data?.configured && tab === 'browse', retry: false,
    queryFn: async ({ signal }) => unwrap(await installed!.list(revision!, signal)) });
  const label = labelSelection && labelSelection.sourceRevision === revision ? labelSelection.slug : '';
  const labels = useQuery({ ...readCacheOptions, queryKey: ['skillhub-labels', revision], enabled: !!context.data?.configured && !favoriteOnly && tab === 'browse', retry: false,
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
    ...readCacheOptions,
    queryKey: ['skillhub-catalog', revision, query, sort, label], enabled: !!context.data?.configured && tab === 'browse' && !favoriteOnly,
    initialPageParam: 0, retry: false,
    queryFn: async ({ signal, pageParam }) => unwrap(await (label ? remote.listCatalogByLabel(revision!, query, pageParam, sort, label, signal)
      : remote.listCatalog(revision!, query, pageParam, sort, signal))),
    getNextPageParam: last => last.mode === 'paged' && last.hasMore ? last.page + 1 : undefined,
  });
  const pages = listing.data?.pages;
  const items = Array.from(new Map((pages?.flatMap(page => page.items) ?? []).map(item => [`${item.namespace}/${item.slug}`, item])).values());
  const limited = pages?.[0]?.mode === 'limited';
  const visibleFavorites = filterFavorites(favorites.items, favoriteQuery);
  const detail = useQuery({
    ...readCacheOptions,
    queryKey: ['skillhub-detail', revision, selected?.namespace, selected?.slug, selected?.latestVersion],
    enabled: !!selected && !!context.data?.configured && sourceMatches,
    queryFn: async ({ signal }) => unwrap(await remote.getSkillDetail(revision!, selected!.namespace, selected!.slug, selected!.latestVersion, signal)), retry: false,
  });
  useLayoutEffect(() => {
    if (tab !== 'browse') return;
    const filterChanged = previousFavoriteOnly.current !== favoriteOnly; previousFavoriteOnly.current = favoriteOnly;
    if (scroller.current) scroller.current.scrollTop = selected ? 0 : favoriteOnly ? navigation.favoriteScrollTop ?? 0 : navigation.scrollTop;
    if (selected) heading.current?.focus();
    else if (!filterChanged) lastCard.current?.focus({ preventScroll: true });
  }, [selected, navigation, tab, favoriteOnly]);
  function back() { navigation.selected = null; setSelected(null); }
  function rememberScroll() {
    const top = scroller.current?.scrollTop ?? 0;
    if (favoriteOnly) navigation.favoriteScrollTop = top;
    else navigation.scrollTop = top;
  }
  function cancelFavoriteOpen() {
    favoriteRequest.current?.abort(); favoriteRequest.current = null;
    setFavoriteOpening(null); setFavoriteOpenError(null);
  }
  function openSkill(item: Item, button?: HTMLButtonElement) {
    cancelFavoriteOpen();
    lastSelected.current = item;
    if (button) lastCard.current = button;
    rememberScroll();
    navigation.selected = item; navigation.sourceRevision = revision!;
    setSelected(item);
  }
  async function openFavorite(item: FavoriteSkill, intent: 'detail' | 'install', button?: HTMLButtonElement) {
    if (!revision) return;
    const captured = revision; cancelFavoriteOpen();
    const controller = new AbortController(); favoriteRequest.current = controller;
    setFavoriteOpening({ revision: captured, key: favoriteIdentity(item) });
    try {
      const resolved = unwrap(await remote.resolveFavoriteVersion(captured, item.namespace, item.slug, controller.signal));
      if (controller.signal.aborted || favoriteRequest.current !== controller || latestRevision.current !== captured) return;
      if (!favoriteItems.current.some(saved => favoriteIdentity(saved) === favoriteIdentity(item))) return;
      const current = resolvedFavoriteItem(item, resolved);
      if (intent === 'install') setInstallCandidate({ item: current, revision: captured });
      else openSkill(current, button);
    } catch (failure) {
      if (!controller.signal.aborted && favoriteRequest.current === controller && latestRevision.current === captured) {
        setFavoriteOpenError({ revision: captured, item, intent, message: failure instanceof Error ? failure.message : '技能暂时无法读取，请重试。' });
      }
    } finally {
      if (favoriteRequest.current === controller) { favoriteRequest.current = null; setFavoriteOpening(null); }
    }
  }
  function favoriteAction(item: FavoriteSkill) {
    const pressed = favorites.items.some(saved => favoriteIdentity(saved) === favoriteIdentity(item));
    return <FavoriteButton item={item} pressed={pressed} disabled={!favorites.ready} busy={favorites.loading || favorites.saving}
      onToggle={() => {
        if (pressed && favoriteOpening?.key === favoriteIdentity(item)) cancelFavoriteOpen();
        void favorites.save(item, !pressed).then(saved => { if (saved && favoriteOnly && pressed) favoriteFilter.current?.focus({ preventScroll: true }); });
      }} />;
  }
  function manageSkill(item: NonNullable<typeof inventory.data>['items'][number], intent: 'remove' | 'upgrade') {
    if (intent === 'remove') { if (revision && context.data) setRemovalCandidate({ item, revision, source: item.registry ?? context.data.registryUrl }); return; }
    if (revision && context.data) setUpgradeCandidate({ item, revision, source: item.registry ?? context.data.registryUrl });
  }
  function error(message: string, retry: () => void) {
    return <div role="alert" className="astron-sh-error"><p>{message}</p><Button onClick={retry}>重试</Button></div>;
  }
  function changeTab(value: string) {
    cancelFavoriteOpen();
    if (tab === 'browse') rememberScroll();
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
  function searchFavorites(next: string) {
    navigation.favoriteQuery = next; navigation.favoriteScrollTop = 0; setFavoriteQuery(next);
    if (scroller.current) scroller.current.scrollTop = 0;
  }
  function toggleFavorites() {
    cancelFavoriteOpen(); rememberScroll();
    navigation.favoriteOnly = !favoriteOnly; setFavoriteOnly(!favoriteOnly);
  }
  return <section ref={scroller} className="skillhub-controls astron-sh-catalog" aria-label="SkillHub 技能广场">
    <NativeControlStyles /><UiAvailabilityNotice availability={availability} />
    {upgradeCandidate && installed && <UpgradeControl key={`${installed.sessionId}:${upgradeCandidate.revision}:${upgradeCandidate.item.itemKey}`} presentation="dialog" eligible
      services={installed} sourceRevision={upgradeCandidate.revision} source={upgradeCandidate.source} item={upgradeCandidate.item} onClosed={() => setUpgradeCandidate(null)} />}
    {removalCandidate && installed && <RemovalControl key={`${installed.sessionId}:${removalCandidate.revision}:${removalCandidate.item.itemKey}`} presentation="dialog"
      services={installed} sourceRevision={removalCandidate.revision} source={removalCandidate.source} item={removalCandidate.item}
      eligible={['present', 'missing'].includes(removalCandidate.item.state)} onClosed={() => setRemovalCandidate(null)} />}
    {installCandidate && installation && <SkillInstallDialog client={queryClient} identity={JSON.stringify([installCandidate.revision, installCandidate.item.namespace, installCandidate.item.slug, installCandidate.item.latestVersion])}
      services={installation} initiatedFrom="plaza" close={() => setInstallCandidate(null)} onManage={item => { setInstallCandidate(null); manageSkill(item, 'remove'); }} load={async signal => {
        const source = unwrap(await remote.getContext());
        if (source.sourceRevision !== installCandidate.revision) throw new Error('来源已变化，请重新选择。');
        const detail = unwrap(await remote.getSkillDetail(installCandidate.revision, installCandidate.item.namespace, installCandidate.item.slug, installCandidate.item.latestVersion, signal));
        return { detail, sourceRevision: source.sourceRevision };
      }} />}

    <header className="astron-sh-catalog-header"><div className="astron-sh-location"><Icon name="grid" /><strong>SkillHub</strong><span>/</span><span>{selected ? '技能详情' : '技能广场'}</span></div>
      <div className="astron-sh-toolbar-actions"><Button variant="ghost" className="astron-sh-icon-button" aria-label="查看当前来源" title={context.data?.registryUrl || '尚未配置来源'} onClick={() => setSourceOpen(value => !value)}><Icon name="info" /></Button>
      {(installation || installed) && <Button variant="ghost" className="astron-sh-icon-button" aria-label="返回会话" title="关闭 SkillHub，返回会话" onClick={() => { cancelFavoriteOpen(); if (!selected && tab === 'browse') rememberScroll(); onReturn(); }}><Icon name="close" /></Button>}</div>
    </header>
    {sourceOpen && <p className="astron-sh-source-info" role="status">当前来源：{context.data?.registryUrl || '尚未配置来源'}</p>}
    {(compatibility.isError || compatibility.data?.dependencyBaseline === 'unverified') && <div role="alert" className="astron-sh-error">
      <p>{compatibility.isError ? '无法读取 DSH 依赖版本核验结果。' : `当前 DSH 依赖组合未通过核验，安装、升级、卸载和恢复写入已禁用。请使用 ${compatibility.data?.candidateDshVersion} 对应的完整发行依赖。`}</p>
      <Button onClick={() => { void compatibility.refetch(); }}>重新核验版本</Button></div>}
    {context.isPending ? <CatalogSkeleton tab={tab} draft={draft} /> : context.isError ? error(context.error.message, () => { void context.refetch(); })
      : selected && context.data.configured ? <>
          <Button variant="ghost" onClick={back}>← 返回技能广场</Button>
          <div className="astron-sh-detail-heading"><SkillAvatar name={sourceMatches ? detail.data?.title || selected.displayName : selected.displayName} slug={selected.slug} namespace={selected.namespace} />
            <div><h2 ref={heading} tabIndex={-1}>{sourceMatches ? detail.data?.title || selected.displayName || selected.slug : selected.displayName || selected.slug}</h2>
              <p>@{selected.namespace}/{selected.slug} · {selected.latestVersion}</p></div>
            <div className="astron-sh-detail-actions">{sourceMatches && favoriteAction({ ...selected, displayName: detail.data?.title || selected.displayName, summary: detail.data?.summary ?? selected.summary })}
            <SkillWebsiteLink webUrl={detail.data?.webUrl}
              enabled={sourceMatches && !detail.isError && !!detail.data && detail.data.namespace === selected.namespace && detail.data.slug === selected.slug} /></div></div>
          {favorites.error && <div role="alert" className="astron-sh-favorite-feedback"><p>{favorites.error}</p><Button size="sm" variant="ghost" disabled={favorites.fetching || favorites.saving} onClick={favorites.retry}>重新读取收藏</Button></div>}
          <p className="astron-sh-detail-summary">{sourceMatches ? detail.data?.summary ?? selected.summary : selected.summary}</p>
          {!sourceMatches ? error('来源已变化，请返回技能广场重新选择。', back)
            : detail.isPending ? <DetailSkeleton />
              : detail.isError ? error(detail.error.message, () => { void detail.refetch(); })
                 : <><SkillMetrics item={detail.data} />
                  {installation && installed && <MarketCardActions label="使用已安装版本" records={inventory.data?.items.filter(item => sameRegistry(item.registry, context.data.registryUrl) && item.namespace === selected.namespace && item.slug === selected.slug) ?? []}
                    sourceRevision={revision!} services={installed} verified={!!inventory.data && !inventory.isError && inventory.data.complete && !inventory.data.unavailable.length}
                    onInstall={() => setInstallCandidate({ item: selected, revision: revision! })} />}
                  <div className="astron-sh-detail-layout"><article className="astron-sh-detail-main astron-sh-documentation" aria-label="技能说明">
                  <DetailContent detail={detail.data} modes={context.data.detailTabs} remote={remote} retry={() => { void detail.refetch(); }}
                    cacheIdentity={JSON.stringify([installation?.sessionId ?? installed?.sessionId, revision, selected.namespace, selected.slug, selected.latestVersion])}
                    onContextStale={() => { void detail.refetch(); }}
                    onSelectVersion={version => { const next = { ...selected, latestVersion: version }; navigation.selected = next; setSelected(next); }} />
                </article><aside className="astron-sh-detail-aside">
                  {installed?.management ? <InstallationManagement identity={{ ...selected, registry: context.data.registryUrl }} revision={revision!} services={installed.management} useSkill={installed.skillUse}
                    onInstall={inventory.data?.items.some(item => sameRegistry(item.registry, context.data.registryUrl) && item.namespace === selected.namespace && item.slug === selected.slug) ? () => setInstallCandidate({ item: selected, revision: revision! }) : undefined} />
                    : <div className="astron-sh-empty"><p>请先打开一个 DSH 会话。</p><Button onClick={onReturn}>前往会话</Button></div>}
                </aside></div></>}
        </> : <>
          <Tabs value={tab} onValueChange={changeTab}><TabsList className="astron-sh-directory-tabs" aria-label="技能目录"><TabsTrigger value="browse">浏览技能</TabsTrigger><TabsTrigger value="installed" onClick={() => { navigation.installedOrigin = undefined; setInstalledOrigin(undefined); }}>已安装</TabsTrigger></TabsList>
          <TabsContent value="installed">{installedOrigin && (installedOrigin.sessionId !== installed?.sessionId || installedOrigin.sourceRevision !== revision)
            ? <p role="alert">会话或来源已变化，请点击“已安装”查看当前上下文的记录。</p>
            : installed ? <InstalledSkills key={`${installed.sessionId}:${revision}`} services={installed} onInstall={item => setInstallCandidate({ item: { namespace: item.namespace, slug: item.slug, latestVersion: item.version, summary: '' }, revision: revision! })} onRemove={item => manageSkill(item, 'remove')} onUpgrade={item => manageSkill(item, 'upgrade')} navigation={installedNavigation!} source={context.data.registryUrl} sourceRevision={revision!} remote={remote} modes={context.data.detailTabs} scrollContainer={scroller} />
              : <div className="astron-sh-empty"><p>请先打开一个 DSH 会话，再查看该用户和项目的安装记录。</p><Button onClick={onReturn}>前往会话</Button></div>
            }</TabsContent><TabsContent value="browse">
              {!context.data.configured ? <div className="astron-sh-empty"><h2>连接 SkillHub</h2><p>{entries.configuration ? '在 DSH 插件页面的 SkillHub 设置中配置技能来源。' : '来源配置暂不可用，已安装技能仍可管理。'}</p><Button onClick={() => { void context.refetch(); }}>刷新连接</Button></div> : <>
              <form className="astron-sh-search-form" onSubmit={event => { event.preventDefault(); if (favoriteOnly) searchFavorites(favoriteDraft.trim()); else search(draft.trim()); }}>
                <div className="astron-sh-search-field"><Icon name="search" /><Input aria-label={favoriteOnly ? '搜索本机收藏' : '搜索技能'} placeholder={favoriteOnly ? '搜索收藏的技能' : '搜索技能名称或用途'} value={favoriteOnly ? favoriteDraft : draft} onChange={event => {
                  if (favoriteOnly) { navigation.favoriteDraft = event.target.value; setFavoriteDraft(event.target.value); }
                  else { navigation.draft = event.target.value; setDraft(event.target.value); }
                }} /></div>
                <Button type="submit">搜索</Button><Button onClick={() => {
                  if (favoriteOnly) { navigation.favoriteDraft = ''; setFavoriteDraft(''); searchFavorites(''); }
                  else { navigation.draft = ''; setDraft(''); search(''); }
                }}>清空</Button>
              </form>
              {!favoriteOnly && (label || !!labels.data?.items.length) && <div className="astron-sh-label-filter" role="group" aria-label="技能标签">
                <span>标签</span><Button size="sm" variant="ghost" aria-pressed={!label} onClick={() => changeLabel('')}>全部</Button>
                {labels.data?.items.map(item => <Button key={item.slug} size="sm" variant="ghost" aria-pressed={label === item.slug} onClick={() => changeLabel(item.slug)}>{catalogLabel(item)}</Button>)}
                {label && !labels.data?.items.some(item => item.slug === label) && <Button size="sm" variant="ghost" aria-pressed="true" onClick={() => changeLabel('')}>{label} · 清除</Button>}
              </div>}
              {!favoriteOnly && labels.isError && <p className="astron-sh-label-note" role="status">标签暂时无法读取。<Button size="sm" variant="ghost" disabled={labels.isFetching} onClick={() => { void labels.refetch(); }}>重试标签</Button>{label && <Button size="sm" variant="ghost" onClick={() => changeLabel('')}>清除标签筛选</Button>}</p>}
              <div className="astron-sh-result-bar"><div className="astron-sh-result-summary"><Button ref={favoriteFilter} size="sm" variant={favoriteOnly ? 'outline' : 'ghost'} className="astron-sh-favorite-filter" aria-pressed={favoriteOnly} onClick={toggleFavorites}><Icon name="bookmark" />仅看收藏</Button>
                <p role="status">{favoriteOnly ? favorites.ready ? `${visibleFavorites.length} 项收藏` : favorites.loading ? '正在读取收藏…' : '本机收藏' : pages ? `共匹配 ${pages[0]!.total} 项，当前显示 ${items.length} 项。` : '技能目录'}</p></div>
                {!favoriteOnly && !limited && <label>排序<SelectField label="技能排序" value={sort} onValueChange={next => { const value = next as Sort; resetListing(query, value); navigation.sort = value; setSort(value); }} options={[{ value: 'newest', label: '最新发布' }, { value: 'downloads', label: '下载量' }, { value: 'rating', label: '评分' }]} /></label>}
              </div>
              {favorites.error && <div role="alert" className="astron-sh-favorite-feedback"><p>{favorites.error}</p><Button size="sm" variant="ghost" disabled={favorites.fetching || favorites.saving} onClick={favorites.retry}>重新读取收藏</Button></div>}
              {favoriteOpening?.revision === revision && <p role="status" className="astron-sh-label-note">正在读取所选技能…</p>}
              {favoriteOpenError && favoriteOpenError.revision === revision && <div role="alert" className="astron-sh-favorite-feedback"><p>{favoriteOpenError.message}</p><Button size="sm" variant="ghost" onClick={() => { void openFavorite(favoriteOpenError.item, favoriteOpenError.intent); }}>重试</Button><Button size="sm" variant="ghost" onClick={() => setFavoriteOpenError(null)}>关闭</Button></div>}
              {installed && inventory.isPending && !inventory.data && <p className="astron-sh-label-note" role="status">正在核对本地安装状态，完成后可安装或管理技能。</p>}
              {installed && !inventory.isFetching && (inventory.isError || !inventory.data || !inventory.data.complete || inventory.data.unavailable.length > 0) && <p className="astron-sh-label-note" role="status">部分本地安装状态暂时无法核实，安装和管理操作暂不可用。<Button size="sm" variant="ghost" onClick={() => { void inventory.refetch(); }}>重新核对</Button></p>}
              {!favoriteOnly && limited && <p role="status" className="astron-sh-source">当前来源仅支持首批搜索，不支持分页与排序。可输入更具体的关键词缩小范围。</p>}
              {favoriteOnly ? favorites.loading ? <CardSkeletons /> : !favorites.ready ? null : visibleFavorites.length === 0
                ? <div className="astron-sh-empty"><p>{favorites.items.length ? '没有匹配的收藏。' : '当前来源还没有收藏。'}</p>{!favorites.items.length && <Button variant="outline" onClick={toggleFavorites}>浏览技能</Button>}</div>
                : <div className="astron-sh-card-grid">{visibleFavorites.map(item => <SkillCard key={favoriteIdentity(item)} item={item} favoriteAction={favoriteAction(item)}
                  status={inventory.data && !inventory.isError && inventory.data.items.some(record => sameRegistry(record.registry, context.data.registryUrl) && record.namespace === item.namespace && record.slug === item.slug)
                    ? <span>{skillInstallationSummary(inventory.data.items.filter(record => sameRegistry(record.registry, context.data.registryUrl) && record.namespace === item.namespace && record.slug === item.slug))}</span> : undefined}
                  actions={<MarketCardActions records={inventory.data?.items.filter(record => sameRegistry(record.registry, context.data.registryUrl) && record.namespace === item.namespace && record.slug === item.slug) ?? []}
                    services={installed} sourceRevision={revision!} verified={!!inventory.data && !inventory.isError && inventory.data.complete && !inventory.data.unavailable.length}
                    onInstall={() => { void openFavorite(item, 'install'); }} onManage={() => { void openFavorite(item, 'detail'); }} />}
                  ref={node => { if (lastSelected.current?.namespace === item.namespace && lastSelected.current?.slug === item.slug) lastCard.current = node; }}
                  onOpen={event => { void openFavorite(item, 'detail', event.currentTarget); }} />)}</div>
              : listing.isPending ? <CardSkeletons /> : listing.isError && !pages ? error(listing.error.message, () => { void listing.refetch(); }) : <>
                {items.length === 0 ? <p className="astron-sh-empty">{listing.hasNextPage ? '此批没有可展示的已发布技能，可继续加载。' : '没有找到匹配的技能，请尝试其他关键词。'}</p>
                  : <div className="astron-sh-card-grid">{items.map(item => <SkillCard key={`${item.namespace}/${item.slug}`} item={item} version={item.latestVersion} favoriteAction={favoriteAction(item)}
                    status={inventory.data && !inventory.isError
                      && inventory.data.items.some(record => sameRegistry(record.registry, context.data.registryUrl) && record.namespace === item.namespace && record.slug === item.slug)
                      ? <span>{skillInstallationSummary(inventory.data.items.filter(record => sameRegistry(record.registry, context.data.registryUrl) && record.namespace === item.namespace && record.slug === item.slug))}</span> : undefined}
                    actions={<MarketCardActions records={inventory.data?.items.filter(record => sameRegistry(record.registry, context.data.registryUrl) && record.namespace === item.namespace && record.slug === item.slug) ?? []}
                      services={installed} sourceRevision={revision!}
                      verified={!!inventory.data && !inventory.isError && inventory.data.complete && !inventory.data.unavailable.length}
                      onInstall={() => revision && setInstallCandidate({ item, revision })} onManage={() => openSkill(item)} />}
                    ref={node => { if (lastSelected.current?.namespace === item.namespace && lastSelected.current?.slug === item.slug) lastCard.current = node; }}
                    onOpen={event => openSkill(item, event.currentTarget)} />)}</div>}
                {listing.isRefetchError && <div role="alert" className="astron-sh-error"><p>刷新失败，当前保留上次加载的结果，尚未确认是否为最新内容。{listing.error.message}</p><Button disabled={listing.isFetching} onClick={() => { void listing.refetch(); }}>重新刷新</Button></div>}
                {listing.isFetchNextPageError && <p role="alert">下一批暂时无法读取，已加载的技能保留。请重试。</p>}
                {listing.hasNextPage && <div className="astron-sh-load-more"><span>已显示 {items.length} 项</span><Button disabled={listing.isFetching} onClick={() => { void listing.fetchNextPage(); }}>{listing.isFetchingNextPage ? '正在加载…' : listing.isFetchNextPageError ? '重新加载下一批' : '加载更多'}</Button></div>}
              </>}
            </>}</TabsContent></Tabs>
        </>}
  </section>;
}
