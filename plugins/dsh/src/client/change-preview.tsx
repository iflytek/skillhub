import { HostWriteStatus, useWritableHost } from './host-compatibility.js';
import { ChangeRequests } from './upgrade-requests.js';
import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { ChangesView, ChangeDiffView, FileResetPlanView, HostExecutePlanRequest, NativeOperation, UpgradePlanView } from '../host/types.js';
import { Badge, Button, Card, CardHeader, CardContent } from './ui.js';
import type { UpgradeServices } from './upgrade-control.js';

export interface ChangeServices {
  inspect(revision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<ChangesView>>;
  diff(revision: string, itemKey: string, snapshotId: string, path: string, signal: AbortSignal): Promise<RemoteResult<ChangeDiffView>>;
  preview(revision: string, itemKey: string, snapshotId: string, version: string | null, backup: boolean, signal: AbortSignal): Promise<RemoteResult<ChangesView>>;
  confirm(revision: string, itemKey: string, snapshotId: string, signal: AbortSignal): Promise<RemoteResult<UpgradePlanView>>;
  prepareReset(revision: string, itemKey: string, snapshotId: string, paths: string[], signal: AbortSignal): Promise<RemoteResult<FileResetPlanView>>;
  reset(revision: string, itemKey: string, input: HostExecutePlanRequest & { hostInstanceId: string }, signal: AbortSignal): Promise<RemoteResult<NativeOperation | null>>;
}
function value<T>(response: RemoteResult<T>): T { if (!response.ok) throw new Error('读取或操作未确认，请重新检查安装状态。'); return response.value; }
export function ChangePreviewStyles() { return <style>{`
.astron-sh-changes{min-width:0;--changes-add:light-dark(#047857,#6ee7b7);--changes-add-bg:light-dark(#ecfdf5,#12352c);--changes-remove:light-dark(#be123c,#fda4af);--changes-remove-bg:light-dark(#fff1f2,#3b2029)}
.astron-sh-changes .changes-panel{box-shadow:none;overflow:hidden}
.astron-sh-changes .changes-heading{padding:18px 18px 14px;border-bottom:1px solid var(--astron-sh-border);gap:5px}
.astron-sh-changes .changes-heading h3{font-size:16px;line-height:24px;margin:0;font-weight:600}
.astron-sh-changes .changes-context{color:var(--astron-sh-muted);font-size:12px;line-height:18px;overflow-wrap:anywhere;margin:0}
.astron-sh-changes .changes-content{padding:16px 18px 18px}
.astron-sh-changes .changes-section-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;font-size:12px;color:var(--astron-sh-muted)}
.astron-sh-changes .changes-section-heading .astron-sh-button{margin:0;min-height:26px;height:26px;padding:2px 6px;font-size:12px}
.astron-sh-changes .changes-list{border:1px solid var(--astron-sh-border);border-radius:8px;overflow:hidden}
.astron-sh-changes .changes-file{position:relative;min-width:0}
.astron-sh-changes .changes-file+.changes-file{border-top:1px solid var(--astron-sh-border)}
.astron-sh-changes summary{display:flex;align-items:center;gap:9px;cursor:pointer;padding:11px 12px;min-width:0;list-style:none;background:var(--astron-sh-bg)}
.astron-sh-changes summary::-webkit-details-marker{display:none}
.astron-sh-changes summary:hover{background:var(--astron-sh-soft)}
.astron-sh-changes summary code{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0;flex:1;font:12px/20px ui-monospace,SFMono-Regular,Consolas,monospace}
.astron-sh-changes .changes-file.has-undo summary{padding-right:84px}
.astron-sh-changes .changes-file-chevron{width:14px;height:14px;margin-left:auto;flex:none;color:var(--astron-sh-muted);transition:transform .15s ease}
.astron-sh-changes details[open] .changes-file-chevron{transform:rotate(90deg)}
.astron-sh-changes .changes-file.has-undo .changes-file-chevron{position:absolute;right:12px;top:15px}
.astron-sh-changes .changes-undo.astron-sh-button{position:absolute;right:31px;top:7px;min-height:28px;height:28px;font-size:12px;padding:3px 7px;margin:0;color:var(--astron-sh-muted)}
.astron-sh-changes .changes-badge-added{color:var(--changes-add);background:var(--changes-add-bg);border-color:transparent}
.astron-sh-changes .changes-badge-deleted{color:var(--changes-remove);background:var(--changes-remove-bg);border-color:transparent}
.astron-sh-changes .changes-code{max-height:320px;overflow:auto;background:var(--astron-sh-bg);font:12px/22px ui-monospace,SFMono-Regular,Consolas,monospace;margin:0;border-top:1px solid var(--astron-sh-border);tab-size:2}
.astron-sh-changes .changes-line{display:flex;white-space:pre;min-width:max-content;padding-right:12px}
.astron-sh-changes .changes-line-number{width:40px;flex:none;text-align:right;padding-right:10px;color:var(--astron-sh-muted);user-select:none;border-right:1px solid var(--astron-sh-border);font-variant-numeric:tabular-nums}
.astron-sh-changes .changes-line-sign{width:25px;text-align:center;flex:none;user-select:none}
.astron-sh-changes .changes-line.added{background:var(--changes-add-bg);color:var(--changes-add)}
.astron-sh-changes .changes-line.deleted{background:var(--changes-remove-bg);color:var(--changes-remove)}
.astron-sh-changes .changes-inline-message{padding:12px;margin:0;font-size:12px;color:var(--astron-sh-muted)}
.astron-sh-changes .changes-footer{display:flex;justify-content:flex-end;gap:8px;flex-wrap:wrap;margin-top:16px;border-top:1px solid var(--astron-sh-border);padding-top:14px}
.astron-sh-changes .changes-footer .astron-sh-button{margin:0}
.astron-sh-changes .changes-confirm{background:var(--astron-sh-soft);border:1px solid var(--astron-sh-border);border-radius:8px;padding:14px;margin-top:14px;font-size:13px;overflow-wrap:anywhere}
.astron-sh-changes .changes-confirm strong{font-size:13px}
.astron-sh-changes .changes-confirm p{font-size:12px;color:var(--astron-sh-muted);margin:6px 0 12px}
.astron-sh-changes .changes-confirm ul{font-size:12px;padding-left:18px;margin-bottom:12px}
.astron-sh-changes .changes-release-note{margin:0 0 16px;font-size:13px;line-height:21px}
.astron-sh-changes .changes-release-note strong{font-size:12px;font-weight:600}.astron-sh-changes .changes-release-note p{margin:5px 0 0;color:var(--astron-sh-muted)}
.astron-sh-changes .changes-backup{display:flex;align-items:flex-start;justify-content:space-between;gap:8px;padding:10px 12px;margin-bottom:14px;border-radius:7px;background:var(--astron-sh-soft);font-size:12px}
.astron-sh-changes .changes-backup code{display:block;font-size:11px;line-height:17px;color:var(--astron-sh-muted);overflow-wrap:anywhere;min-width:0}
.astron-sh-changes .changes-backup .astron-sh-button{margin:0;min-height:26px;height:26px;padding:2px 6px;font-size:12px}
@media(max-width:640px){.astron-sh-changes .changes-code{max-height:240px}.astron-sh-changes .changes-heading{padding:14px}.astron-sh-changes .changes-content{padding:12px 14px 14px}.astron-sh-changes summary{gap:7px;padding-left:10px}.astron-sh-changes .changes-line-number{width:32px}.astron-sh-changes .changes-confirm .astron-sh-button{margin:0 5px 0 0}}
`}</style>; }
function FileChange({ file, view, revision, itemKey, services, onUndo, disabled }: { file: ChangesView['changes'][number]; view: ChangesView; revision: string; itemKey: string; services: ChangeServices; onUndo?(trigger: HTMLButtonElement): void; disabled?: boolean }) {
  const [open, setOpen] = useState(false);
  const diff = useQuery({ queryKey: ['skillhub-change-file', revision, itemKey, view.snapshotId, file.path], enabled: open, retry: false, gcTime: 0,
    queryFn: async ({ signal }) => value(await services.diff(revision, itemKey, view.snapshotId!, file.path, signal)) });
  return <div className={`changes-file${onUndo ? ' has-undo' : ''}`}><details onToggle={event => setOpen(event.currentTarget.open)}><summary><Badge variant={file.kind === 'modified' ? 'info' : 'neutral'} className={`changes-badge-${file.kind}`}>{file.kind === 'added' ? '新增' : file.kind === 'deleted' ? '删除' : '修改'}</Badge><code title={file.path}>{file.path}</code><svg className="changes-file-chevron" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="m6 4 4 4-4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg></summary>
    {diff.isPending ? <p className="changes-inline-message" role="status">正在读取差异…</p> : diff.isError ? <p role="alert">无法读取差异。<Button size="sm" onClick={() => void diff.refetch()}>重试</Button></p>
      : diff.data.state === 'text' ? <><pre className="changes-code" tabIndex={0} aria-label={`${file.path} 文件差异，行号为差异预览行号`}>{diff.data.lines.map((line, index) => <span className={`changes-line ${line.kind}`} key={index}><span className="changes-line-number" title="差异预览行号" aria-hidden="true">{index + 1}</span><span className="changes-line-sign" aria-hidden="true">{line.kind === 'added' ? '+' : line.kind === 'deleted' ? '−' : ' '}</span><span>{line.text}</span></span>)}</pre>{diff.data.truncated && <p>差异过长，仅展示部分内容。</p>}</>
        : <p className="changes-inline-message" role="status">{diff.data.state === 'binary' ? '此文件不支持文本差异预览。' : diff.data.state === 'too-large' ? '文件超出预览限制。' : '原始内容暂不可用，无法比较。'}</p>}
  </details>{onUndo && <Button size="sm" variant="ghost" className="changes-undo" aria-label={`撤销 ${file.path} 的修改`} disabled={disabled} onClick={event => onUndo(event.currentTarget)}>撤销</Button>}</div>;
}

/** Sequential local handling → release differences → final explicit acceptance. */
export function ChangePreview({ services, revision, itemKey, upgrade, targetVersion, installationKey, onClose, onCompleted }: {
  services: UpgradeServices & { changes: ChangeServices }; revision: string; itemKey: string; upgrade: boolean; targetVersion?: string; installationKey?: string; onClose(): void; onCompleted?(version: string): void;
}) {
  const writable = useWritableHost(services);
  const [view, setView] = useState<ChangesView>(); const [error, setError] = useState(''); const [pending, setPending] = useState(false);
  const resetTrigger = useRef<HTMLButtonElement | null>(null);
  const resetCancel = useRef<HTMLButtonElement>(null);
  const [reset, setReset] = useState<FileResetPlanView>(); const [operation, setOperation] = useState<NativeOperation>();
  const [requests] = useState(() => new ChangeRequests(() => sessionStorage));
  const requestKeys = { reset: JSON.stringify([services.sessionId, installationKey ?? itemKey, 'reset']), upgrade: JSON.stringify([services.sessionId, installationKey ?? itemKey, 'upgrade']) };
  const activeKey = useRef(requestKeys.upgrade);
  useEffect(() => { if (reset && !pending) resetCancel.current?.focus(); }, [reset, pending]);
  const closeReset = () => { setReset(undefined); requestAnimationFrame(() => resetTrigger.current?.focus()); };
  const [resetting, setResetting] = useState(false); const controller = useRef(new AbortController());
  const execution = useRef<{ request: HostExecutePlanRequest & { hostInstanceId: string }; kind: 'reset' | 'upgrade' }>();
  useEffect(() => { const active = controller.current; return () => active.abort(); }, []);
  const signal = () => controller.current.signal;
  const load = async () => {
    for (const kind of ['reset', 'upgrade'] as const) {
    const saved = requests.get(requestKeys[kind]);
    if (saved) {
      activeKey.current = requestKeys[kind];
      execution.current = { kind, request: { ...saved.request, hostInstanceId: saved.hostInstanceId } };
      if (saved.operationId) { const original = value(await services.operation(saved.operationId, saved.hostInstanceId)); if (!original) throw new Error('原操作暂不可读，请重试。'); setResetting(original.kind === 'reset-files'); execution.current.kind = original.kind === 'reset-files' ? 'reset' : 'upgrade'; setOperation(original); return; }
      throw new Error('有未确认的文件操作，请查询原请求。');
    }
    }
    const local = value(await services.changes.inspect(revision, itemKey, signal()));
    signal().throwIfAborted();
    if (upgrade && local.status === 'ready' && !local.changes.length) await preview(local, false);
    else setView(local);
  };
  const run = (action: () => Promise<void>) => { if (pending) return; setPending(true); setError(''); void action().catch(failure => { if (!signal().aborted) setError(failure instanceof Error ? failure.message : '操作未完成。'); }).finally(() => { if (!signal().aborted) setPending(false); }); };
  useEffect(() => { run(load); /* The keyed parent owns context replacement. */
  }, []);
  async function preview(local: ChangesView, backup: boolean) {
    const result = value(await services.changes.preview(revision, itemKey, local.snapshotId!, targetVersion ?? null, backup, signal()));
    signal().throwIfAborted(); setView(result);
  }
  async function prepareReset(paths: string[]) { const result = value(await services.changes.prepareReset(revision, itemKey, view!.snapshotId!, paths, signal())); signal().throwIfAborted(); setReset(result); }
  async function execute() {
    const saved = execution.current; if (!saved) return;
    await requests.submit(activeKey.current, saved.request.planId, saved.request.hostInstanceId, async input => value(await (saved.kind === 'reset' ? services.changes.reset(revision, itemKey, { ...input, hostInstanceId: saved.request.hostInstanceId }, signal()) : services.executePlan(input, signal()))));
    const admitted = requests.get(activeKey.current);
    const result = admitted?.operation ?? (admitted?.operationId ? value(await services.operation(admitted.operationId, admitted.hostInstanceId)) : null);
    if (!result) throw new Error('操作未确认，请查询原请求。'); signal().throwIfAborted(); setOperation(result); setReset(undefined); setResetting(saved.kind === 'reset');
  }
  useEffect(() => {
    if (!operation || ['completed', 'failed', 'unknown', 'recovery-required'].includes(operation.phase)) return;
    let disposed = false;
    const timer = setTimeout(() => { void services.operation(operation.operationId, operation.context.hostInstanceId).then(response => { if (disposed) return; if (!response.ok || !response.value) throw new Error('Operation unavailable'); setOperation(response.value); }).catch(() => { if (!disposed) setError('进度暂未确认，请重试原请求。'); }); }, 800);
    return () => { disposed = true; clearTimeout(timer); };
  }, [operation, services]);
  useEffect(() => { if (operation?.phase === 'completed' && resetting) { requests.closeResult(activeKey.current, operation); setOperation(undefined); execution.current = undefined; setResetting(false); void load().catch(failure => setError(failure instanceof Error ? failure.message : '重新读取失败。')); } }, [operation?.phase, resetting]);
  return <section className="astron-sh-changes" aria-label={upgrade ? '升级变化预览' : '本地修改'}><ChangePreviewStyles />
    <HostWriteStatus state={writable} /><Card className="changes-panel"><CardHeader className="changes-heading"><h3>{view?.kind === 'version' ? '版本变化' : upgrade ? '处理本地修改' : '本地修改'}</h3>{view?.status === 'ready' && <p className="changes-context">{view.kind === 'local' ? `v${view.version} 原始发布内容 → 当前本地文件` : `v${view.version} 原始发布内容 → v${view.candidateVersion} 发布内容`}</p>}</CardHeader><CardContent className="changes-content">
    {error && <p role="alert">{error}<Button size="sm" onClick={() => run(operation ? async () => { const current = value(await services.operation(operation.operationId, operation.context.hostInstanceId)); if (!current) throw new Error('原操作暂不可读，请重试。'); setOperation({ ...current }); } : execution.current ? execute : load)} disabled={pending}>{execution.current ? '查询原请求' : '重新检查'}</Button></p>}
    {pending && <p role="status">{execution.current ? '正在提交…' : '正在检查并准备内容…'}</p>}
    {operation ? <><p role="status">{operation.phase === 'completed' ? '升级完成。' : operation.phase === 'failed' ? '操作未完成，请核验安装状态。' : ['unknown', 'recovery-required'].includes(operation.phase) ? '操作需要恢复核验，请勿重复提交。' : '正在执行…'}</p>{['unknown', 'recovery-required'].includes(operation.phase) && <Button disabled={pending || !writable.canWrite} onClick={() => run(async () => { const result = value(await services.recoverOperation({ operationId: operation.operationId, hostInstanceId: operation.context.hostInstanceId })); if (result) setOperation(result); })}>核验并恢复</Button>}<Button onClick={() => { if (operation.phase === 'completed' || (operation.phase === 'failed' && ['restored', 'not-needed'].includes(operation.recovery))) requests.closeResult(activeKey.current, operation); if (operation.phase === 'completed') onCompleted?.(operation.version); onClose(); }}>关闭</Button></> : <>
      {view?.status === 'blocked' && <p role="alert">当前无法比较或操作：{view.reason}</p>}
      {view?.status === 'ready' && <>
        {view.kind === 'version' && view.changelog && <div className="changes-release-note"><strong>更新说明</strong><p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{view.changelog}</p></div>}
        {view.backupSaved && <div className="changes-backup" role="status"><div>本地修改已备份{view.backupLocation && <code>{view.backupLocation}</code>}</div>{view.backupLocation && <Button size="sm" variant="ghost" onClick={() => { void navigator.clipboard.writeText(view.backupLocation!).catch(() => setError('复制失败，可选择上方路径复制。')); }}>复制路径</Button>}</div>}
        {view.changes.length ? <><div className="changes-section-heading"><span>{view.changes.length} 个文件变化</span>{view.kind === 'local' && <Button size="sm" variant="ghost" disabled={pending || !!reset || !writable.canWrite} onClick={event => { resetTrigger.current = event.currentTarget; run(() => prepareReset(view.changes.map(file => file.path))); }}>撤销全部修改</Button>}</div>
          <div className="changes-list">{view.changes.map(file => <FileChange key={`${view.snapshotId}:${file.path}`} file={file} view={view} revision={revision} itemKey={itemKey} services={services.changes}
            disabled={pending || !!reset} onUndo={view.kind === 'local' && writable.canWrite ? trigger => { resetTrigger.current = trigger; run(() => prepareReset([file.path])); } : undefined} />)}</div></> : <p role="status">{view.kind === 'local' ? '没有本地修改。' : '两个发布版本的文件内容相同。'}</p>}
      </>}
      {reset && <div className="changes-confirm" onKeyDown={event => { if (event.key === 'Escape' && !pending) { event.preventDefault(); closeReset(); } }}><strong>{reset.paths.length === 1 ? `撤销 ${reset.paths[0]!.path} 的修改？` : `撤销这 ${reset.paths.length} 个文件的修改？`}</strong><p>{reset.paths.length === 1 && reset.paths[0]!.kind === 'added' ? '将删除这个本地新增文件。' : `将恢复所选发布文件的原始内容${reset.paths.some(file => file.kind === 'added') ? `，并删除 ${reset.paths.filter(file => file.kind === 'added').length} 个本地新增文件` : ''}，版本保持不变。`}</p><ul>{reset.paths.slice(0, 5).map(file => <li key={file.path}>{file.kind === 'added' ? '删除本地新增文件：' : '恢复原始内容：'}<code>{file.path}</code></li>)}</ul>{reset.paths.length > 5 && <p>另有 {reset.paths.length - 5} 个文件。</p>}<Button ref={resetCancel} disabled={pending} onClick={closeReset}>取消</Button> <Button disabled={pending} onClick={() => run(async () => { if (!writable.canWrite) throw new Error('当前宿主不可写。'); activeKey.current = requestKeys.reset; execution.current = { kind: 'reset', request: { planId: reset.planId, hostInstanceId: reset.hostInstanceId, requestKey: crypto.randomUUID() } }; await execute(); })}>确认撤销</Button></div>}
      <div className="changes-footer"><Button disabled={pending} onClick={onClose}>{upgrade && view?.kind === 'local' ? '暂不升级' : '返回详情'}</Button>
        {upgrade && view?.status === 'ready' && !reset && <Button variant="default" disabled={pending || !writable.canWrite} onClick={() => run(async () => {
          if (!writable.canWrite) throw new Error('当前宿主不可写。');
          if (view.kind === 'local') { await preview(view, view.changes.length > 0); return; }
          const plan = value(await services.changes.confirm(revision, itemKey, view.snapshotId!, signal()));
          if (plan.status !== 'ready' || !plan.planId) throw new Error('升级计划已变化，请重新检查。');
          activeKey.current = requestKeys.upgrade; execution.current = { kind: 'upgrade', request: { planId: plan.planId, hostInstanceId: plan.hostInstanceId, requestKey: crypto.randomUUID() } }; await execute();
        })}>{view.kind === 'version' ? '确认升级' : view.changes.length ? '备份修改并继续' : '继续'}</Button>}
      </div>
    </>}</CardContent></Card>
  </section>;
}
