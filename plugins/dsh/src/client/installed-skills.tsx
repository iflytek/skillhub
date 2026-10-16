import { isInventoryScopeVerified } from './installed-inventory.js';
import { CardSkeletons } from './loading-layout.js';
import { useState, useRef, useLayoutEffect, useCallback } from 'react';
import type { RefObject } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { RemoteResult, TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { InstalledSkillList, InstalledSkillDetail, InstalledUpdateCheck, SkillHubConfig } from '../host/types.js';
import { UpgradeControl, UpgradeHistory } from './upgrade-control.js';
import type { UpgradeServices } from './upgrade-control.js';
import { DetailContent } from './detail-content.js';
import { RemovalControl, RemovalHistory, type RemovalServices } from './removal-control.js';
import { InstalledStyles } from './installed-styles.js';
import type { InstalledNavigation } from './installed-navigation.js';
import { SelectField, Button, Card, CardHeader, CardContent, CardFooter } from './ui.js';
import { SkillAvatar } from './skill-card.js';
import { readableDate } from './skill-presentation.js';
import { useInstalledUpdate } from './installed-update.js';

export interface InstalledServices extends UpgradeServices, RemovalServices {
  sessionId: string;
  list(sourceRevision: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillList>>;
  detail(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillDetail>>;
  checkUpdate(sourceRevision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<InstalledUpdateCheck>>;
}
const states: Record<InstalledSkillList['items'][number]['state'], string> = {
  present: '文件存在', missing: '安装目录缺失', 'metadata-invalid': '安装记录无法识别',
  mismatch: '安装记录与本地目标不一致', unreadable: '本地文件暂时无法读取',
};
const updateReasons: Record<string, string> = {
  'remote-older': '远端版本比已安装版本更旧，不执行降级。',
  'unknown-order': '无法确定版本先后顺序，请核实发布信息。',
  'same-version-content-changed': '同一版本的内容发生变化，需要核实，不能直接升级。',
  'missing-fingerprint': '缺少内容基线，暂时无法确认更新。',
  'local-record-unverified': '本地安装记录存在异常，请先核对安装状态。',
  SOURCE_DENIED: '来源不允许匿名访问此技能。', SKILL_UNAVAILABLE: '技能或版本已不可用。',
  RATE_LIMITED: '来源暂时限流，请稍后重试。',
};

export function InstalledSkills({ services, sourceRevision, source, remote, modes, scrollContainer, navigation }: {
  services: InstalledServices; sourceRevision: string; source: string; remote: TypertRemoteNamespaceMap['skillhub']; modes: SkillHubConfig['detailTabs'];
  scrollContainer: RefObject<HTMLElement>; navigation: InstalledNavigation;
}) {
  const [scope, setScope] = useState(navigation.scope);
  const [keyword, setKeyword] = useState(navigation.keyword);
  const [selected, setSelected] = useState(navigation.selected);
  const [completed, setCompleted] = useState(navigation.completed);
  const [removalCompleted, setRemovalCompleted] = useState(navigation.removalCompleted);
  const onRemovalCompleted = useCallback(() => { if (selected) setRemovalCompleted(selected.itemKey); }, [selected]);
  const onUpgradeCompleted = useCallback((version: string) => {
    if (selected) setCompleted(previous => previous?.itemKey === selected.itemKey && previous.version === version
      ? previous : { itemKey: selected.itemKey, version });
  }, [selected]);
  const intent = useRef(navigation.intent);
  const upgradeSection = useRef<HTMLDivElement>(null);
  const removalSection = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const listScroll = useRef(navigation.listScroll);
  const triggerKey = useRef(navigation.triggerKey);
  useLayoutEffect(() => {
    Object.assign(navigation, { scope, keyword, selected, completed, removalCompleted,
      intent: intent.current, listScroll: listScroll.current, triggerKey: triggerKey.current });
  });
  const heading = useRef<HTMLHeadingElement>(null);
  const filter = useRef<HTMLButtonElement>(null);
  useLayoutEffect(() => {
    if (selected) {
      const target = intent.current === 'upgrade' ? upgradeSection.current : intent.current === 'remove' ? removalSection.current : heading.current;
      target?.focus();
    }
    else {
      if (trigger.current?.isConnected) trigger.current.focus({ preventScroll: true });
      else filter.current?.focus({ preventScroll: true });
      if (scrollContainer.current) scrollContainer.current.scrollTop = listScroll.current;
    }
  }, [selected, scrollContainer]);
  const query = useQuery({ queryKey: ['skillhub-installed', services.sessionId, sourceRevision], retry: false,
    queryFn: async ({ signal }) => {
      const response = await services.list(sourceRevision, signal);
      if (!response.ok) throw new Error(response.error.code === 'skillhub/context-stale'
        ? '会话或来源已变化，请重新打开已安装列表。' : '无法读取本地安装记录，请检查 DSH 会话后重试。');
      return response.value;
    } });
  const detail = useQuery({ queryKey: ['skillhub-installed-detail', services.sessionId, sourceRevision, selected?.itemKey],
    enabled: !!selected, retry: false, queryFn: async ({ signal }) => {
      const response = await services.detail(sourceRevision, selected!.itemKey, signal);
      if (!response.ok) throw new Error('安装记录已变化或暂时无法读取，请返回列表刷新。');
      return response.value;
    } });
  const update = useInstalledUpdate(services, sourceRevision, selected?.itemKey);
  const upgradedVersion = completed?.itemKey === selected?.itemKey ? completed?.version : undefined;
  const refreshedItem = selected && query.data?.items.find(item => item.namespace === selected.namespace && item.slug === selected.slug && item.scope === selected.scope);
  const removed = !!selected && removalCompleted === selected.itemKey && !!query.data && !refreshedItem;
  const currentLocalState = detail.data?.item.state ?? selected?.state;
  const detailVerified = !!detail.data && !detail.isError && !detail.isFetching;
  if (selected) return <section className="skillhub-installed skillhub-controls" aria-label="已安装技能详情">
    <InstalledStyles />
    <button onClick={() => { triggerKey.current = refreshedItem?.itemKey ?? selected.itemKey; setSelected(null); }}>← 返回已安装</button>
    <div className="astron-sh-detail-heading"><SkillAvatar slug={selected.slug} namespace={selected.namespace} /><h2 ref={heading} tabIndex={-1}>{selected.slug}</h2></div>
    <p>@{selected.namespace}/{selected.slug} · {removed ? '原安装版本' : '已安装版本'} {upgradedVersion ?? selected.version}</p>
    <p>{selected.scope === 'user' ? '当前用户' : '当前项目'} · {removed ? '此安装记录已移除' : states[currentLocalState ?? selected.state]}</p>
    <div className="astron-sh-detail-layout"><article className="astron-sh-detail-main astron-sh-documentation">{removed ? <p role="status">此安装位置的操作已完成，请查看下方结果或返回列表。</p>
      : upgradedVersion ? <p role="status">安装版本已更新。<button disabled={!refreshedItem || refreshedItem.version !== upgradedVersion}
      onClick={() => { if (refreshedItem) setSelected(refreshedItem); }}>查看升级后详情</button></p>
      : detail.isPending ? <p role="status">正在核对本地记录并读取固定版本详情…</p>
      : detail.isError ? <p role="alert">{detail.error.message}</p>
        : detail.data.content.state === 'local-only' ? <p role="status">远端详情暂时不可用，以上保留本地安装信息。未切换到其他版本。</p>
          : <DetailContent detail={detail.data.content.detail} modes={modes} remote={remote} retry={() => { void detail.refetch(); }} />}
    </article><aside className="astron-sh-detail-aside">
    {!removed && !upgradedVersion && <Card className="astron-sh-version-update"><CardHeader><h3>版本与更新</h3></CardHeader><CardContent><div className="astron-sh-update-actions"><Button size="sm" variant="outline" disabled={detail.isFetching} onClick={() => { void detail.refetch(); }}>重新读取详情</Button>
    <Button size="sm" variant="outline" disabled={update.isFetching} onClick={() => { void update.refetch(); }}>检查更新</Button></div>
    {(update.isFetching || update.isError || update.data) && <div aria-live="polite">
      {update.isFetching ? <p role="status">正在检查更新…</p> : update.isError ? <p role="alert">{update.error.message}</p>
        : update.data && <>
          <p>{update.data.state === 'upgradable' ? `发现新版本 ${update.data.candidateVersion}。升级前仍需核验安装目标和本地内容。`
            : update.data.state === 'current' ? '版本及记录指纹与来源当前发布一致。'
              : updateReasons[update.data.reason] ?? '暂时无法确认更新，请检查来源连接后重试。'}</p>
          <p className="astron-sh-update-time">检查时间：<time dateTime={update.data.checkedAt}>{readableDate(update.data.checkedAt, true)}</time></p>
        </>}
    </div>}
    </CardContent></Card>}
    {!removed && <div ref={upgradeSection} tabIndex={-1} aria-label="升级操作区域"><UpgradeControl key={selected.itemKey} services={services} sourceRevision={sourceRevision} source={source} item={selected}
      onCompleted={onUpgradeCompleted}
      eligible={detailVerified && currentLocalState === 'present' && !upgradedVersion && !update.isFetching && !update.isError && update.data?.state === 'upgradable'} /></div>}
    <div ref={removalSection} tabIndex={-1} aria-label="卸载操作区域"><RemovalControl key={`remove:${selected.itemKey}`} services={services} sourceRevision={sourceRevision} source={source} item={detail.data?.item ?? selected}
      eligible={detailVerified && !removed && !upgradedVersion && ['present', 'missing'].includes(currentLocalState ?? '')} onCompleted={onRemovalCompleted}
      onClosed={() => heading.current?.focus()} /></div></aside></div>
  </section>;
  const term = keyword.trim().toLocaleLowerCase();
  const items = query.data?.items.filter(item => (scope === 'all' || item.scope === scope)
    && (!term || `@${item.namespace}/${item.slug} ${item.version}`.toLocaleLowerCase().includes(term))) ?? [];
  return <section className="skillhub-installed skillhub-controls" aria-label="已安装技能">
    <InstalledStyles />
    <div className="skillhub-installed-toolbar">
      <label className="skillhub-installed-search">本地过滤
        <input type="search" aria-label="过滤已安装技能" placeholder="名称、命名空间或已安装版本" value={keyword} onChange={event => setKeyword(event.target.value)} />
      </label>
      <label>安装范围 <SelectField ref={filter} label="筛选安装范围" value={scope} onValueChange={value => setScope(value as typeof scope)}
        options={[{ value: 'all', label: '全部范围' }, { value: 'project', label: '当前项目' }, { value: 'user', label: '当前用户' }]} /></label>
      <button onClick={() => { void query.refetch(); }} disabled={query.isFetching}>刷新已安装</button>
      {keyword && <button onClick={() => setKeyword('')}>清空过滤</button>}
    </div>
    <p className="skillhub-installed-note">显示当前 SkillHub 来源在当前用户和项目中的安装记录。“文件存在”不代表内容完整性或安全审核已通过。</p>
    {query.isPending ? <CardSkeletons message="正在读取本地安装记录…" /> : query.isError ? <p role="alert">{query.error.message}</p> : <>
      {(!query.data.complete || query.data.unavailable.length > 0) && <div role="alert"><p>部分安装记录无法读取，以下列表可能不完整。</p>
        {query.data.unavailable.length > 0 && <p>无法读取范围：{query.data.unavailable.map(value => value === 'user' ? '当前用户' : '当前项目').join('、')}</p>}
      </div>}
      <p role="status">当前显示 {items.length} 项安装记录。</p>
      {items.length === 0 ? <p className="skillhub-installed-empty">{term ? '没有匹配的本地安装记录，请调整关键词或安装范围。' : (scope === 'all' ? query.data.complete && query.data.unavailable.length === 0 : isInventoryScopeVerified(query.data, scope)) ? '当前范围没有该来源的安装记录。' : '当前范围没有可展示的记录，请先处理读取问题。'}</p>
        : <div className="astron-sh-card-grid" aria-label="本地安装记录">{items.map(item => <InstalledRow key={item.itemKey} item={item} services={services} sourceRevision={sourceRevision}
            buttonRef={(action, node) => { if (triggerKey.current === item.itemKey && intent.current === action) trigger.current = node; }}
            navigate={(action, button) => {
              listScroll.current = scrollContainer.current?.scrollTop ?? 0;
              intent.current = action; triggerKey.current = item.itemKey; trigger.current = button; setSelected(item);
            }} />)}</div>}
    </>}
    <UpgradeHistory services={services} sourceRevision={sourceRevision} source={source} onClosed={() => filter.current?.focus()} />
    <RemovalHistory services={services} sourceRevision={sourceRevision} source={source} onClosed={() => filter.current?.focus()} />
  </section>;
}


function InstalledRow({ item, services, sourceRevision, navigate, buttonRef }: {
  item: InstalledSkillList['items'][number]; services: InstalledServices; sourceRevision: string;
  navigate(action: 'detail' | 'upgrade' | 'remove', button: HTMLButtonElement): void;
  buttonRef(action: 'detail' | 'upgrade' | 'remove', node: HTMLButtonElement | null): void;
}) {
  const update = useInstalledUpdate(services, sourceRevision, item.itemKey);
  return <Card className="astron-sh-installed-card">
    <CardHeader className="astron-sh-skill-heading"><SkillAvatar slug={item.slug} namespace={item.namespace} /><div>
    <h3>{item.slug}</h3><span className="skillhub-installed-coordinate">@{item.namespace}/{item.slug}</span></div></CardHeader><CardContent>
    <div className="astron-sh-installed-meta"><span>已安装版本 {item.version}</span><span>{item.scope === 'user' ? '当前用户' : '当前项目'}</span></div>
    <p><span className="astron-sh-state-badge" data-state={item.state}>{states[item.state]}</span></p>
    <div aria-live="polite">
      {update.isFetching ? '正在检查更新…' : update.isError ? update.error.message : !update.data ? '尚未检查'
        : update.data.state === 'upgradable' ? `可升级至 ${update.data.candidateVersion}`
          : update.data.state === 'current' ? '版本及记录指纹一致'
            : updateReasons[update.data.reason] ?? '暂时无法确认更新，请重试。'}
      {update.data && <details className="astron-sh-check-time"><summary>检查记录</summary><time className="skillhub-installed-coordinate" dateTime={update.data.checkedAt}>{update.isError || update.isFetching ? '上次成功检查' : '检查于'} {new Date(update.data.checkedAt).toLocaleString()}</time></details>}
    </div></CardContent>
    <CardFooter className="skillhub-installed-actions">
      <button ref={node => buttonRef('detail', node)} onClick={event => navigate('detail', event.currentTarget)}>查看已安装详情</button>
      <button disabled={update.isFetching} onClick={() => { void update.refetch(); }}>检查更新</button>
      {!update.isFetching && !update.isError && update.data?.state === 'upgradable'
        && <button ref={node => buttonRef('upgrade', node)} onClick={event => navigate('upgrade', event.currentTarget)}>升级</button>}
      <button ref={node => buttonRef('remove', node)} disabled={!['present', 'missing'].includes(item.state)}
        onClick={event => navigate('remove', event.currentTarget)}>{item.state === 'missing' ? '清理失效记录' : '卸载'}</button>
    </CardFooter>
  </Card>;
}
