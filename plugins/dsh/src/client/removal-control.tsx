import { canCollapseHistory } from './operation-presentation.js';
import { OperationDetails } from './operation-details.js';
import { HostWriteStatus, useWritableHost, type CompatibilityServices } from './host-compatibility.js';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useMutation } from '@tanstack/react-query';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { HostExecutePlanRequest, HostRecoveryRequest, NativeOperation, UninstallPlanView, InstalledSkillList } from '../host/types.js';
import { type RemovalRequests, upgradeRequestKey } from './upgrade-requests.js';
import { Dialog, DialogContent, DialogTitle, DialogDescription, Button } from './ui.js';
import { SkillAvatar } from './skill-card.js';
import { NativeControlStyles } from './native-control-styles.js';
import { useInstallationOperation } from './use-installation-operation.js';

export interface RemovalServices extends CompatibilityServices {
  sessionId: string;
  removalRequests: RemovalRequests;
  prepareUninstall(revision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<UninstallPlanView>>;
  executePlan(input: HostExecutePlanRequest, signal: AbortSignal): Promise<RemoteResult<NativeOperation | null>>;
  operation(id: string, host: string): Promise<RemoteResult<NativeOperation | null>>;
  recoverOperation(input: HostRecoveryRequest): Promise<RemoteResult<NativeOperation | null>>;
}
const blockers: Record<string, string> = {
  'shared-target': '该安装位置存在共享归属，暂不能卸载。',
  'local-changes': '本地文件已修改，本次卸载已阻止。请先妥善处理修改。',
  'metadata-invalid': '缺少可靠的安装基线，暂不能卸载。',
  'record-changed': '安装记录已变化，请刷新列表后重新检查。',
  'recovery-required': '有未完成的操作需要恢复核验，暂不能卸载。',
  'target-alias': '安装位置发生重定向，请先核对本地目录。',
};

export function RemovalControl({ services, sourceRevision, source, item, eligible = false, onCompleted, onClosed, history = false, presentation = 'panel' }: {
  services: RemovalServices; sourceRevision: string; source: string; eligible?: boolean; history?: boolean; presentation?: 'panel' | 'dialog';
  item: { itemKey?: string; namespace: string; slug: string; scope: string; version?: string; state?: InstalledSkillList['items'][number]['state'] }; onCompleted?(): void; onClosed?(): void;
}) {
  const writableHost = useWritableHost(services);
  const key = upgradeRequestKey(services.sessionId, source, item);
  const attempt = useSyncExternalStore(services.removalRequests.subscribe, () => services.removalRequests.get(key), () => undefined);
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const [dialogOpen, setDialogOpen] = useState(presentation === 'dialog');
  const prepared = useRef(false);
  const opener = useRef(typeof document === 'undefined' ? null : document.activeElement);
  const trigger = useRef<HTMLButtonElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now);
  const plan = useMutation({ mutationFn: async () => {
    if (!writableHost.canWrite) throw new Error('当前 DSH 写入兼容性未通过核验。');
    if (!item.itemKey) throw new Error('请从当前安装记录重新发起卸载。');
    const response = await services.prepareUninstall(sourceRevision, item.itemKey, new AbortController().signal);
    if (!response.ok) throw new Error('无法准备卸载，请刷新记录并检查当前会话的写入权限。');
    return response.value;
  } });
  useEffect(() => {
    if (presentation === 'dialog' && !prepared.current && writableHost.canWrite && eligible && !attempt) {
      prepared.current = true;
      plan.mutate();
    }
  }, [presentation, writableHost.canWrite, eligible, attempt, plan.mutate]);
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
      if (!response.ok || !response.value) throw new Error('恢复结果未确认，请查询原操作并核对当前目录及写入权限。');
    } finally {
      await operation.refetch();
    }
  } });
  useEffect(() => {
    if (result?.phase === 'completed' && ['uninstall', 'remove-stale-record'].includes(result.kind)) onCompleted?.();
  }, [result?.phase, result?.kind, onCompleted]);
  const expired = !!plan.data?.expiresAt && now >= Date.parse(plan.data.expiresAt);
  const submit = () => {
    const planId = attempt?.request.planId ?? plan.data?.planId;
    const host = attempt?.hostInstanceId ?? plan.data?.hostInstanceId;
    if ((!attempt && !writableHost.canWrite) || !planId || !host || (!attempt && (!plan.data?.expiresAt || Date.now() >= Date.parse(plan.data.expiresAt)))) return;
    setError(null);
    void services.removalRequests.submit(key, planId, host, async input => {
      const response = await services.executePlan(input, new AbortController().signal);
      if (!response.ok) throw new Error('卸载请求未确认。');
      return response.value;
    }).catch(error => setError(error instanceof Error ? error.message : '卸载请求尚未提交。'));
  };
  if (!eligible && !attempt) return null;
  const kind = result?.kind ?? plan.data?.kind;
  const stale = kind ? kind === 'remove-stale-record' : item.state === 'missing';
  const cancelPlan = () => {
    if (presentation === 'dialog' && !operation.isError && operation.data && recoveryConfirmed) {
      try { services.removalRequests.closeResult(key, operation.data); }
      catch { setError('原结果暂不能关闭，请稍后核验。'); return; }
    }
    setDialogOpen(false); plan.reset();
    if (presentation === 'dialog') onClosed?.();
    else trigger.current?.focus();
  };
  const confirmation = <Dialog open={dialogOpen} onOpenChange={open => { if (!open) cancelPlan(); }}>
    <DialogContent className="astron-sh-dialog astron-sh-removal-dialog" container={container} onOpenAutoFocus={event => {
      event.preventDefault(); container?.querySelector<HTMLButtonElement>('[data-removal-cancel]')?.focus();
    }} onCloseAutoFocus={event => { event.preventDefault(); if (opener.current instanceof HTMLElement && opener.current.isConnected) opener.current.focus(); }}>
      <DialogTitle>{stale ? '清理安装记录' : '卸载技能'}</DialogTitle>
      <DialogDescription>{stale ? '仅清理所选范围的失效记录' : '确认要移除这项技能吗？'}</DialogDescription>
      <RemovalConfirmationSummary item={item} version={plan.data?.currentVersion ?? result?.version ?? item.version} stale={stale} />
      <HostWriteStatus state={writableHost} />
      {!attempt && <>
        {plan.isPending && <p role="status">正在检查安装状态…</p>}
        {plan.isError && <p role="alert">{plan.error.message}</p>}
        {plan.data?.status === 'blocked' && <p role="alert">{blockers[plan.data.reason] ?? '无法核验当前安装，请刷新记录后重试。'}</p>}
        {expired && <p role="alert">确认已过期，请重新检查。</p>}
      </>}
      {attempt && <div className="astron-sh-removal-status" aria-live="polite">
        {attempt.state === 'submitting' ? <p role="status">正在卸载…</p>
          : attempt.state === 'unresolved' || operation.isError || operation.data === null ? <p role="alert">卸载结果暂未确认，请查询原请求。</p>
            : result?.phase === 'completed' ? <p role="status">{operation.data?.phase === 'completed' ? stale ? '安装记录已清理' : '卸载完成' : '正在确认卸载结果…'}</p>
              : result?.phase === 'failed' ? <p role="alert">卸载未完成，请检查安装状态。</p>
                : result && ['recovery-required', 'unknown'].includes(result.phase) ? <p role="alert">卸载结果需要核验，请勿重复操作。</p> : <p role="status">正在卸载…</p>}
        {!!result?.warningCount && <p role="alert">仍有残留需要核验。</p>}
        {attempt.state === 'unresolved' && <Button size="sm" onClick={submit}>查询原请求</Button>}
        {attempt.state === 'accepted' && (operation.isError || operation.data === null) && <Button size="sm" onClick={() => { void operation.refetch(); }}>查询原请求</Button>}
        {result && (['recovery-required', 'unknown'].includes(result.phase) || (result.phase === 'completed' && result.warningCount > 0)) && <Button size="sm" disabled={!writableHost.canWrite || recovery.isPending} onClick={() => recovery.mutate()}>核验并恢复</Button>}
        {recovery.isError && <p role="alert">{recovery.error.message}</p>}
      </div>}
      {error && <p role="alert">{error}</p>}
      <div className="astron-sh-dialog-actions">
        <Button data-removal-cancel onClick={cancelPlan}>{attempt ? '关闭' : '取消'}</Button>
        {!attempt && (plan.isError || expired) && <Button disabled={plan.isPending || !writableHost.canWrite} onClick={() => plan.mutate()}>重新检查</Button>}
        {!attempt && <Button variant="destructive" disabled={!writableHost.canWrite || expired || plan.isPending || plan.data?.status !== 'ready' || !eligible} onClick={submit}>{stale ? '确认清理' : '确认卸载'}</Button>}
      </div>
    </DialogContent>
  </Dialog>;
  if (presentation === 'dialog') return <div ref={setContainer} className="skillhub-controls"><NativeControlStyles />{container && confirmation}</div>;
  const content = <section aria-label={`卸载操作 ${item.slug}`} style={{ border: '1px solid #8885', borderRadius: 12, padding: 16, marginBlock: 16 }}>
    <h3>{stale ? '清理失效记录' : '卸载'} @{item.namespace}/{item.slug}</h3>
    <HostWriteStatus state={writableHost} />
    {!attempt && <>
      <button ref={trigger} disabled={!writableHost.canWrite || plan.isPending || !eligible} onClick={() => { setError(null); setDialogOpen(true); plan.mutate(); }}>{plan.isPending ? '正在核验操作范围…' : stale ? '清理失效记录' : '卸载此安装位置'}</button>
      {plan.isError && <p role="alert">{plan.error.message}</p>}
      {plan.data?.status === 'blocked' && <p role="alert">{blockers[plan.data.reason] ?? '当前记录或目录无法通过核验，暂不能卸载。'}</p>}
    </>}
    <div ref={setContainer}>{container && confirmation}</div>
    {error && <p role="alert">{error}</p>}
    {attempt?.state === 'submitting' && <p role="status">正在提交卸载请求…</p>}
    {attempt?.state === 'unresolved' && <div role="alert"><p>请求结果尚未确认，请查询原请求。</p><button onClick={submit}>查询原卸载请求</button></div>}
    {attempt?.state === 'accepted' && <div aria-live="polite">
      {operation.isError || operation.data === null ? <p role="alert">卸载状态暂时无法确认。<button onClick={() => { void operation.refetch(); }}>重新查询</button></p>
        : result?.phase === 'completed' ? <><p>{result.kind === 'remove-stale-record' ? '失效安装记录已清理。' : '此安装位置已卸载。'}</p>
          <p>其他安装位置和远端技能不受影响；DSH 仍可能从其他位置加载同名技能。</p>
          {result.warningCount > 0 && <p role="alert">有隔离残留或操作记录需要进一步核验。</p>}</>
          : result?.phase === 'failed' ? <p role="alert">卸载未完成。{result.recovery === 'restored' ? '原文件已恢复。' : result.recovery === 'not-needed' ? '无需恢复文件。' : '请核对恢复状态。'}</p>
            : result && ['recovery-required', 'unknown'].includes(result.phase) ? <p role="alert">此操作需要恢复核验，请勿重复提交。</p>
              : <p role="status">正在处理卸载…</p>}
      {result && result.warningCount > 0 && <p role="alert">此操作存在需核验告警，请查看原操作结果详情并核验恢复状态。</p>}
      <p>操作 ID：{attempt.operationId}</p>
      {!operation.isError && operation.data && <OperationDetails operation={operation.data} />}
      {!operation.isError && operation.data && (['recovery-required', 'unknown'].includes(operation.data.phase)
        || (operation.data.phase === 'completed' && operation.data.warningCount > 0)) && <div>
        <p>核验原操作：提交前恢复原文件，已提交则只清理残留，不重复卸载。</p>
        <button disabled={!writableHost.canWrite || recovery.isPending} onClick={() => recovery.mutate()}>{recovery.isPending ? '正在核验并恢复…' : '核验并恢复'}</button>
      </div>}
      {recovery.isError && !recoveryConfirmed && <p role="alert">{recovery.error.message}</p>}
      {!recovery.isPending && !operation.isError && operation.data && (operation.data.phase === 'completed'
        || (operation.data.phase === 'failed' && ['restored', 'not-needed'].includes(operation.data.recovery))) && <button onClick={() => {
          try { services.removalRequests.closeResult(key, operation.data!); plan.reset(); setError(null); onClosed?.(); }
          catch { setError('无法关闭本次结果，请重新查询后重试。'); }
        }}>关闭本次卸载结果</button>}
    </div>}
  </section>;
  if (!history) return content;
  const foldable = canCollapseHistory(operation.data, operation.isError);
  return <details className="astron-sh-history-entry" open={foldable ? undefined : true}
    onToggle={event => { if (!foldable && !event.currentTarget.open) event.currentTarget.open = true; }}>
    <summary><strong>{item.slug}</strong><span>卸载 · {item.scope === 'user' ? '当前用户' : '当前项目'}</span><span>{foldable ? '已完成 · 查看记录' : operation.isError || operation.data === null ? '状态暂未确认' : result?.phase === 'failed' ? '操作失败' : result?.phase === 'recovery-required' ? '需要恢复' : result?.phase === 'unknown' ? '结果未知' : result?.phase === 'completed' ? '已完成 · 需核验告警' : attempt?.state === 'unresolved' ? '请求待确认' : '处理中'}</span></summary>
    {content}
  </details>;
}

export function RemovalHistory({ services, sourceRevision, source, onClosed }: {
  services: RemovalServices; sourceRevision: string; source: string; onClosed?(): void;
}) {
  useSyncExternalStore(services.removalRequests.subscribe, services.removalRequests.getRevision, () => 0);
  let entries;
  try { entries = services.removalRequests.list(services.sessionId, source); }
  catch { return <p role="alert">无法读取已保存的卸载请求，请恢复浏览器存储后重试。</p>; }
  if (!entries.length) return null;
  return <section className="astron-sh-history" aria-label="未关闭的卸载结果"><h3>卸载操作记录</h3>
    {entries.map(item => <RemovalControl key={item.key} services={services} sourceRevision={sourceRevision} source={source} item={item} onClosed={onClosed} history />)}
  </section>;
}

/** Only information needed to confirm the selected target; no raw host paths or plan metadata. */
export function RemovalConfirmationSummary({ item, version, stale }: {
  item: { namespace: string; slug: string; scope: string }; version?: string; stale: boolean;
}) {
  return <><div className="astron-sh-removal-identity"><SkillAvatar namespace={item.namespace} slug={item.slug} /><div>
    <strong>{item.slug}</strong><p>@{item.namespace}/{item.slug}{version ? ` · v${version}` : ''}</p></div></div>
    <div className="astron-sh-removal-scope"><span>卸载范围</span><strong>{item.scope === 'user' ? '所有项目（当前用户）' : '仅此项目'}</strong></div>
    <p className="astron-sh-removal-impact">{stale ? '仅移除失效记录，不删除文件。' : item.scope === 'user' ? '移除当前用户共享的技能，项目内的独立安装保留。' : '仅移除此项目的安装，用户级及其他项目的安装保留。'}</p></>;
}
