import { isInventoryScopeVerified } from './installed-inventory.js';
import { canCollapseHistory } from './operation-presentation.js';
import { OperationDetails } from './operation-details.js';
import { DiscoveryStatus } from './discovery-status.js';
import { HostWriteStatus, useWritableHost, type CompatibilityServices } from './host-compatibility.js';
import { useRef, useState, useSyncExternalStore } from 'react';
import { SelectField, Button, Dialog, DialogContent, DialogDescription, DialogTitle } from './ui.js';
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
  const panel = useRef<HTMLElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [confirmation, setConfirmation] = useState<{ key: string; targetToken: string; reinstall: boolean } | null>(null);
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
  const foldable = canCollapseHistory(operation.data, operation.isError);
  const target = targets.data?.targets.find(item => item.scope === scope);
  const local = useQuery({ queryKey: ['skillhub-installed', services.sessionId, sourceRevision], retry: false,
    enabled: true,
    queryFn: async ({ signal }) => read(await services.installed(sourceRevision, signal)) });
  const current = local.data?.items.find(item => item.namespace === detail.namespace && item.slug === detail.slug && item.scope === scope);
  const inventoryVerified = !local.isError && !local.isFetching && isInventoryScopeVerified(local.data, scope);
  const absent = inventoryVerified && !current;
  function submit(reinstall = false) {
    if (!target || !host || (!attempt && !writableHost.canWrite)) return;
    if (reinstall && (!absent || !writableHost.canWrite || operation.data?.phase !== 'completed' || !operationId)) return;
    setSubmissionError(null);
    void services.requests.submit(key, { detailToken: detail.detailToken, targetToken: target.targetToken,
      hostInstanceId: host, initiatedFrom }, async input => read(await services.start(input, new AbortController().signal)), reinstall ? operationId : undefined)
      .catch(error => setSubmissionError(error instanceof Error ? error.message : '无法保存安装请求。'));
  }
  return <section ref={panel} aria-label="安装技能" className="astron-sh-install-panel">
    <h3>安装 {detail.version}</h3>
    <HostWriteStatus state={writableHost} />
    <label className="astron-sh-scope-field">安装范围 <SelectField label="安装范围" value={scope} disabled={attempt?.state === 'submitting'} onValueChange={value => setScope(value as 'project' | 'user')}
      options={[{ value: 'project', label: '当前项目' }, { value: 'user', label: '当前用户' }]} /></label>
    {targets.isPending ? <p role="status">正在确认安装位置…</p> : targets.isError
      ? <p role="alert">无法确认安装位置。<button onClick={() => { void targets.refetch(); }}>重试</button></p>
      : !target ? <p>当前范围不可安装，请选择其他范围或检查 DSH 会话设置。</p> : null}
    {local.isPending || local.isFetching ? <p role="status">正在核验当前安装记录…</p>
      : !inventoryVerified ? <p role="alert">当前安装记录无法完整核验，请重试或前往已安装查看。</p>
        : current ? current.state !== 'present' ? <p role="alert">当前范围记录版本：{current.version}。本地状态异常，请前往已安装核验。</p>
          : <div className="astron-sh-current-install"><strong>{current.version === detail.version ? '已安装此版本' : `已安装 v${current.version}`}</strong><p>{current.version === detail.version ? '当前范围文件存在。' : '此范围已有其他版本。请在已安装中检查升级，不能直接覆盖或降级。'}</p></div>
            : <p className="astron-sh-install-state">当前范围未安装</p>}
    {!attempt && <Button ref={trigger} variant="default" disabled={!writableHost.canWrite || !target || targets.isFetching || !inventoryVerified || !!current} onClick={() => target && setConfirmation({ key, targetToken: target.targetToken, reinstall: false })}>{current?.state === 'present' && current.version === detail.version ? '已安装此版本' : `安装到${scope === 'project' ? '当前项目' : '当前用户'}`}</Button>}
    {(!inventoryVerified) && !local.isPending && <Button disabled={local.isFetching} onClick={() => { void local.refetch(); }}>核验当前安装状态</Button>}
    {submissionError && <p role="alert">{submissionError}</p>}
    {attempt?.state === 'submitting' && <p role="status">正在提交安装请求…</p>}
    {attempt?.state === 'unresolved' && <div role="alert"><p>尚未确认请求结果。查询原请求不会重复安装。</p><button onClick={() => submit()} disabled={!target}>查询原请求</button></div>}
    {attempt?.state === 'accepted' && <div aria-live="polite">
      {operation.isError || operation.data === null ? <p role="alert">安装状态暂时无法确认。<button onClick={() => { void operation.refetch(); }}>重新查询</button></p>
        : result?.phase === 'completed' ? <>
          {(result.warningCount > 0 || !!result.resultDetails?.warnings.length) && <p role="alert">此操作存在需核验告警，请展开操作详情查看。</p>}
          {result.discovery !== 'confirmed' && <p role="status">尚未确认 DSH 发现此操作对应的技能内容，请在操作详情中重新核验。</p>}
        </> : result?.phase === 'failed' ? <p role="alert">安装未完成。请查看操作详情并确认恢复状态。</p>
          : result && ['recovery-required', 'unknown'].includes(result.phase) ? <p role="alert">操作需要恢复核验，请勿重复安装。</p>
            : <p role="status">正在安装，请稍候…</p>}
      {operation.data?.phase === 'completed' && absent && <Button ref={trigger} disabled={!writableHost.canWrite || !target || targets.isFetching} onClick={() => target && setConfirmation({ key, targetToken: target.targetToken, reinstall: true })}>重新安装到{scope === 'project' ? '当前项目' : '当前用户'}</Button>}
      <details className="astron-sh-operation-fold" open={foldable ? undefined : true} onToggle={event => { if (!foldable && !event.currentTarget.open) event.currentTarget.open = true; }}><summary>操作详情</summary>
        <p>这是原操作回执，当前安装状态以本页核验的库存为准。</p>
        {result?.phase === 'completed' && <p>{result.files === 'already-installed' ? '此版本已安装。' : '技能文件安装完成。'}</p>}
        {result && <p>{result.context.sourceLabel} · {result.targetLabel} · {result.version}</p>}
        {result?.phase === 'completed' && <DiscoveryStatus confirmed={result.discovery === 'confirmed'} checkedAt={operation.dataUpdatedAt} refreshing={operation.isFetching} refresh={() => { void operation.refetch(); }} />}
        {!operation.isError && operation.data && <OperationDetails operation={operation.data} />}
        <button disabled={local.isFetching} onClick={() => { void local.refetch(); }}>核验当前安装状态</button>
      </details>
    </div>}
    <Dialog open={confirmation !== null} onOpenChange={open => { if (!open) setConfirmation(null); }}>
      <DialogContent container={panel.current} onCloseAutoFocus={event => { event.preventDefault(); trigger.current?.focus(); }}>
        <DialogTitle>确认{confirmation?.reinstall ? '重新安装' : '安装'}技能？</DialogTitle>
        <DialogDescription>确认技能、版本和安装范围后，才会向当前 DSH Host 提交安装请求。</DialogDescription>
        <dl><dt>技能</dt><dd>@{detail.namespace}/{detail.slug}</dd><dt>版本</dt><dd>{detail.version}</dd>
          <dt>安装范围</dt><dd>{scope === 'project' ? '当前项目' : '当前用户'}</dd></dl>
        {(confirmation?.key !== key || confirmation?.targetToken !== target?.targetToken) && <p role="alert">会话、来源或安装目标已变化，请取消后重新确认。</p>}
        <div className="astron-sh-dialog-actions"><Button onClick={() => setConfirmation(null)}>取消</Button>
          <Button variant="default" disabled={(confirmation?.key !== key || confirmation?.targetToken !== target?.targetToken) || !writableHost.canWrite || !target || targets.isFetching || attempt?.state === 'submitting' || !inventoryVerified || !!current}
            onClick={() => { if ((confirmation?.key !== key || confirmation?.targetToken !== target?.targetToken)) return; if (!inventoryVerified || current) return; const reinstall = confirmation.reinstall; setConfirmation(null); submit(reinstall); }}>确认安装</Button></div>
      </DialogContent>
    </Dialog>
  </section>;
}
