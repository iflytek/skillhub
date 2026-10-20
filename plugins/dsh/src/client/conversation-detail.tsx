import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { QueryClientProvider, useMutation, useQuery } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';
import type { RemoteResult, TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { ConversationDetail as ConversationDetailData } from '../host/types.js';
import { NativeControlStyles } from './native-control-styles.js';
import { SkillAvatar, SkillMetrics } from './skill-card.js';
import { DetailContent } from './detail-content.js';
import { InstallControl } from './install-control.js';
import type { InstallationServices } from './install-control.js';
import '../../lib/typert.remote-client.js';
import type { ConversationSearchResult } from '../host/search-result.js';

export interface SearchViewServices {
  client: QueryClient;
  remote: TypertRemoteNamespaceMap['skillhub'];
  openDetail: (reference: string, signal: AbortSignal) => Promise<RemoteResult<ConversationDetailData>>;
  reopenDetail: (callId: string, reference: string, signal: AbortSignal) => Promise<RemoteResult<ConversationDetailData>>;
  acknowledgeDetail: (reference: string, token: string) => Promise<RemoteResult<boolean>>;
  installation: InstallationServices;
  openCatalog: (result: ConversationSearchResult) => Promise<void>;
}
class StaleDetailError extends Error {}
function value<T>(result: RemoteResult<T>): T {
  if (result.ok) return result.value;
  if (result.error.code === 'skillhub/context-stale') throw new StaleDetailError('这条搜索记录的引用已失效，需重新核验当前会话与来源。');
  throw new Error('详情暂时无法读取，请稍后重试。');
}
export function ConversationDetail({ reference, callId, originalSource, services, close, browseVersions, allowHistoricalReopen = true }: {
  reference: string; callId: string; originalSource: string; services: SearchViewServices; close: () => void; browseVersions?: () => void; allowHistoricalReopen?: boolean;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useLayoutEffect(() => {
    const element = dialog.current!;
    const previous = document.activeElement;
    element.showModal();
    return () => { element.close(); if (previous instanceof HTMLElement && previous.isConnected) previous.focus(); };
  }, []);
  return <dialog ref={dialog} className="skillhub-controls astron-sh-detail-drawer" aria-label="技能详情" onCancel={event => { event.preventDefault(); close(); }}
    style={{ margin: '0 0 0 auto', height: '100dvh', maxHeight: '100dvh', width: 'min(720px, 100vw)', maxWidth: '100vw', boxSizing: 'border-box', border: '1px solid #8885', padding: 24, overflowY: 'auto', font: 'inherit', lineHeight: 1.6, overflowWrap: 'anywhere' }}>
    <NativeControlStyles />
    <button autoFocus onClick={close} style={{ float: 'right', minHeight: 40 }} aria-label="关闭技能详情">关闭</button>
    <QueryClientProvider client={services.client}><Content reference={reference} callId={callId} originalSource={originalSource} services={services} browseVersions={browseVersions} allowHistoricalReopen={allowHistoricalReopen} /></QueryClientProvider>
  </dialog>;
}
function Content({ reference, callId, originalSource, services, browseVersions, allowHistoricalReopen }: { reference: string; callId: string; originalSource: string; services: SearchViewServices; browseVersions?: () => void; allowHistoricalReopen: boolean }) {
  const [reopening, setReopening] = useState(false);
  const query = useQuery({ queryKey: ['skillhub-conversation-detail', services.installation.sessionId, callId, reference, reopening], retry: false, gcTime: 0, refetchOnWindowFocus: false, refetchOnReconnect: false,
    queryFn: async ({ signal }) => {
      const [context, detail] = await Promise.all([services.remote.getContext(), reopening && allowHistoricalReopen ? services.reopenDetail(callId, reference, signal) : services.openDetail(reference, signal)]);
      return { context: value(context), detail: value(detail) };
    },
  });
  if (query.isPending || query.isFetching) return <p role="status">正在读取技能详情…</p>;
  if (query.isError) return <div role="alert">
    <p>原搜索来源：{originalSource}</p>
    {query.error instanceof StaleDetailError
      ? <><p>{reopening || !allowHistoricalReopen ? '无法在当前会话与来源中重新核验这条记录，请重新搜索技能。' : query.error.message}</p>
        {!reopening && allowHistoricalReopen && <button onClick={() => setReopening(true)}>重新查看详情</button>}</>
      : <><p>{query.error.message}</p><button onClick={() => { void query.refetch(); }}>重试</button></>}
  </div>;
  const { context, detail } = query.data;
  return <>
    {reopening && <p role="status">已重新核验详情。安装需要另行确认。</p>}
    <div className="astron-sh-detail-heading"><SkillAvatar name={detail.title} slug={detail.slug} namespace={detail.namespace} /><div><h2>{detail.title}</h2><p>@{detail.namespace}/{detail.slug} · {detail.version}</p></div></div><p>{detail.summary}</p><SkillMetrics item={detail} />
    <Acknowledgment key={detail.detailToken} reference={detail.selectionRef} token={detail.detailToken} acknowledge={services.acknowledgeDetail} />
    <InstallControl detail={detail} sourceRevision={context.sourceRevision} services={services.installation} initiatedFrom="conversation" onContextStale={() => { void query.refetch(); }} />
    <article className="astron-sh-documentation"><DetailContent detail={detail} modes={context.detailTabs} remote={services.remote} retry={() => { void query.refetch(); }} cacheIdentity={JSON.stringify([services.installation.sessionId, context.sourceRevision, detail.namespace, detail.slug, detail.version])} onContextStale={() => { void query.refetch(); }} onSelectVersion={browseVersions} versionActionLabel="前往广场选择版本" /></article>
    <details className="astron-sh-conversation-provenance"><summary>来源信息</summary><p>原搜索来源：{originalSource}</p><p>当前详情来源：{context.registryUrl}</p></details>
  </>;
}
function Acknowledgment({ reference, token, acknowledge }: {
  reference: string; token: string; acknowledge: SearchViewServices['acknowledgeDetail'];
}) {
  const { mutate, isError } = useMutation({ mutationFn: async () => {
    if (!value(await acknowledge(reference, token))) throw new Error('Display acknowledgment refused.');
  }, retry: false });
  useEffect(() => {
    let sent = false;
    const confirm = () => { if (!sent && document.visibilityState === 'visible') { sent = true; mutate(); } };
    confirm(); document.addEventListener('visibilitychange', confirm);
    return () => document.removeEventListener('visibilitychange', confirm);
  }, [mutate]);
  return isError ? <p role="alert">详情展示确认失败，请关闭后重新打开。</p> : null;
}
