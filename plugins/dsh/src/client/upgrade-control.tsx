import { OperationDetails } from './operation-details.js';
import { DiscoveryStatus } from './discovery-status.js';
import { HostWriteStatus, useWritableHost, type CompatibilityServices } from './host-compatibility.js';
import { useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useMutation } from '@tanstack/react-query';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { HostExecutePlanRequest, HostRecoveryRequest, NativeOperation, UpgradePlanView } from '../host/types.js';
import type { UpgradeRequests } from './upgrade-requests.js';
import { upgradeRequestKey } from './upgrade-requests.js';
import { useInstallationOperation } from './use-installation-operation.js';

export interface UpgradeServices extends CompatibilityServices {
  sessionId: string;
  upgradeRequests: UpgradeRequests;
  prepareUpgrade(revision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<UpgradePlanView>>;
  executePlan(input: HostExecutePlanRequest, signal: AbortSignal): Promise<RemoteResult<NativeOperation | null>>;
  operation(id: string, host: string): Promise<RemoteResult<NativeOperation | null>>;
  recoverOperation(input: HostRecoveryRequest): Promise<RemoteResult<NativeOperation | null>>;
}
const blockers: Record<string, string> = {
  'shared-target': '安装位置存在共享归属，暂不能升级。',
  'target-outside-context': '完整安装记录包含当前会话无法管理的位置，不能只升级其中一部分。',
  'target-alias': '安装位置重复或发生重定向，请先核对本地目录。',
  'local-changes': '本地文件已修改，升级会覆盖这些内容，本次已阻止。',
  'metadata-invalid': '缺少可靠的安装基线，请先核对本地记录。',
  'record-changed': '安装记录已变化，请刷新列表后重新检查。',
  'recovery-required': '有未完成的操作需要恢复核验，暂不能升级。',
};

export function UpgradeControl({ services, sourceRevision, source, item, eligible = false, onCompleted, onClosed }: {
  services: UpgradeServices; sourceRevision: string; source: string; eligible?: boolean;
  item: { itemKey?: string; namespace: string; slug: string; version?: string; scope: string };
  onCompleted?(version: string): void; onClosed?(): void;
}) {
  const writableHost = useWritableHost(services);
  const key = upgradeRequestKey(services.sessionId, source, item);
  const attempt = useSyncExternalStore(services.upgradeRequests.subscribe, () => services.upgradeRequests.get(key), () => undefined);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now);
  const trigger = useRef<HTMLButtonElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  const hadPlan = useRef(false);
  const plan = useMutation({ mutationFn: async () => {
    if (!writableHost.canWrite) throw new Error('当前 DSH 写入兼容性未通过核验。');
    if (!item.itemKey) throw new Error('请从当前安装记录重新发起升级。');
    const response = await services.prepareUpgrade(sourceRevision, item.itemKey, new AbortController().signal);
    if (!response.ok) throw new Error('无法生成升级计划，请刷新安装记录后重试。');
    return response.value;
  } });
  useLayoutEffect(() => {
    if (plan.data) cancel.current?.focus();
    else if (hadPlan.current) trigger.current?.focus();
    hadPlan.current = !!plan.data;
  }, [plan.data]);
  useEffect(() => {
    if (!plan.data?.expiresAt || attempt) return;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [plan.data?.expiresAt, attempt]);
  const operation = useInstallationOperation(services, attempt?.hostInstanceId ?? '', attempt?.operationId);
  const result = operation.data ?? attempt?.operation;
  const recoveryConfirmed = !operation.isError && operation.data && (operation.data.phase === 'completed'
    || (operation.data.phase === 'failed' && ['restored', 'not-needed'].includes(operation.data.recovery)));
  const recovery = useMutation({ mutationFn: async () => {
    if (!writableHost.canWrite) throw new Error('当前 DSH 写入兼容性未通过核验。');
    if (!attempt?.operationId) throw new Error('无法识别原操作，请重新查询。');
    try {
      const response = await services.recoverOperation({ operationId: attempt.operationId, hostInstanceId: attempt.hostInstanceId });
      if (!response.ok || !response.value) throw new Error('恢复结果未确认，请查询原操作并核对完整目标范围。');
    } finally {
      await operation.refetch();
    }
  } });
  useEffect(() => {
    if (result?.phase === 'completed' && result.kind === 'upgrade' && result.version !== item.version) onCompleted?.(result.version);
  }, [result?.phase, result?.kind, result?.version, item.version, onCompleted]);
  const expired = !!plan.data?.expiresAt && now >= Date.parse(plan.data.expiresAt);
  const submit = () => {
    const planId = attempt?.request.planId ?? plan.data?.planId;
    const host = attempt?.hostInstanceId ?? plan.data?.hostInstanceId;
    if ((!attempt && !writableHost.canWrite) || !planId || !host || (!attempt && (!plan.data?.expiresAt || Date.now() >= Date.parse(plan.data.expiresAt)))) return;
    setError(null);
    void services.upgradeRequests.submit(key, planId, host, async input => {
      const response = await services.executePlan(input, new AbortController().signal);
      if (!response.ok) throw new Error('升级请求未确认。');
      return response.value;
    }).catch(error => setError(error instanceof Error ? error.message : '升级请求未提交。'));
  };
  if (!eligible && !attempt) return null;
  return <section aria-label="升级技能" style={{ border: '1px solid #8885', borderRadius: 12, padding: 16, marginBlock: 16 }}>
    <h3>升级 @{item.namespace}/{item.slug}</h3>
    <HostWriteStatus state={writableHost} />
    {!attempt && <>
      {!plan.data && <button ref={trigger} disabled={!writableHost.canWrite || plan.isPending} onClick={() => plan.mutate()}>{plan.isPending ? '正在核验升级范围…' : '生成升级计划'}</button>}
      {plan.isError && <p role="alert">{plan.error.message}</p>}
      {plan.data?.status === 'ready' && <>
        <p>{source}</p><p>{plan.data.currentVersion} → {plan.data.candidateVersion}</p>
        <p>将升级以下 {plan.data.targets.length} 个安装位置：</p>
        <ul>{plan.data.targets.map(target => <li key={target.path}>{target.scope === 'user' ? '当前用户' : '当前项目'}：<code style={{ overflowWrap: 'anywhere' }}>{target.path}</code></li>)}</ul>
        <p>计划有效至：<time dateTime={plan.data.expiresAt!}>{new Date(plan.data.expiresAt!).toLocaleString()}</time></p>
        {expired && <p role="alert">计划已过期，请重新生成。</p>}
        <button disabled={!writableHost.canWrite || expired} onClick={submit}>确认升级</button>
        <button ref={cancel} onClick={() => plan.reset()}>取消</button>
      </>}
      {plan.data && plan.data.status !== 'ready' && <>
        <p role="status">{plan.data.status === 'unchanged' ? '版本及记录指纹一致，无需升级。' : blockers[plan.data.reason] ?? '当前条件不满足升级要求，请刷新记录并核对来源后重试。'}</p>
        <button ref={cancel} onClick={() => plan.reset()}>返回</button>
      </>}
    </>}
    {error && <p role="alert">{error}</p>}
    {attempt?.state === 'submitting' && <p role="status">正在提交升级请求…</p>}
    {attempt?.state === 'unresolved' && <div role="alert"><p>请求结果尚未确认。请查询原请求，不要重复生成升级。</p><button onClick={submit}>查询原升级请求</button></div>}
    {attempt?.state === 'accepted' && <div aria-live="polite">
      {operation.isError || operation.data === null ? <p role="alert">升级状态暂时无法确认。<button onClick={() => { void operation.refetch(); }}>重新查询</button></p>
        : result?.phase === 'completed' ? <><p>技能文件升级完成：{result.version}。</p><DiscoveryStatus confirmed={result.discovery === 'confirmed'} checkedAt={operation.dataUpdatedAt}
            refreshing={operation.isFetching} refresh={() => { void operation.refetch(); }} /><p>这是原操作的结果；当前是否安装请以已安装列表为准。</p></>
          : result?.phase === 'failed' ? <p role="alert">升级未完成，恢复状态：{result.recovery === 'restored' ? '已恢复旧版本' : '请核对操作记录'}。</p>
            : result && ['recovery-required', 'unknown'].includes(result.phase) ? <p role="alert">升级需要恢复核验，请勿重复提交。</p>
              : <p role="status">正在升级，请稍候…</p>}
      <p>操作 ID：{attempt.operationId}</p>
      {!operation.isError && operation.data && <OperationDetails operation={operation.data} />}
      {!operation.isError && operation.data && (['recovery-required', 'unknown'].includes(operation.data.phase)
        || (operation.data.phase === 'completed' && operation.data.warningCount > 0)) && <div>
        <p>核验原升级的全部目标：提交前恢复旧版本，已提交则只清理残留，不重复升级。</p>
        <button disabled={!writableHost.canWrite || recovery.isPending} onClick={() => recovery.mutate()}>{recovery.isPending ? '正在核验并恢复…' : '核验并恢复'}</button>
      </div>}
      {recovery.isError && !recoveryConfirmed && <p role="alert">{recovery.error.message}</p>}
      {!recovery.isPending && !operation.isError && operation.data && (operation.data.phase === 'completed'
        || (operation.data.phase === 'failed' && ['restored', 'not-needed'].includes(operation.data.recovery))) && <button onClick={() => {
          try { services.upgradeRequests.closeResult(key, operation.data!); plan.reset(); setError(null); onClosed?.(); }
          catch { setError('无法关闭本次结果，请重新查询后重试。'); }
        }}>关闭本次结果</button>}
    </div>}
  </section>;
}

/** Results remain reachable when inventory changes or temporarily cannot be read. */
export function UpgradeHistory({ services, sourceRevision, source, onClosed }: {
  services: UpgradeServices; sourceRevision: string; source: string; onClosed?(): void;
}) {
  useSyncExternalStore(services.upgradeRequests.subscribe, services.upgradeRequests.getRevision, () => 0);
  let entries;
  try { entries = services.upgradeRequests.list(services.sessionId, source); }
  catch { return <p role="alert">无法读取已保存的升级请求，请恢复浏览器存储后重试。</p>; }
  if (!entries.length) return null;
  return <section aria-label="未关闭的升级结果"><h3>升级操作</h3>
    <p>安装记录变化后，仍可在这里查询原请求并核对结果。</p>
    {entries.map(item => <UpgradeControl key={item.key} services={services} sourceRevision={sourceRevision} source={source} item={item} onClosed={onClosed} />)}
  </section>;
}
