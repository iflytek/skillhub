import { DiscoveryStatus } from './discovery-status.js';
import { HostWriteStatus, useWritableHost, type CompatibilityServices } from './host-compatibility.js';
import { useState, useSyncExternalStore } from 'react';
import { useInstallationOperation } from './use-installation-operation.js';
import { useQuery } from '@tanstack/react-query';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { HostInstallRequest, InstallationTargetList, InstalledSkillList, NativeOperation, SkillDetail } from '../host/types.js';
import type { InstallationRequests } from './installation-requests.js';

export interface InstallationServices extends CompatibilityServices {
  sessionId: string;
  requests: InstallationRequests;
  targets(token: string, signal: AbortSignal): Promise<RemoteResult<InstallationTargetList>>;
  installed(revision: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillList>>;
  start(input: HostInstallRequest, signal: AbortSignal): Promise<RemoteResult<NativeOperation | null>>;
  operation(id: string, host: string): Promise<RemoteResult<NativeOperation | null>>;
}

function read<T>(response: RemoteResult<T>): T {
  if (response.ok) return response.value;
  throw new Error('暂时无法确认安装状态，请检查连接或重新打开当前会话。');
}

export function InstallControl({ detail, sourceRevision, services, initiatedFrom }: {
  detail: SkillDetail; sourceRevision: string; services: InstallationServices; initiatedFrom: HostInstallRequest['initiatedFrom'];
}) {
  const writableHost = useWritableHost(services);
  const [scope, setScope] = useState<'project' | 'user'>('user');
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const targets = useQuery({ queryKey: ['skillhub-install-targets', services.sessionId, detail.detailToken], retry: false,
    queryFn: async ({ signal }) => read(await services.targets(detail.detailToken, signal)) });
  const host = targets.data?.hostInstanceId ?? '';
  const key = JSON.stringify([services.sessionId, host, sourceRevision, detail.namespace, detail.slug, detail.version, scope]);
  const attempt = useSyncExternalStore(services.requests.subscribe, () => services.requests.get(key), () => undefined);
  const operationId = attempt?.operationId ?? attempt?.operation?.operationId;
  const operation = useInstallationOperation(services, host, operationId);
  const result = operation.data ?? attempt?.operation;
  const target = targets.data?.targets.find(item => item.scope === scope);
  const local = useQuery({ queryKey: ['skillhub-installed', services.sessionId, sourceRevision], retry: false,
    enabled: operation.data?.phase === 'completed',
    queryFn: async ({ signal }) => read(await services.installed(sourceRevision, signal)) });
  const current = local.data?.items.find(item => item.namespace === detail.namespace && item.slug === detail.slug && item.scope === scope);
  const absent = !local.isError && !local.isFetching && local.data?.complete === true && !local.data.unavailable.includes(scope) && !current;
  function submit(reinstall = false) {
    if (!target || !host || (!attempt && !writableHost.canWrite)) return;
    if (reinstall && (!absent || !writableHost.canWrite || operation.data?.phase !== 'completed' || !operationId)) return;
    setSubmissionError(null);
    void services.requests.submit(key, { detailToken: detail.detailToken, targetToken: target.targetToken,
      hostInstanceId: host, initiatedFrom }, async input => read(await services.start(input, new AbortController().signal)), reinstall ? operationId : undefined)
      .catch(error => setSubmissionError(error instanceof Error ? error.message : '无法保存安装请求。'));
  }
  return <section aria-label="安装技能" style={{ border: '1px solid #8885', borderRadius: 12, padding: 16, marginBlock: 16 }}>
    <h3>安装 {detail.version}</h3>
    <HostWriteStatus state={writableHost} />
    <label>安装范围 <select aria-label="安装范围" value={scope} disabled={attempt?.state === 'submitting'} onChange={event => setScope(event.target.value as 'project' | 'user')}>
      <option value="project">当前项目</option><option value="user">当前用户</option>
    </select></label>
    {targets.isPending ? <p role="status">正在确认安装位置…</p> : targets.isError
      ? <p role="alert">无法确认安装位置。<button onClick={() => { void targets.refetch(); }}>重试</button></p>
      : !target ? <p>当前范围不可安装，请选择其他范围或检查 DSH 会话设置。</p> : null}
    {!attempt && <button disabled={!writableHost.canWrite || !target || targets.isFetching} onClick={() => submit()}>安装到{scope === 'project' ? '当前项目' : '当前用户'}</button>}
    {submissionError && <p role="alert">{submissionError}</p>}
    {attempt?.state === 'submitting' && <p role="status">正在提交安装请求…</p>}
    {attempt?.state === 'unresolved' && <div role="alert"><p>尚未确认请求结果。查询原请求不会重复安装。</p><button onClick={() => submit()} disabled={!target}>查询原请求</button></div>}
    {attempt?.state === 'accepted' && <div aria-live="polite">
      {operation.isError || operation.data === null ? <p role="alert">安装状态暂时无法确认。<button onClick={() => { void operation.refetch(); }}>重新查询</button></p>
        : result?.phase === 'completed' ? <><p>{result.files === 'already-installed' ? '此版本已安装。' : '技能文件安装完成。'}</p><DiscoveryStatus confirmed={result.discovery === 'confirmed'} checkedAt={operation.dataUpdatedAt}
            refreshing={operation.isFetching} refresh={() => { void operation.refetch(); }} /><p>这是原操作的结果；当前是否安装请以已安装列表为准。</p></>
          : result?.phase === 'failed' ? <p role="alert">安装未完成。请查看 DSH 操作记录并确认恢复状态。</p>
            : result && ['recovery-required', 'unknown'].includes(result.phase) ? <p role="alert">操作需要恢复核验，请勿重复安装。</p>
              : <p role="status">正在安装，请稍候…</p>}
      {result && <p style={{ overflowWrap: 'anywhere', opacity: .7 }}>{result.context.sourceLabel} · {result.targetLabel} · {result.version}</p>}
      {operation.data?.phase === 'completed' && <div>
        <p>以上为原安装操作结果。</p>
        {local.isPending || local.isFetching ? <p role="status">正在核验当前安装记录…</p>
          : local.isError || !local.data?.complete ? <p role="alert">当前安装记录无法完整核验，请重试或前往已安装查看。</p>
            : current ? <p>当前范围记录版本：{current.version}。{current.state === 'present' ? '文件存在。' : '本地状态异常，请前往已安装核验。'}</p>
              : <p>当前范围已无此技能的安装记录。</p>}
        <button disabled={local.isFetching} onClick={() => { void local.refetch(); }}>核验当前安装状态</button>
        {absent && <button disabled={!writableHost.canWrite || !target || targets.isFetching} onClick={() => submit(true)}>重新安装到{scope === 'project' ? '当前项目' : '当前用户'}</button>}
      </div>}
    </div>}
  </section>;
}
