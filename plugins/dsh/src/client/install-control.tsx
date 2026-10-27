import { UseSkillButton, type SkillUseServices } from './use-skill.js';
import { UpgradeControl, type UpgradeServices } from './upgrade-control.js';
import { InstallationProgressSummary, type InstallationProgressJob } from './installation-progress.js';
import { isInventoryScopeVerified } from './installed-inventory.js';
import { HostWriteStatus, useWritableHost, type CompatibilityServices } from './host-compatibility.js';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { SelectField, Button, Dialog, DialogContent, DialogDescription, DialogTitle } from './ui.js';
import { useInstallationOperation } from './use-installation-operation.js';
import { useQuery } from '@tanstack/react-query';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { HostInstallRequest, InstallationTargetList, InstalledSkillList, NativeOperation, SkillDetail, InstalledUpdateCheck } from '../host/types.js';
import type { InstallationRequests } from './installation-requests.js';

export interface InstallationServices extends CompatibilityServices {
  sessionId: string;
  skillUse?: SkillUseServices;
  upgrades?: UpgradeServices;
  registryIdentity?(revision: string): Promise<string>;
  checkVersionUpdate?(revision: string, itemKey: string, version: string, signal: AbortSignal): Promise<RemoteResult<InstalledUpdateCheck>>;
  manageInstalled?(revision: string, item: InstalledSkillList['items'][number]): void;
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

export function InstallControl({ detail, sourceRevision, services, initiatedFrom, onContextStale, presentation = 'panel', onClose, onSubmitted, onManage }: {
  detail: SkillDetail; sourceRevision: string; services: InstallationServices; initiatedFrom: HostInstallRequest['initiatedFrom']; onContextStale?(): void; presentation?: 'panel' | 'dialog'; onClose?(): void; onSubmitted?(job: InstallationProgressJob): void; onManage?(item: InstalledSkillList['items'][number]): void;
}) {
  const panel = useRef<HTMLElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const [confirmation, setConfirmation] = useState<{ key: string; targetToken: string; reinstall: boolean } | null>(null);
  const writableHost = useWritableHost(services);
  const [scope, setScope] = useState<'project' | 'user'>('user');
  const [upgradeCandidate, setUpgradeCandidate] = useState<{ item: InstalledSkillList['items'][number]; version: string; source: string } | null>(null);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const refreshedToken = useRef<string | null>(null);
  const scopeChosen = useRef(false);
  const targets = useQuery({ queryKey: ['skillhub-install-targets', services.sessionId, detail.detailToken], retry: false,
    queryFn: async ({ signal }) => {
      const response = await services.targets(detail.detailToken, signal);
      if (!response.ok && response.error.code === 'skillhub/context-stale') throw new StaleTargetError('详情已过期，请重新读取。');
      return read(response);
    } });
  useEffect(() => {
    if (targets.error instanceof StaleTargetError && refreshedToken.current !== detail.detailToken) {
      refreshedToken.current = detail.detailToken;
      onContextStale?.();
    }
  }, [targets.error, detail.detailToken, onContextStale]);
  const host = targets.data?.hostInstanceId ?? '';
  const key = JSON.stringify([services.sessionId, host, sourceRevision, detail.namespace, detail.slug, detail.version, scope]);
  const attempt = useSyncExternalStore(services.requests.subscribe, () => services.requests.get(key), () => undefined);
  const operationId = attempt?.operationId ?? attempt?.operation?.operationId;
  const operation = useInstallationOperation(services, host, operationId);
  const target = targets.data?.targets.find(item => item.scope === scope);
  const local = useQuery({ queryKey: ['skillhub-installed', services.sessionId, sourceRevision], retry: false,
    enabled: true,
    queryFn: async ({ signal }) => read(await services.installed(sourceRevision, signal)) });
  const current = local.data?.items.find(item => item.namespace === detail.namespace && item.slug === detail.slug && item.scope === scope);
  const inventoryVerified = !local.isError && !local.isFetching && isInventoryScopeVerified(local.data, scope);
  const upgradeSource = useQuery({ queryKey: ['skillhub-upgrade-source', services.sessionId, sourceRevision], retry: false,
    enabled: !!services.upgrades && !!services.registryIdentity && current?.state === 'present' && current.version !== detail.version,
    queryFn: () => services.registryIdentity!(sourceRevision) });
  const versionUpdate = useQuery({ queryKey: ['skillhub-update-check', services.sessionId, sourceRevision, current?.itemKey, detail.version], retry: false,
    enabled: !!services.upgrades && !!services.checkVersionUpdate && inventoryVerified && current?.state === 'present' && current.version !== detail.version,
    queryFn: async ({ signal }) => read(await services.checkVersionUpdate!(sourceRevision, current!.itemKey, detail.version, signal)) });
  const absent = inventoryVerified && !current;
  const records = local.data?.items.filter(item => item.namespace === detail.namespace && item.slug === detail.slug) ?? [];
  const result = operation.data ?? attempt?.operation;
  const completed = result?.phase === 'completed';
  const progressVisible = !!attempt && !completed;
  useEffect(() => {
    if (scopeChosen.current || !targets.data || !local.data || local.isError || local.isFetching || targets.isFetching) return;
    scopeChosen.current = true;
    const initial = preferredInstallationScope(local.data, detail, targets.data.targets.map(item => item.scope));
    if (initial) setScope(initial);
  }, [targets.data, targets.isFetching, local.data, local.isError, local.isFetching, detail]);
  useEffect(() => {
    if (attempt && onSubmitted && (attempt.state === 'submitting' || attempt.state === 'unresolved'
      || (result && !['completed', 'failed', 'unknown', 'recovery-required'].includes(result.phase)))) {
      onSubmitted({ key, sessionId: services.sessionId, sourceRevision, hostInstanceId: host,
        namespace: detail.namespace, slug: detail.slug, version: detail.version, title: detail.title, scope });
    }
  }, [attempt, result, onSubmitted, key, services.sessionId, sourceRevision, host, detail, scope]);
  function manage() {
    if (!current || !inventoryVerified) return;
    try { if (onManage) onManage(current); else services.manageInstalled?.(sourceRevision, current); }
    catch { setSubmissionError('无法打开管理页面，请前往已安装重试。'); }
  }
  function submit(reinstall = false) {
    if (!target || !host || (!attempt && !writableHost.canWrite)) return;
    if (reinstall && (!absent || !writableHost.canWrite || operation.data?.phase !== 'completed' || !operationId)) return;
    setSubmissionError(null);
    const pending = services.requests.submit(key, { detailToken: detail.detailToken, targetToken: target.targetToken,
      hostInstanceId: host, initiatedFrom }, async input => read(await services.start(input, new AbortController().signal)), reinstall ? operationId : undefined)
      .catch(error => setSubmissionError(error instanceof Error ? error.message : '无法保存安装请求。'));
    if (services.requests.get(key)) onSubmitted?.({ key, sessionId: services.sessionId, sourceRevision, hostInstanceId: host, namespace: detail.namespace, slug: detail.slug, version: detail.version, title: detail.title, scope });
    void pending;
  }
  return <section ref={panel} aria-label="安装技能" className={`astron-sh-install-panel${presentation === 'dialog' ? ' astron-sh-install-dialog-control' : ''}`}>
    {presentation === 'panel' && <h3>安装管理</h3>}
    {upgradeCandidate && services.upgrades && <UpgradeControl key={`${upgradeCandidate.item.itemKey}:${upgradeCandidate.version}`} presentation="dialog" eligible
      services={services.upgrades} sourceRevision={sourceRevision} source={upgradeCandidate.source} item={upgradeCandidate.item}
      targetVersion={upgradeCandidate.version} onClosed={() => setUpgradeCandidate(null)} />}
    <HostWriteStatus state={writableHost} />
    {records.length > 0 ? <label className="astron-sh-scope-field">安装范围 <SelectField label="安装范围" value={scope} disabled={attempt?.state === 'submitting'} onValueChange={value => { scopeChosen.current = true; setScope(value as 'project' | 'user'); }}
      options={[{ value: 'project', label: '仅此项目', disabled: !targets.data?.targets.some(item => item.scope === 'project') && !records.some(item => item.scope === 'project') }, { value: 'user', label: '所有项目', disabled: !targets.data?.targets.some(item => item.scope === 'user') && !records.some(item => item.scope === 'user') }]} /></label> : <details className="astron-sh-install-advanced">
      <summary>高级选项{scope === 'project' ? ' · 当前项目' : ''}</summary>
      <label className="astron-sh-scope-field">安装范围 <SelectField label="安装范围" value={scope} disabled={attempt?.state === 'submitting'} onValueChange={value => { scopeChosen.current = true; setScope(value as 'project' | 'user'); }}
      options={[{ value: 'project', label: '仅此项目', disabled: !targets.data?.targets.some(item => item.scope === 'project') && !records.some(item => item.scope === 'project') }, { value: 'user', label: '所有项目', disabled: !targets.data?.targets.some(item => item.scope === 'user') && !records.some(item => item.scope === 'user') }]} /></label>
    </details>}
    <p className="astron-sh-scope-help">{scope === 'project' ? '仅当前项目可用' : '当前用户的所有项目可用'}</p>
    {targets.isError && <p role="alert">无法读取安装位置。<Button size="sm" onClick={() => { void targets.refetch(); }}>重试</Button></p>}
    {local.isPending || (!local.data && local.isFetching) ? <p role="status">正在读取安装状态…</p>
      : !isInventoryScopeVerified(local.data, scope) || local.isError ? <p role="alert">安装状态暂不可用。<Button size="sm" onClick={() => { void local.refetch(); }}>重试</Button></p>
        : current ? <div className="astron-sh-current-install" data-state={current.state}>
          <strong>{current.state === 'present' ? `已安装 v${current.version}` : '安装记录异常'}</strong>
          {(onManage || services.manageInstalled) && <Button size="sm" variant="destructive" disabled={!inventoryVerified} onClick={manage}>{current.state === 'present' ? '卸载' : '管理'}</Button>}
        </div> : records.length > 0 ? <p className="astron-sh-other-install">已安装：{records.map(item => `${item.scope === 'user' ? '所有项目' : '仅此项目'} v${item.version}`).join('、')}</p> : null}
    {upgradeSource.isError && <p role="alert">无法核验当前来源。<Button size="sm" onClick={() => { void upgradeSource.refetch(); }}>重试</Button></p>}
    {current?.state === 'present' && current.version !== detail.version && services.checkVersionUpdate && <div className="astron-sh-version-action">
      {versionUpdate.isFetching ? <p role="status">正在核对所选版本…</p> : versionUpdate.isError ? <p role="alert">无法核对所选版本。<Button size="sm" onClick={() => { void versionUpdate.refetch(); }}>重试</Button></p>
        : versionUpdate.data?.state === 'upgradable' && versionUpdate.data.candidateVersion === detail.version ? <Button variant="default" disabled={!inventoryVerified || !writableHost.canWrite || !upgradeSource.data || upgradeSource.isError || upgradeSource.isFetching} onClick={() => { if (upgradeSource.data) setUpgradeCandidate({ item: current, version: detail.version, source: upgradeSource.data }); }}>升级到 v{detail.version}</Button>
          : versionUpdate.data && <p role="status">{selectedVersionUpdateMessage(versionUpdate.data)}</p>}
    </div>}
    {inventoryVerified && !current && (!attempt || completed) && <Button ref={trigger} variant="default" disabled={!writableHost.canWrite || !target || targets.isFetching}
      onClick={() => { if (!target) return; if (presentation === 'dialog') submit(completed); else setConfirmation({ key, targetToken: target.targetToken, reinstall: completed }); }}>
      {scope === 'project' ? '安装到此项目' : '安装到所有项目'}</Button>}
    {inventoryVerified && current?.state === 'present' && current.version === detail.version && <UseSkillButton services={services.skillUse} revision={sourceRevision} itemKey={current.itemKey} onHandoff={onClose} onUsed={onClose} />}
    {current && completed && (result.warningCount > 0 || !!result.resultDetails?.warnings.length) && <p role="alert">安装有告警，请在已安装中核验。</p>}
    {submissionError && <p role="alert">{submissionError}</p>}
    {progressVisible && attempt && <InstallationProgressSummary attempt={attempt} operation={operation.data ?? attempt.operation} readFailed={operation.isError || operation.data === null}
      onCheck={() => { if (attempt.state === 'unresolved') submit(); else void operation.refetch(); }} />}
    {presentation === 'dialog' && <Button onClick={onClose}>{attempt ? '关闭' : '取消'}</Button>}
    <Dialog open={confirmation !== null} onOpenChange={open => { if (!open) setConfirmation(null); }}>
      <DialogContent container={panel.current} onCloseAutoFocus={event => { event.preventDefault(); trigger.current?.focus(); }}>
        <DialogTitle>确认{confirmation?.reinstall ? '重新安装' : '安装'}技能？</DialogTitle>
        <DialogDescription>确认技能、版本和安装范围后，才会向当前 DSH Host 提交安装请求。</DialogDescription>
        <dl><dt>技能</dt><dd>@{detail.namespace}/{detail.slug}</dd><dt>版本</dt><dd>{detail.version}</dd>
          <dt>安装范围</dt><dd>{scope === 'project' ? '仅此项目' : '所有项目'}</dd></dl>
        {(confirmation?.key !== key || confirmation?.targetToken !== target?.targetToken) && <p role="alert">会话、来源或安装目标已变化，请取消后重新确认。</p>}
        <div className="astron-sh-dialog-actions"><Button onClick={() => setConfirmation(null)}>取消</Button>
          <Button variant="default" disabled={(confirmation?.key !== key || confirmation?.targetToken !== target?.targetToken) || !writableHost.canWrite || !target || targets.isFetching || attempt?.state === 'submitting' || !inventoryVerified || !!current}
            onClick={() => { if ((confirmation?.key !== key || confirmation?.targetToken !== target?.targetToken)) return; if (!inventoryVerified || current) return; const reinstall = confirmation.reinstall; setConfirmation(null); submit(reinstall); }}>确认安装</Button></div>
      </DialogContent>
    </Dialog>
  </section>;
}

class StaleTargetError extends Error {}

/** Prefer the scope already installed, rather than presenting a misleading second install. */
export function preferredInstallationScope(inventory: InstalledSkillList, detail: Pick<SkillDetail, 'namespace' | 'slug'>, available: readonly ('user' | 'project')[]): 'user' | 'project' | undefined {
  const records = inventory.items.filter(item => item.namespace === detail.namespace && item.slug === detail.slug);
  return (['project', 'user'] as const).find(scope => records.some(item => item.scope === scope))
    ?? (available.includes('user') ? 'user' : undefined);
}

export function selectedVersionUpdateMessage(update: InstalledUpdateCheck): string {
  if (update.state === 'current') return '已是此版本，无需升级。';
  const messages: Record<string, string> = { 'remote-older': '所选版本较旧，暂不支持降级。', 'unknown-order': '无法可靠比较版本，暂不能升级。',
    'same-version-content-changed': '此版本内容已变化，请核实来源。', 'local-record-unverified': '请先核验当前安装。' };
  return messages[update.reason] ?? '所选版本暂不可升级，请重新读取版本信息。';
}
