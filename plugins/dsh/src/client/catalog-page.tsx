import { UiAvailabilityNotice, type UiAvailability } from './ui-availability.js';
import { NativeControlStyles } from './native-control-styles.js';
import { catalogErrorMessage } from './catalog-error.js';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { useLayoutEffect, useRef, useState, useSyncExternalStore } from 'react';
import type { CSSProperties } from 'react';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import { installedNavigationFor, type InstalledNavigation } from './installed-navigation.js';
import { InstalledSkills } from './installed-skills.js';
import type { InstalledServices } from './installed-skills.js';
import { DetailContent } from './detail-content.js';
import { InstallControl } from './install-control.js';
import type { InstallationServices } from './install-control.js';
import type { SkillList } from '../host/types.js';
import '../../lib/typert.remote-client.js';

type Item = SkillList['items'][number];
type Remote = TypertRemoteNamespaceMap['skillhub'];
export interface CatalogNavigation {
  installedNavigation?: InstalledNavigation;
  installedOrigin?: { sessionId: string; sourceRevision: string };
  tab: 'browse' | 'installed';
  query: string;
  draft: string;
  selected: Item | null;
  sourceRevision: string | null;
  scrollTop: number;
}
export const createCatalogNavigation = (): CatalogNavigation => ({
  tab: 'browse', query: '', draft: '', selected: null, sourceRevision: null, scrollTop: 0,
});
const button: CSSProperties = { border: '1px solid #8886', borderRadius: 8, padding: '8px 14px', cursor: 'pointer', background: 'transparent', color: 'inherit' };
const panel: CSSProperties = { border: '1px solid #8885', borderRadius: 12, padding: 20 };

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
  const entries = useSyncExternalStore(availability.subscribe, availability.getSnapshot);
  const [tab, setTab] = useState(navigation.tab);
  const [installedOrigin, setInstalledOrigin] = useState(navigation.installedOrigin);
  const [draft, setDraft] = useState(navigation.draft);
  const [query, setQuery] = useState(navigation.query);
  const [selected, setSelected] = useState(navigation.selected);
  const scroller = useRef<HTMLElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const lastCard = useRef<HTMLButtonElement | null>(null);
  const lastSelected = useRef(selected);
  const context = useQuery({ queryKey: ['skillhub-context'], queryFn: async () => unwrap(await remote.getContext()), retry: false });
  const compatibility = useQuery({ queryKey: ['skillhub-compatibility'],
    queryFn: async () => unwrap(await remote.getCompatibility()), retry: false });
  const revision = context.data?.sourceRevision;
  const installedNavigation = installed && revision ? installedNavigationFor(navigation, installed.sessionId, revision) : undefined;
  const sourceMatches = !selected || revision === navigation.sourceRevision;
  const listing = useQuery({
    queryKey: ['skillhub-list', revision, query], enabled: !!context.data?.configured && tab === 'browse',
    queryFn: async ({ signal }) => unwrap(await remote.listSkills(revision!, query, signal)), retry: false,
  });
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
    return <div role="alert" style={panel}><p>{message}</p><button style={button} onClick={retry}>重试</button></div>;
  }
  return <section ref={scroller} className="skillhub-controls" aria-label="SkillHub 技能广场" style={{ padding: 24, overflow: 'auto', height: '100%', boxSizing: 'border-box', fontFamily: 'inherit', lineHeight: 1.6 }}>
    <NativeControlStyles />
    {availability && <UiAvailabilityNotice availability={availability} />}
    <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap', marginBottom: 24 }}>
      <div><h1 style={{ margin: 0 }}>SkillHub 技能广场</h1><p style={{ opacity: .65, overflowWrap: 'anywhere' }}>{context.data?.registryUrl || '尚未配置来源'}</p></div>
      <button style={button} onClick={() => { if (!selected && tab === 'browse') navigation.scrollTop = scroller.current?.scrollTop ?? 0; onReturn(); }}>返回会话</button>
    </header>
    {(compatibility.isError || compatibility.data?.dependencyBaseline === 'unverified') && <div role="alert" style={panel}>
      <p>{compatibility.isError ? '无法读取 DSH 依赖版本核验结果。'
        : `当前 DSH 依赖组合未通过核验，安装、升级、卸载和恢复写入已禁用。请使用 ${compatibility.data?.candidateDshVersion} 对应的完整发行依赖。`}</p>
      <button style={button} onClick={() => { void compatibility.refetch(); }}>重新核验版本</button>
    </div>}
    {context.isPending ? <p role="status">正在读取来源…</p> : context.isError ? error(context.error.message, () => { void context.refetch(); })
      : !context.data.configured ? <div style={panel}><h2>连接 SkillHub</h2><p>{entries.configuration ? '在 DSH 的插件页面打开 SkillHub 配置，填写来源地址并保存。' : '尚未配置技能来源，且当前配置入口不可用。请先恢复 DSH 的插件配置功能，再连接 SkillHub。'}</p><button style={button} onClick={() => { void context.refetch(); }}>刷新连接</button></div>
      : selected ? <>
        <button style={button} onClick={back}>← 返回技能广场</button>
        <h2 ref={heading} tabIndex={-1}>{sourceMatches ? detail.data?.title || selected.slug : selected.slug}</h2>
        <p>@{selected.namespace}/{selected.slug} · {selected.latestVersion}</p>
        <p>{sourceMatches ? detail.data?.summary ?? selected.summary : selected.summary}</p>
        {!sourceMatches ? error('来源已变化，请返回技能广场重新选择。', back)
          : detail.isPending ? <p role="status">正在读取技能详情…</p>
          : detail.isError ? error(detail.error.message, () => { void detail.refetch(); })
          : <>
          {installation ? <InstallControl key={installation.sessionId} detail={detail.data} sourceRevision={revision!} services={installation} initiatedFrom="plaza" />
            : <p>请先打开一个 DSH 会话，再选择安装范围。<button style={button} onClick={onReturn}>前往会话</button></p>}
          <article style={{ ...panel, overflowWrap: 'anywhere' }} aria-label="技能说明">
            <DetailContent detail={detail.data} modes={context.data.detailTabs} remote={remote} retry={() => { void detail.refetch(); }} />
          </article></>}
      </> : <>
        <nav aria-label="技能目录" style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
          <button style={{ ...button, background: tab === 'browse' ? '#8882' : 'transparent' }} aria-pressed={tab === 'browse'} onClick={() => { navigation.installedOrigin = undefined; setInstalledOrigin(undefined); navigation.tab = 'browse'; setTab('browse'); }}>浏览技能</button>
          <button style={{ ...button, background: tab === 'installed' ? '#8882' : 'transparent' }} aria-pressed={tab === 'installed'} onClick={() => { if (tab === 'browse') navigation.scrollTop = scroller.current?.scrollTop ?? 0; navigation.installedOrigin = undefined; setInstalledOrigin(undefined); navigation.tab = 'installed'; setTab('installed'); }}>已安装</button>
        </nav>
        {tab === 'installed' ? installedOrigin && (installedOrigin.sessionId !== installed?.sessionId || installedOrigin.sourceRevision !== revision)
          ? <p role="alert">会话或来源已变化，请点击“已安装”查看当前上下文的记录。</p>
          : installed ? <InstalledSkills key={`${installed.sessionId}:${revision}`} services={installed} navigation={installedNavigation!} source={context.data.registryUrl} sourceRevision={revision!} remote={remote} modes={context.data.detailTabs} scrollContainer={scroller} />
          : <p>请先打开一个 DSH 会话，再查看该用户和项目的安装记录。<button style={button} onClick={onReturn}>前往会话</button></p>
          : <>

        <form onSubmit={event => { event.preventDefault(); const next = draft.trim(); navigation.query = next; navigation.scrollTop = 0; setQuery(next); if (next === query) void listing.refetch(); }} style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <input aria-label="搜索技能" placeholder="搜索技能名称或用途" value={draft} onChange={event => { navigation.draft = event.target.value; setDraft(event.target.value); }} style={{ ...button, flex: '1 1 200px', minWidth: 0, cursor: 'text' }} />
          <button style={button} type="submit">搜索</button>
          <button style={button} type="button" onClick={() => { navigation.draft = ''; navigation.query = ''; setDraft(''); setQuery(''); }}>清空</button>
        </form>
        {listing.isPending ? <p role="status">正在加载技能…</p> : listing.isError ? error(listing.error.message, () => { void listing.refetch(); }) : <>
          <p role="status">共匹配 {listing.data.total} 项，当前显示 {listing.data.returned} 项{listing.data.total > listing.data.returned ? '；可输入更具体的关键词缩小范围。' : '。'}</p>
          {listing.data.items.length === 0 ? <p>没有找到匹配的技能，请尝试其他关键词。</p> : <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 270px), 1fr))', gap: 16 }}>
            {listing.data.items.map(item => <button key={`${item.namespace}/${item.slug}`} ref={node => { if (lastSelected.current?.namespace === item.namespace && lastSelected.current?.slug === item.slug) lastCard.current = node; }} style={{ ...button, ...panel, textAlign: 'left' }} onClick={event => {
              lastSelected.current = item; lastCard.current = event.currentTarget; navigation.scrollTop = scroller.current?.scrollTop ?? 0;
              navigation.selected = item; navigation.sourceRevision = revision!; setSelected(item);
            }}><strong style={{ fontSize: 18 }}>{item.slug}</strong><p style={{ opacity: .65 }}>@{item.namespace}/{item.slug}</p><p>{item.summary || '暂无摘要'}</p><span>版本 {item.latestVersion} · 查看详情 →</span></button>)}
          </div>}
        </>}
        </>}
      </>}
  </section>;
}
