import { checkInstalledUpdates, updateFailed, type UpdateResult } from './update-overview.js';
import { SkillWebsiteLink } from './skill-website-link.js';
import { groupManagementItems, InstallationManagement, type ManagementViewServices } from './management-tool-view.js';
import { SkillPrimaryActions, skillInstallationSummary } from './skill-primary-actions.js';
import type { SkillUseServices } from './use-skill.js';
import { CardSkeletons } from './loading-layout.js';
import { useState, useRef, useLayoutEffect, useSyncExternalStore, type RefObject } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { RemoteResult, TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { InstalledSkillList, InstalledSkillDetail, InstalledUpdateCheck, SkillHubConfig } from '../host/types.js';
import { UpgradeHistory, type UpgradeServices } from './upgrade-control.js';
import { readCacheOptions } from './read-cache.js';
import { DetailContent } from './detail-content.js';
import { RemovalHistory, type RemovalServices } from './removal-control.js';
import { InstalledStyles } from './installed-styles.js';
import type { InstalledNavigation } from './installed-navigation.js';
import { Button, Card, CardHeader, CardContent, CardFooter } from './ui.js';
import { SkillAvatar } from './skill-card.js';

export interface InstalledServices extends UpgradeServices, RemovalServices {
  management?: ManagementViewServices;
  sessionId: string;
  context?: { getSnapshot(): number; subscribe(listener: () => void): () => void };
  skillUse?: SkillUseServices;
  list(sourceRevision: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillList>>;
  detail(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillDetail>>;
  beginInstalledUpdateScan(sourceRevision: string, signal: AbortSignal): Promise<RemoteResult<{ scanId: string; itemKeys: string[] }>>;
  checkInstalledUpdateScan(sourceRevision: string, scanId: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<InstalledUpdateCheck>>;
  endInstalledUpdateScan(sourceRevision: string, scanId: string): Promise<RemoteResult<void>>;
  checkUpdate(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<InstalledUpdateCheck>>;
}
type Item = InstalledSkillList['items'][number];
const noContext = { getSnapshot: () => 0, subscribe: () => () => {} };
export function InstalledSkills({ services, sourceRevision, source, remote, modes, scrollContainer, navigation, onInstall }: {
  services: InstalledServices; sourceRevision: string; source: string; remote: TypertRemoteNamespaceMap['skillhub']; modes: SkillHubConfig['detailTabs'];
  scrollContainer: RefObject<HTMLElement>; navigation: InstalledNavigation; onInstall?(item: Item): void; onRemove?(item: Item): void; onUpgrade?(item: Item): void;
}) {
  const context = services.context ?? noContext;
  const generation = useSyncExternalStore(context.subscribe, context.getSnapshot, context.getSnapshot);
  const contextKey = JSON.stringify([services.sessionId, sourceRevision, generation]);
  const resultContext = useRef(contextKey);
  const [keyword, setKeyword] = useState(navigation.keyword);
  const [selected, setSelected] = useState(navigation.selected);
  const heading = useRef<HTMLHeadingElement>(null);
  const refreshButton = useRef<HTMLButtonElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const listScroll = useRef(navigation.listScroll);
  useLayoutEffect(() => { Object.assign(navigation, { scope: 'all', keyword, selected, intent: 'detail', listScroll: listScroll.current }); });
  useLayoutEffect(() => {
    if (selected) heading.current?.focus();
    else {
      if (trigger.current?.isConnected) trigger.current.focus({ preventScroll: true });
      else refreshButton.current?.focus({ preventScroll: true });
      if (scrollContainer.current) scrollContainer.current.scrollTop = listScroll.current;
    }
  }, [selected, scrollContainer]);
  const query = useQuery({ queryKey: ['skillhub-installed', services.sessionId, sourceRevision, ...(services.context ? [generation] : [])], retry: false,
    queryFn: async ({ signal }) => {
      const response = await services.list(sourceRevision, signal);
      if (!response.ok) throw new Error('会话或来源已变化，请重新读取已安装技能。');
      return response.value;
    } });
  const detail = useQuery({ ...readCacheOptions, queryKey: ['skillhub-installed-detail', services.sessionId, sourceRevision, selected?.itemKey, ...(services.context ? [generation] : [])],
    enabled: !!selected, retry: false, queryFn: async ({ signal }) => {
      const response = await services.detail(sourceRevision, selected!.itemKey, signal);
      if (!response.ok) throw new Error('安装记录已变化或暂时无法读取，请返回列表刷新。');
      return response.value;
    } });
  const verified = !!query.data && !query.isError && query.data.complete && !query.data.unavailable.length;
  const records = (identity: Item) => query.data?.items.filter(item => item.namespace === identity.namespace && item.slug === identity.slug) ?? [identity];
  const actions = (items: Item[], label?: string) => <SkillPrimaryActions items={items} revision={sourceRevision} verified={verified}
    select={services.management?.selectSkillUse} useSkill={services.skillUse} onManage={!label ? () => { const identity = items[0]; if (identity) { listScroll.current = scrollContainer.current?.scrollTop ?? 0; setSelected(identity); } } : undefined} label={label} summaryVisible={!label} />;

  const [results, setResults] = useState<Record<string, UpdateResult>>({});
  const [filter, setFilter] = useState('all');
  const [progress, setProgress] = useState({ done: 0, total: 0, running: false });
  const scan = useRef<AbortController | null>(null);
  const inventoryKey = JSON.stringify((query.data?.items ?? []).map(item => item.itemKey).sort());
  useLayoutEffect(() => {
    scan.current?.abort(); scan.current = null;
    const contextChanged = resultContext.current !== contextKey;
    resultContext.current = contextKey;
    if (contextChanged) { setSelected(null); setKeyword(''); setFilter('all'); }
    setResults(previous => contextChanged ? {} : Object.fromEntries(Object.entries(previous).filter(([key]) => (query.data?.items ?? []).some(item => item.itemKey === key))));
    setProgress({ done: 0, total: 0, running: false });
    return () => { scan.current?.abort(); scan.current = null; };
  }, [inventoryKey, contextKey]);
  useLayoutEffect(() => {
    if (selected) { scan.current?.abort(); scan.current = null; setProgress(previous => ({ ...previous, running: false })); }
  }, [selected]);
  const startCheck = (retry = false) => {
    if (scan.current || query.isFetching || query.isError) return;
    const keys = (query.data?.items ?? []).map(item => item.itemKey).filter(key => !retry || updateFailed(results[key]));
    if (!keys.length) return;
    const controller = new AbortController(); scan.current = controller;
    const scanGeneration = context.getSnapshot();
    setResults(previous => Object.fromEntries(Object.entries(previous).filter(([key]) => !keys.includes(key))));
    setProgress({ done: 0, total: keys.length, running: true });
    void (async () => {
      let scanId: string | undefined;
      const end = () => { if (scanId) void services.endInstalledUpdateScan(sourceRevision, scanId).catch(() => {}); };
      controller.signal.addEventListener('abort', end, { once: true });
      try {
        const begun = await services.beginInstalledUpdateScan(sourceRevision, controller.signal);
        if (!begun.ok) throw new Error('scan failed');
        scanId = begun.value.scanId;
        controller.signal.throwIfAborted();
        const accepted = new Set(begun.value.itemKeys);
        const stale = () => {
          controller.abort();
          if (scan.current === controller && context.getSnapshot() === scanGeneration) {
            setResults(Object.fromEntries(keys.map(key => [key, { state: 'failed' as const }])));
            setProgress({ done: keys.length, total: keys.length, running: false });
          }
        };
        await checkInstalledUpdates(keys, async (key, signal) => {
          if (context.getSnapshot() !== scanGeneration) { controller.abort(); throw new Error('context changed'); }
          if (!accepted.has(key)) { stale(); throw new Error('installed record changed'); }
          const response = await services.checkInstalledUpdateScan(sourceRevision, scanId!, key, signal);
          if (!response.ok) { if (response.error.code === 'skillhub/context-stale') stale(); throw new Error('check failed'); }
          return response.value;
        }, controller.signal, (key, result) => {
          if (scan.current !== controller || context.getSnapshot() !== scanGeneration) return;
          setResults(previous => ({ ...previous, [key]: result }));
          setProgress(previous => ({ ...previous, done: previous.done + 1 }));
        });
      } catch {
        if (scan.current === controller && !controller.signal.aborted) {
          setResults(previous => ({ ...previous, ...Object.fromEntries(keys.map(key => [key, { state: 'failed' as const }])) }));
          setProgress(previous => ({ ...previous, done: keys.length }));
        }
      } finally { controller.signal.removeEventListener('abort', end); end(); }
    })().finally(() => {
      if (scan.current === controller) { scan.current = null; setProgress(previous => ({ ...previous, running: false })); }
    });
  };
  const allGroups = groupManagementItems(query.data?.items ?? []);
  const upgradable = allGroups.filter(items => items.some(item => results[item.itemKey]?.state === 'upgradable')).length;
  const failures = (query.data?.items ?? []).filter(item => updateFailed(results[item.itemKey])).length;

  const detailVerified = !!detail.data && !detail.isError && !detail.isFetching;
  if (selected) return <section className="skillhub-installed skillhub-controls" aria-label="已安装技能详情">
    <InstalledStyles />
    <Button variant="ghost" onClick={() => setSelected(null)}>← 返回已安装</Button>
    <div className="astron-sh-detail-heading"><SkillAvatar slug={selected.slug} namespace={selected.namespace} /><h2 ref={heading} tabIndex={-1}>{selected.slug}</h2><SkillWebsiteLink webUrl={detail.data?.content.state === 'ready' ? detail.data.content.detail.webUrl : undefined}
      enabled={detailVerified && detail.data?.content.state === 'ready' && detail.data.content.detail.namespace === selected.namespace && detail.data.content.detail.slug === selected.slug} /></div>
    <p>@{selected.namespace}/{selected.slug} · 文档版本 {selected.version}</p>
    {actions(records(selected), '使用已安装版本')}
    <div className="astron-sh-detail-layout"><article className="astron-sh-detail-main astron-sh-documentation">{detail.isPending ? <p role="status">正在读取技能说明…</p>
      : detail.isError ? <p role="alert">{detail.error.message}</p>
        : detail.data.content.state === 'local-only' ? <p role="status">远端说明暂不可用，可在管理安装中查看本地状态。</p>
          : <DetailContent detail={detail.data.content.detail} modes={modes} remote={remote} retry={() => { void detail.refetch(); }} cacheIdentity={JSON.stringify([services.sessionId, sourceRevision, selected.namespace, selected.slug, detail.data.content.detail.version])} onContextStale={() => { void detail.refetch(); }} />}
    </article><aside className="astron-sh-detail-aside">{services.management && <InstallationManagement identity={selected} revision={sourceRevision} services={services.management} updateChecks={Object.fromEntries(Object.entries(results).filter((entry): entry is [string, InstalledUpdateCheck] => entry[1].state !== 'failed'))} useSkill={services.skillUse} onInstall={onInstall ? () => onInstall(selected) : undefined} />}</aside></div>
  </section>;
  const term = keyword.trim().toLocaleLowerCase();
  const groups = allGroups.filter(items => filter === 'all' || items.some(item => filter === 'updates' ? results[item.itemKey]?.state === 'upgradable' : updateFailed(results[item.itemKey]))).filter(items => !term || items.some(item => `@${item.namespace}/${item.slug} ${item.version}`.toLocaleLowerCase().includes(term)));
  return <section className="skillhub-installed skillhub-controls" aria-label="已安装技能">
    <InstalledStyles />
    <div className="skillhub-installed-toolbar"><label className="skillhub-installed-search">
      <input type="search" aria-label="过滤已安装技能" placeholder="搜索已安装技能" value={keyword} onChange={event => setKeyword(event.target.value)} />
    </label><Button ref={refreshButton} onClick={() => { void query.refetch(); }} disabled={query.isFetching}>刷新已安装</Button>
      {keyword && <Button variant="ghost" onClick={() => setKeyword('')}>清空过滤</Button>}
      <Button onClick={() => startCheck()} disabled={progress.running || query.isFetching || query.isError || !query.data?.items.length}>检查更新</Button>
      {progress.running && <Button variant="ghost" onClick={() => { scan.current?.abort(); scan.current = null; setProgress(previous => ({ ...previous, running: false })); }}>取消检查</Button>}
      {failures > 0 && <Button onClick={() => startCheck(true)} disabled={progress.running || query.isFetching}>重试失败项</Button>}
    </div>
    <div className="skillhub-installed-summary">
      <div className="skillhub-installed-filters" role="group" aria-label="更新筛选">
        {([['all', '全部'], ['updates', '可更新'], ['failed', '检查失败']] as const).map(([value, label]) => <Button key={value} size="sm" variant={filter === value ? 'outline' : 'ghost'} aria-pressed={filter === value} onClick={() => setFilter(value)}>{label}</Button>)}
      </div>
      <p role="status">{progress.total > 0 ? `${progress.running ? '检查中' : progress.done < progress.total ? '已停止' : '已检查'} ${progress.done}/${progress.total}${upgradable ? ` · ${upgradable} 个技能可更新` : ''}${failures ? ` · ${failures} 处检查失败` : ''}` : `${groups.length} 个技能`}</p>
    </div>
    {query.isPending ? <CardSkeletons message="正在读取已安装技能…" /> : query.isError ? <p role="alert">{query.error.message}</p> : <>
      {(!query.data.complete || query.data.unavailable.length > 0) && <p role="alert">部分安装记录无法读取，列表可能不完整。</p>}
      {!groups.length ? <p className="skillhub-installed-empty">{term || filter !== 'all' ? '没有匹配的技能。' : verified ? '当前来源暂无已安装技能。' : '暂未读取到安装记录。'}</p>
        : <div className="astron-sh-card-grid" aria-label="已安装技能列表">{groups.map(items => {
          const identity = items[0]!;
          return <Card className="astron-sh-installed-card" key={JSON.stringify([identity.namespace, identity.slug])}>
            <CardHeader className="astron-sh-skill-heading"><SkillAvatar slug={identity.slug} namespace={identity.namespace} /><div><h3>{identity.slug}</h3><span>@{identity.namespace}/{identity.slug}</span></div></CardHeader>
            <CardContent><span>{skillInstallationSummary(items)}</span>
              {items.some(item => results[item.itemKey]?.state === 'upgradable') && <p>{items.length > 1 ? '多位置安装有更新' : `可更新至 ${(() => { const result = results[identity.itemKey]; return result && 'candidateVersion' in result ? result.candidateVersion ?? '新版本' : '新版本'; })()}`}</p>}
              {items.some(item => results[item.itemKey]?.state === 'blocked') && <p>更新需处理，请查看安装状态。</p>}
              {items.some(item => updateFailed(results[item.itemKey])) && <p>部分安装位置检查失败，可重试。</p>}
            </CardContent>
            <CardFooter className="skillhub-installed-actions">{actions(items)}<Button variant="ghost" onClick={event => {
              // This record selects documentation only; use always resolves the actual winner on the Host.
              listScroll.current = scrollContainer.current?.scrollTop ?? 0; trigger.current = event.currentTarget; setSelected(identity);
            }}>{items.some(item => results[item.itemKey]?.state === 'upgradable') ? '查看更新' : '详情'}</Button></CardFooter>
          </Card>;
        })}</div>}
    </>}
    <UpgradeHistory services={services} sourceRevision={sourceRevision} source={source} onClosed={() => refreshButton.current?.focus()} />
    <RemovalHistory services={services} sourceRevision={sourceRevision} source={source} onClosed={() => refreshButton.current?.focus()} />
  </section>;
}
