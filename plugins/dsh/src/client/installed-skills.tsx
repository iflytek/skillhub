import { SkillWebsiteLink } from './skill-website-link.js';
import { groupManagementItems, InstallationManagement, type ManagementViewServices } from './management-tool-view.js';
import { SkillPrimaryActions, skillInstallationSummary } from './skill-primary-actions.js';
import type { SkillUseServices } from './use-skill.js';
import { CardSkeletons } from './loading-layout.js';
import { useState, useRef, useLayoutEffect, type RefObject } from 'react';
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
  skillUse?: SkillUseServices;
  list(sourceRevision: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillList>>;
  detail(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillDetail>>;
  checkUpdate(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<InstalledUpdateCheck>>;
}
type Item = InstalledSkillList['items'][number];
export function InstalledSkills({ services, sourceRevision, source, remote, modes, scrollContainer, navigation, onInstall }: {
  services: InstalledServices; sourceRevision: string; source: string; remote: TypertRemoteNamespaceMap['skillhub']; modes: SkillHubConfig['detailTabs'];
  scrollContainer: RefObject<HTMLElement>; navigation: InstalledNavigation; onInstall?(item: Item): void; onRemove?(item: Item): void; onUpgrade?(item: Item): void;
}) {
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
  const query = useQuery({ queryKey: ['skillhub-installed', services.sessionId, sourceRevision], retry: false,
    queryFn: async ({ signal }) => {
      const response = await services.list(sourceRevision, signal);
      if (!response.ok) throw new Error('会话或来源已变化，请重新读取已安装技能。');
      return response.value;
    } });
  const detail = useQuery({ ...readCacheOptions, queryKey: ['skillhub-installed-detail', services.sessionId, sourceRevision, selected?.itemKey],
    enabled: !!selected, retry: false, queryFn: async ({ signal }) => {
      const response = await services.detail(sourceRevision, selected!.itemKey, signal);
      if (!response.ok) throw new Error('安装记录已变化或暂时无法读取，请返回列表刷新。');
      return response.value;
    } });
  const verified = !!query.data && !query.isError && query.data.complete && !query.data.unavailable.length;
  const records = (identity: Item) => query.data?.items.filter(item => item.namespace === identity.namespace && item.slug === identity.slug) ?? [identity];
  const actions = (items: Item[], label?: string) => <SkillPrimaryActions items={items} revision={sourceRevision} verified={verified}
    select={services.management?.selectSkillUse} useSkill={services.skillUse} onManage={!label ? () => { const identity = items[0]; if (identity) { listScroll.current = scrollContainer.current?.scrollTop ?? 0; setSelected(identity); } } : undefined} label={label} summaryVisible={!label} />;

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
    </article><aside className="astron-sh-detail-aside">{services.management && <InstallationManagement identity={selected} revision={sourceRevision} services={services.management} useSkill={services.skillUse} onInstall={onInstall ? () => onInstall(selected) : undefined} />}</aside></div>
  </section>;
  const term = keyword.trim().toLocaleLowerCase();
  const groups = groupManagementItems(query.data?.items ?? []).filter(items => !term || items.some(item => `@${item.namespace}/${item.slug} ${item.version}`.toLocaleLowerCase().includes(term)));
  return <section className="skillhub-installed skillhub-controls" aria-label="已安装技能">
    <InstalledStyles />
    <div className="skillhub-installed-toolbar"><label className="skillhub-installed-search">本地过滤
      <input type="search" aria-label="过滤已安装技能" placeholder="名称、命名空间或已安装版本" value={keyword} onChange={event => setKeyword(event.target.value)} />
    </label><Button ref={refreshButton} onClick={() => { void query.refetch(); }} disabled={query.isFetching}>刷新已安装</Button>
      {keyword && <Button variant="ghost" onClick={() => setKeyword('')}>清空过滤</Button>}</div>
    {query.isPending ? <CardSkeletons message="正在读取已安装技能…" /> : query.isError ? <p role="alert">{query.error.message}</p> : <>
      {(!query.data.complete || query.data.unavailable.length > 0) && <p role="alert">部分安装记录无法读取，列表可能不完整。</p>}
      <p role="status">当前显示 {groups.length} 个技能。</p>
      {!groups.length ? <p className="skillhub-installed-empty">{term ? '没有匹配的技能。' : verified ? '当前来源暂无已安装技能。' : '暂未读取到安装记录。'}</p>
        : <div className="astron-sh-card-grid" aria-label="已安装技能列表">{groups.map(items => {
          const identity = items[0]!;
          return <Card className="astron-sh-installed-card" key={JSON.stringify([identity.namespace, identity.slug])}>
            <CardHeader className="astron-sh-skill-heading"><SkillAvatar slug={identity.slug} namespace={identity.namespace} /><div><h3>{identity.slug}</h3><span>@{identity.namespace}/{identity.slug}</span></div></CardHeader>
            <CardContent><span>{skillInstallationSummary(items)}</span></CardContent>
            <CardFooter className="skillhub-installed-actions">{actions(items)}<Button variant="ghost" onClick={event => {
              // This record selects documentation only; use always resolves the actual winner on the Host.
              listScroll.current = scrollContainer.current?.scrollTop ?? 0; trigger.current = event.currentTarget; setSelected(identity);
            }}>详情</Button></CardFooter>
          </Card>;
        })}</div>}
    </>}
    <UpgradeHistory services={services} sourceRevision={sourceRevision} source={source} onClosed={() => refreshButton.current?.focus()} />
    <RemovalHistory services={services} sourceRevision={sourceRevision} source={source} onClosed={() => refreshButton.current?.focus()} />
  </section>;
}
