import { InstalledCardMetadata } from './installed-card-metadata.js';
export { installedCardVersions } from './installed-card-metadata.js';
import { PackageImport, type PackageTransferServices } from './package-transfer.js';
import { ChangePreview } from './change-preview.js';
import { checkInstalledUpdates, updateFailed, type UpdateResult } from './update-overview.js';
import { DetailMeta } from './detail-presentation.js';
import { FavoriteButton } from './favorite-button.js';
import { useFavorites } from './use-favorites.js';
import { favoriteIdentity } from './favorites.js';
import { sameRegistry } from './registry-config.js';
import { groupManagementItems, InstallationManagement, type ManagementViewServices } from './management-tool-view.js';
import { SkillPrimaryActions, skillInstallationSummary } from './skill-primary-actions.js';
import type { SkillUseServices } from './use-skill.js';
import { CardSkeletons } from './loading-layout.js';
import { useState, useRef, useLayoutEffect, useSyncExternalStore, type RefObject } from 'react';
import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import type { RemoteResult, TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { InstalledSkillList, InstalledSkillDetail, InstalledUpdateCheck, SkillHubConfig } from '../host/types.js';
import { UpgradeHistory, type UpgradeServices } from './upgrade-control.js';
import { readCacheOptions } from './read-cache.js';
import { DetailContent, type DetailContentHandle } from './detail-content.js';
import { RemovalHistory, type RemovalServices } from './removal-control.js';
import { InstalledStyles } from './installed-styles.js';
import type { InstalledNavigation } from './installed-navigation.js';
import { Badge, Button, Card, CardHeader, CardTitle } from './ui.js';
import { Icon, SkillAvatar } from './skill-card.js';

export interface InstalledServices extends UpgradeServices, RemovalServices {
  management?: ManagementViewServices;
  packages?: PackageTransferServices;
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
export function installedCardBadges(items: Item[], results: Record<string, UpdateResult>, verified: boolean) {
  const badges: { label: string; tone: 'neutral' | 'warning' | 'info' }[] = [];
  if (items[0]?.sourceKind === 'local-import') badges.push({ label: '本地导入', tone: 'info' });
  else if (items[0]?.currentSource === false) badges.push({ label: '其他来源', tone: 'info' });
  const summary = verified ? skillInstallationSummary(items) : '安装待核验';
  if (summary !== '已安装') badges.push({ label: summary, tone: summary === '已停用' ? 'neutral' : 'warning' });
  if (items.some(item => updateFailed(results[item.itemKey]))) badges.push({ label: '检查失败', tone: 'warning' });
  else if (items.some(item => results[item.itemKey]?.state === 'blocked')) badges.push({ label: '更新需处理', tone: 'warning' });
  else if (items.some(item => results[item.itemKey]?.state === 'upgradable')) {
    const result = results[items[0]!.itemKey];
    badges.push({ label: items.length > 1 ? '可更新' : `可更新至 ${result && 'candidateVersion' in result ? result.candidateVersion ?? '新版本' : '新版本'}`, tone: 'info' });
  }
  return badges;
}
export function installedUpdateKeys(inventory: Item[], results: Record<string, UpdateResult>, retry: boolean, group?: Item[]) {
  return inventory.filter(item => item.currentSource !== false && (!group || group.some(target => target.itemKey === item.itemKey)))
    .map(item => item.itemKey).filter(key => !retry || updateFailed(results[key]));
}
export function upgradedInstallation(snapshot: InstalledSkillList, previous: Item, version: string): Item | undefined {
  if (!snapshot.complete || snapshot.unavailable.length) return;
  const matches = snapshot.items.filter(item => item.state === 'present' && item.registry === previous.registry && item.namespace === previous.namespace && item.slug === previous.slug && item.scope === previous.scope && item.version === version);
  return matches.length === 1 ? matches[0] : undefined;
}
/** Refresh a static detail after a mutation without following another version or location. */
export function refreshedInstalledSelection(snapshot: InstalledSkillList, previous: Item): Item | undefined {
  const updated = upgradedInstallation(snapshot, previous, previous.version);
  return updated && (!previous.locationKey || updated.locationKey === previous.locationKey) ? updated : undefined;
}
/** The side panel stays mounted during upgrade; refresh its exact identity before returning. */
export async function refreshUpgradedCaches(client: QueryClient, sessionId: string, revision: string, previous: Item, snapshot: InstalledSkillList, isCurrent: () => boolean): Promise<boolean> {
  const managementKey = ['skillhub-installed', sessionId, revision, 'management-locations', previous.registry, previous.namespace, previous.slug];
  await client.cancelQueries({ queryKey: managementKey, exact: true });
  if (!isCurrent()) return false;
  client.setQueryData(managementKey, snapshot);
  const keys = new Set([previous.itemKey, ...snapshot.items.filter(item => item.registry === previous.registry && item.namespace === previous.namespace && item.slug === previous.slug).map(item => item.itemKey)]);
  await client.invalidateQueries({ predicate: query => ['skillhub-installed-detail', 'skillhub-local-summary'].includes(String(query.queryKey[0])) && query.queryKey[1] === sessionId && query.queryKey[2] === revision && keys.has(String(query.queryKey[3])), refetchType: 'none' });
  return isCurrent();
}
const noContext = { getSnapshot: () => 0, subscribe: () => () => {} };
export function InstalledSkills({ services, sourceRevision, source, remote, modes, scrollContainer, navigation, onInstall }: {
  services: InstalledServices; sourceRevision: string; source: string; remote: TypertRemoteNamespaceMap['skillhub']; modes: SkillHubConfig['detailTabs'];
  scrollContainer: RefObject<HTMLElement>; navigation: InstalledNavigation; onInstall?(item: Item): void; onRemove?(item: Item): void; onUpgrade?(item: Item): void;
}) {
  const client = useQueryClient();
  const context = services.context ?? noContext;
  const generation = useSyncExternalStore(context.subscribe, context.getSnapshot, context.getSnapshot);
  const contextKey = JSON.stringify([services.sessionId, sourceRevision, generation]);
  const resultContext = useRef(contextKey);
  const [keyword, setKeyword] = useState(navigation.keyword);
  const [selected, setSelected] = useState(navigation.selected);
  const [changeIntent, setChangeIntent] = useState<{ upgrade: boolean; version?: string } | undefined>(() => ['upgrade', 'local-changes'].includes(navigation.intent) ? { upgrade: navigation.intent === 'upgrade', version: navigation.changeVersion } : undefined);
  const activeDetail = useRef({ selected, changeIntent });
  activeDetail.current = { selected, changeIntent };
  const mounted = useRef(true);
  useLayoutEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const heading = useRef<HTMLHeadingElement>(null);
  const detailContent = useRef<DetailContentHandle>(null);
  const managementTarget = useRef<HTMLElement>(null);
  const refreshButton = useRef<HTMLButtonElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const triggerKey = useRef<string | null>(null);
  const listScroll = useRef(navigation.listScroll);
  useLayoutEffect(() => { Object.assign(navigation, { scope: 'all', keyword, selected, intent: changeIntent ? changeIntent.upgrade ? 'upgrade' : 'local-changes' : 'detail', changeVersion: changeIntent?.version, listScroll: listScroll.current }); });
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
  useLayoutEffect(() => {
    if (!selected || changeIntent || query.isFetching || query.isError || !query.data || resultContext.current !== contextKey) return;
    const updated = refreshedInstalledSelection(query.data, selected);
    if (updated && (updated.itemKey !== selected.itemKey || updated.installationState !== selected.installationState)) setSelected(updated);
  }, [query.data, query.isFetching, query.isError, selected, changeIntent, contextKey]);
  const verified = !!query.data && !query.isError && query.data.complete && !query.data.unavailable.length;
  const records = (identity: Item) => query.data?.items.filter(item => item.registry === identity.registry && item.namespace === identity.namespace && item.slug === identity.slug) ?? [identity];
  const actions = (items: Item[], label?: string) => <SkillPrimaryActions items={items} revision={sourceRevision} verified={verified}
    select={services.management?.selectSkillUse} useSkill={services.skillUse} label={label} summaryVisible={!label} />;

  const [results, setResults] = useState<Record<string, UpdateResult>>({});
  const [filter, setFilter] = useState('all');
  const [progress, setProgress] = useState({ done: 0, total: 0, running: false });
  const scan = useRef<AbortController | null>(null);
  const inventoryKey = JSON.stringify((query.data?.items ?? []).map(item => item.itemKey).sort());
  useLayoutEffect(() => {
    scan.current?.abort(); scan.current = null;
    const contextChanged = resultContext.current !== contextKey;
    resultContext.current = contextKey;
    if (contextChanged) { setChangeIntent(undefined); setSelected(null); setKeyword(''); setFilter('all'); }
    setResults(previous => contextChanged ? {} : Object.fromEntries(Object.entries(previous).filter(([key]) => (query.data?.items ?? []).some(item => item.itemKey === key))));
    setProgress({ done: 0, total: 0, running: false });
    return () => { scan.current?.abort(); scan.current = null; };
  }, [inventoryKey, contextKey]);
  useLayoutEffect(() => {
    if (selected) { scan.current?.abort(); scan.current = null; setProgress(previous => ({ ...previous, running: false })); }
  }, [selected]);
  const startCheck = (retry = false, group?: Item[]) => {
    if (scan.current || query.isFetching || query.isError) return;
    const keys = installedUpdateKeys(query.data?.items ?? [], results, retry, group);
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

  async function showUpgradedDetail(version: string) {
    if (!selected) return false;
    const current = () => mounted.current && activeDetail.current.selected === selected && activeDetail.current.changeIntent === changeIntent && resultContext.current === contextKey && context.getSnapshot() === generation;
    const snapshot = await query.refetch();
    if (!current()) return false;
    if (snapshot.isError || !snapshot.data) throw new Error('升级已完成，安装记录暂未刷新，请重试返回详情。');
    const updated = upgradedInstallation(snapshot.data, selected, version);
    if (!updated) throw new Error('升级已完成，尚未确认更新后的安装记录，请重试返回详情。');
    if (!await refreshUpgradedCaches(client, services.sessionId, sourceRevision, selected, snapshot.data, current)) return false;
    if (!current()) return false;
    setChangeIntent(undefined); setSelected(updated);
  }
  const management = services.management && { ...services.management, openChanges: (item: Item, upgrade: boolean, version?: string) => { setSelected(item); setChangeIntent({ upgrade, version }); } };
  const favoriteAllowed = !!selected && selected.sourceKind !== 'local-import' && selected.currentSource !== false && sameRegistry(selected.registry ?? '', source);
  const favorites = useFavorites(remote, sourceRevision, favoriteAllowed);
  const selectedFavorite = selected && { namespace: selected.namespace, slug: selected.slug, displayName: selected.displayName, summary: selected.summary };
  const favoritePressed = !!selectedFavorite && favorites.items.some(item => favoriteIdentity(item) === favoriteIdentity(selectedFavorite));
  const detailVerified = !!detail.data && !detail.isError && !detail.isFetching;
  if (selected) return <section className="skillhub-installed skillhub-controls astron-sh-detail-surface" aria-label="已安装技能详情">
    <InstalledStyles />
    <Button className="astron-sh-detail-back" variant="ghost" onClick={() => { setChangeIntent(undefined); setSelected(null); }}><Icon name="arrow-left" />返回已安装</Button>
    <div className="astron-sh-detail-heading"><SkillAvatar name={selected.displayName} slug={selected.slug} namespace={selected.namespace} /><div className="astron-sh-detail-identity"><h2 ref={heading} tabIndex={-1}>{selected.displayName || selected.slug}</h2><p>{selected.sourceKind === 'local-import' ? '本地导入' : `@${selected.namespace}/${selected.slug} · v${selected.version}`}</p></div><div className="astron-sh-detail-actions">{!changeIntent && detail.data?.content.state === 'ready' && <Button size="sm" variant="ghost" className="astron-sh-manage-installations" onClick={() => detailContent.current?.showManagement()}><Icon name="folder-open" />管理安装</Button>}{selectedFavorite && favoriteAllowed && <FavoriteButton showLabel item={selectedFavorite} pressed={favoritePressed} disabled={!favorites.ready} busy={favorites.loading || favorites.saving} onToggle={() => { void favorites.save(selectedFavorite, !favoritePressed); }} />}{!changeIntent && actions(records(selected), '使用技能')}</div></div>
    {favoriteAllowed && favorites.error && <p role="alert">{favorites.error}<Button size="sm" variant="ghost" onClick={favorites.retry}>重新读取收藏</Button></p>}
    {selected.summary && <p className="astron-sh-detail-summary">{selected.summary}</p>}
    <DetailMeta detail={detail.data?.content.state === 'ready' ? detail.data.content.detail : undefined} websiteEnabled={detailVerified && detail.data?.content.state === 'ready' && detail.data.content.detail.namespace === selected.namespace && detail.data.content.detail.slug === selected.slug} />
    <div className="astron-sh-detail-layout"><article className="astron-sh-detail-main astron-sh-documentation">{changeIntent && services.changes ? <ChangePreview key={`${contextKey}:${selected.itemKey}:${changeIntent.upgrade}`} services={{ ...services, changes: services.changes }} revision={sourceRevision} itemKey={selected.itemKey} installationKey={JSON.stringify([selected.registry, selected.namespace, selected.slug, selected.scope])} upgrade={changeIntent.upgrade} targetVersion={changeIntent.version} onCompleted={showUpgradedDetail} onClose={() => { setChangeIntent(undefined); void query.refetch(); void detail.refetch(); void services.management?.client.invalidateQueries({ queryKey: ['skillhub-local-summary', services.sessionId, sourceRevision] }); }} /> : <>
      {detail.isPending ? <p role="status">正在读取技能说明…</p>
      : detail.isError ? <p role="alert">{detail.error.message}</p>
        : detail.data.content.state === 'local-only' ? <p role="status">{selected.sourceKind === 'local-import' ? '本地技能文件暂不可读取，请核对安装状态。' : selected.currentSource === false ? '此技能来自其他来源，可在右侧管理本地安装。' : '远端说明暂不可用，可在管理安装中查看本地状态。'}</p>
          : <DetailContent ref={detailContent} managementTarget={managementTarget} localPackage={selected.sourceKind === 'local-import'} detail={detail.data.content.detail} modes={modes} remote={remote} retry={() => { void detail.refetch(); }} cacheIdentity={JSON.stringify([services.sessionId, sourceRevision, selected.namespace, selected.slug, detail.data.content.detail.version])} onContextStale={() => { void detail.refetch(); }} />}</>}
    </article><aside ref={managementTarget} tabIndex={-1} aria-label="安装位置管理" className="astron-sh-detail-aside">{selected.currentSource !== false && <div className="astron-sh-detail-update-check"><Button size="sm" variant="ghost" disabled={progress.running || query.isFetching || query.isError} onClick={() => startCheck(false, records(selected))}><Icon name="refresh" />检查更新</Button></div>}{progress.total > 0 && <div className="skillhub-installed-summary"><p role="status">{progress.running ? '检查中' : progress.done < progress.total ? '已停止' : '已检查'} {progress.done}/{progress.total}{failures ? ` · ${failures} 处检查失败` : ''}{upgradable ? ` · ${upgradable} 个技能可更新` : ''}</p>{progress.running && <Button size="sm" variant="ghost" onClick={() => { scan.current?.abort(); scan.current = null; setProgress(previous => ({ ...previous, running: false })); }}>取消检查</Button>}{failures > 0 && !progress.running && <Button size="sm" variant="outline" onClick={() => startCheck(true, records(selected))}>重试失败项</Button>}</div>}{management && <InstallationManagement identity={selected} revision={sourceRevision} services={management} updateChecks={Object.fromEntries(Object.entries(results).filter((entry): entry is [string, InstalledUpdateCheck] => entry[1].state !== 'failed'))} useSkill={services.skillUse} onInstall={onInstall && selected.currentSource !== false ? () => onInstall(selected) : undefined} />}</aside></div>
  </section>;
  const term = keyword.trim().toLocaleLowerCase();
  const groups = allGroups.filter(items => filter === 'all' || (filter === 'disabled' ? verified && skillInstallationSummary(items) === '已停用' : filter === 'local' ? items[0]?.sourceKind === 'local-import' : items.some(item => filter === 'updates' ? results[item.itemKey]?.state === 'upgradable' : updateFailed(results[item.itemKey])))).filter(items => !term || items.some(item => `@${item.namespace}/${item.slug} ${item.displayName ?? ''} ${item.version}`.toLocaleLowerCase().includes(term)));
  return <section className="skillhub-installed skillhub-controls" aria-label="已安装技能">
    <InstalledStyles />
    <div className="astron-sh-workbench-heading"><div><h1>我的技能</h1><p>查看和使用已安装技能，按需管理本地安装。</p></div></div>
    <div className="skillhub-installed-toolbar"><label className="skillhub-installed-search">
      <input type="search" aria-label="过滤已安装技能" placeholder="搜索已安装技能" value={keyword} onChange={event => setKeyword(event.target.value)} />
    </label>{services.packages && <PackageImport transfer={services.packages} revision={sourceRevision} contextKey={contextKey} services={services} onCompleted={() => { void query.refetch(); }} />}<Button ref={refreshButton} onClick={() => { void query.refetch(); }} disabled={query.isFetching}><Icon name="refresh" />刷新已安装</Button>
      {keyword && <Button variant="ghost" onClick={() => setKeyword('')}>清空过滤</Button>}
      <Button onClick={() => startCheck()} disabled={progress.running || query.isFetching || query.isError || !query.data?.items.some(item => item.currentSource !== false)}>检查更新</Button>
      {progress.running && <Button variant="ghost" onClick={() => { scan.current?.abort(); scan.current = null; setProgress(previous => ({ ...previous, running: false })); }}>取消检查</Button>}
      {failures > 0 && <Button onClick={() => startCheck(true)} disabled={progress.running || query.isFetching}>重试失败项</Button>}
    </div>
    <div className="skillhub-installed-summary">
      <div className="skillhub-installed-filters" role="group" aria-label="更新筛选">
        {([['all', '全部'], ['updates', '可更新'], ['disabled', '已停用'], ['local', '本地导入'], ['failed', '检查失败']] as const).map(([value, label]) => <Button key={value} size="sm" variant={filter === value ? 'outline' : 'ghost'} aria-pressed={filter === value} onClick={() => setFilter(value)}>{label}</Button>)}
      </div>
      <p role="status">{progress.total > 0 ? `${progress.running ? '检查中' : progress.done < progress.total ? '已停止' : '已检查'} ${progress.done}/${progress.total}${upgradable ? ` · ${upgradable} 个技能可更新` : ''}${failures ? ` · ${failures} 处检查失败` : ''}` : query.data && !query.data.items.some(item => item.currentSource !== false) ? '当前来源暂无可检查的技能' : `${groups.length} 个技能`}</p>
    </div>
    {query.isPending ? <CardSkeletons message="正在读取已安装技能…" /> : query.isError ? <p role="alert">{query.error.message}</p> : <>
      {(!query.data.complete || query.data.unavailable.length > 0) && <p role="alert">部分安装记录无法读取，列表可能不完整。</p>}
      {!groups.length ? <p className="skillhub-installed-empty">{term || filter !== 'all' ? '没有匹配的技能。' : verified ? '暂无受管的已安装技能。' : '暂未读取到安装记录。'}</p>
        : <div className="astron-sh-card-grid" aria-label="已安装技能列表">{groups.map(items => {
          const identity = items[0]!;
          const key = JSON.stringify([identity.registry, identity.namespace, identity.slug]);
          const presentation = items.find(item => item.summary) ?? identity;
          const displayName = presentation.displayName || identity.slug;
          const usable = verified && services.management?.selectSkillUse && items.some(item => item.state === 'present' && item.installationState !== 'disabled');
          return <Card className="astron-sh-installed-card" key={key}>
            <button type="button" className="astron-sh-card-open" aria-label={`查看 ${displayName} @${identity.namespace}/${identity.slug} 详情`}
              ref={node => { if (triggerKey.current === key) trigger.current = node; }} onClick={event => {
                // This record selects documentation only; use resolves the actual winner on the Host.
                listScroll.current = scrollContainer.current?.scrollTop ?? 0;
                triggerKey.current = key; trigger.current = event.currentTarget; setSelected(identity);
              }} />
            <div className="skillhub-installed-card-heading"><SkillAvatar name={displayName} slug={identity.slug} namespace={identity.namespace} />
            <CardHeader className="skillhub-installed-identity">
              <CardTitle title={displayName}>{displayName}</CardTitle>
              <p className="skillhub-installed-namespace" title={identity.sourceKind === 'local-import' ? '本地导入' : `@${identity.namespace}/${identity.slug}`}>{identity.sourceKind === 'local-import' ? '本地导入' : `@${identity.namespace}/${identity.slug}`}</p>
            </CardHeader></div>
            <p className="skillhub-installed-description" title={presentation.summary}>{presentation.summary || '暂无摘要'}</p>
            <div className="skillhub-installed-badges">{installedCardBadges(items, results, verified).filter(badge => badge.label !== '本地导入').map(badge => <Badge key={badge.label} variant={badge.tone}>{badge.label}</Badge>)}</div>
            <div className="skillhub-installed-footer"><InstalledCardMetadata items={items} compact />
              <div className="skillhub-installed-actions astron-sh-card-actions">
                {usable ? actions(items, '使用') : <Button size="sm" variant="outline" onClick={event => {
                  listScroll.current = scrollContainer.current?.scrollTop ?? 0;
                  triggerKey.current = key; trigger.current = event.currentTarget; setSelected(identity);
                }}>查看详情</Button>}
              </div>
            </div>
          </Card>;
        })}</div>}
    </>}
    <UpgradeHistory services={services} sourceRevision={sourceRevision} source={source} onClosed={() => refreshButton.current?.focus()} />
    <RemovalHistory services={services} sourceRevision={sourceRevision} source={source} onClosed={() => refreshButton.current?.focus()} />
  </section>;
}
