import { useId, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { SkillDetail, SkillHubConfig } from '../host/types.js';
import type { ReactNode } from 'react';
import '../../lib/typert.remote-client.js';

type Remote = TypertRemoteNamespaceMap['skillhub'];
type Tab = 'details' | 'versions' | 'securityAudit' | 'reviews';
const names: Record<Tab, string> = { details: '详情', versions: '版本', securityAudit: '安全审核', reviews: '评论' };
export function DetailTabs({ detail, modes, remote, children }: {
  detail: SkillDetail; modes: SkillHubConfig['detailTabs']; remote: Remote; children: ReactNode;
}) {
  const [selected, setSelected] = useState<Tab>('details');
  const id = useId();
  const visible = (Object.keys(names) as Tab[]).filter(tab => tab === 'details'
    || modes[tab] === 'show' || (modes[tab] === 'auto' && detail.tabs[tab] === 'available'));
  const active = visible.includes(selected) ? selected : 'details';
  return <>
    <div role="tablist" aria-label="技能详情" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
      {visible.map((tab, index) => <button key={tab} type="button" role="tab" id={`${id}-tab-${tab}`}
        aria-selected={active === tab} aria-controls={`${id}-content-${tab}`} tabIndex={active === tab ? 0 : -1}
        onClick={() => setSelected(tab)} onKeyDown={event => {
          const next = event.key === 'ArrowRight' ? (index + 1) % visible.length
            : event.key === 'ArrowLeft' ? (index + visible.length - 1) % visible.length
            : event.key === 'Home' ? 0 : event.key === 'End' ? visible.length - 1 : -1;
          if (next < 0) return;
          event.preventDefault(); setSelected(visible[next]!);
          (event.currentTarget.parentElement?.children[next] as HTMLElement | undefined)?.focus();
        }} style={{ padding: '8px 14px', border: '1px solid #8885', borderRadius: 8, background: active === tab ? '#8882' : 'transparent', color: 'inherit' }}>{names[tab]}</button>)}
    </div>
    {visible.map(tab => <section key={tab} role="tabpanel" id={`${id}-content-${tab}`} aria-labelledby={`${id}-tab-${tab}`}
      hidden={active !== tab} tabIndex={active === tab ? 0 : -1}>
      {active !== tab ? null : tab === 'details' ? children : tab === 'securityAudit' ? <p>当前来源的审核信息不支持匿名读取。</p>
        : <>
          {tab === 'reviews' && <p>评论针对整个技能，不限定当前版本。</p>}
          <ModulePage key={`${detail.detailToken}/${tab}`} tab={tab} token={detail.detailToken} remote={remote} />
        </>}
    </section>)}
  </>;
}

function ModulePage({ tab, token, remote }: { tab: 'versions' | 'reviews'; token: string; remote: Remote }) {
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
  if (result.isPending) return <p role="status">正在读取{names[tab]}…</p>;
  if (result.isError) return <div role="alert"><p>{result.error.message}</p><button onClick={() => { void result.refetch(); }}>重试</button></div>;
  const data = result.data;
  return <>
    {data.items.length === 0 ? <p>暂无{names[tab]}信息。</p> : data.items.map(item => 'version' in item
      ? <article key={item.version} style={{ borderBottom: '1px solid #8884', padding: '12px 0' }}>
        <strong>{item.version}</strong><p>{item.publishedAt ?? '发布时间未知'} · {item.downloadAvailable ? '可下载' : '不可下载'}</p>
        <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{item.changelog || '暂无发布说明'}</p>
      </article> : <article key={item.id} style={{ borderBottom: '1px solid #8884', padding: '12px 0' }}>
        <strong>{item.displayName || '用户'} · {item.score}/5</strong><p>{item.createdAt}</p>
        <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{item.reviewText || '未填写评论内容'}</p>
      </article>)}
    <nav aria-label={`${names[tab]}分页`} style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12, marginTop: 16 }}>
      <button style={{ whiteSpace: 'nowrap', flexShrink: 0 }} disabled={page === 0} onClick={() => setPage(page - 1)}>上一页</button>
      <span role="status">第 {page + 1} 页 · 共 {data.total} 项</span>
      <button style={{ whiteSpace: 'nowrap', flexShrink: 0 }} disabled={(page + 1) * data.size >= data.total} onClick={() => setPage(page + 1)}>下一页</button>
    </nav>
  </>;
}
