import { useQuery } from '@tanstack/react-query';
import { exactInstalledSkill, UseSkillButton, type SkillUseServices } from './use-skill.js';
import { useEffect, useRef, useState } from 'react';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { InstalledSkillList, NativeOperation } from '../host/types.js';
import { Button, Dialog, DialogContent, DialogTitle, DialogDescription, SelectField } from './ui.js';
import { useInstallationOperation } from './use-installation-operation.js';

export const MAX_TRANSFER_BYTES = 8 * 1024 * 1024;
export interface PackagePreview { previewId: string; name: string; summary?: string; files: number; bytes: number; expiresAt: string }
export interface PackageTransferServices {
  preview(revision: string, base64: string, signal: AbortSignal): Promise<RemoteResult<PackagePreview>>;
  confirm(revision: string, preview: string, scope: 'user' | 'project', requestKey: string, signal: AbortSignal): Promise<RemoteResult<NativeOperation | null>>;
}
export function encodePackage(bytes: Uint8Array): string {
  if (!bytes.length || bytes.length > MAX_TRANSFER_BYTES) throw new Error('请选择不超过 8 MB 的 ZIP 技能包。');
  let text = '';
  for (let start = 0; start < bytes.length; start += 16384) text += String.fromCharCode(...bytes.subarray(start, start + 16384));
  return btoa(text);
}
export function packageDownload(base64: string, filename: string): void {
  const text = atob(base64);
  if (!text.length || text.length > MAX_TRANSFER_BYTES) throw new Error('导出文件超过传输限制。');
  const bytes = Uint8Array.from(text, char => char.charCodeAt(0));
  const url = URL.createObjectURL(new Blob([bytes], { type: 'application/zip' }));
  const link = document.createElement('a');
  link.href = url; link.download = filename.replace(/[\\/]/g, '_');
  document.body.append(link); link.click(); link.remove();
  // Give the browser time to begin consuming the download before releasing it.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function PackagePreviewContent({ preview }: { preview: PackagePreview }) {
  return <section className="astron-sh-package-preview" aria-label="技能包预览">
    <strong className="astron-sh-package-name">{preview.name}</strong>
    {preview.summary && <div className="astron-sh-package-summary" role="region" aria-label="技能说明" tabIndex={0}>{preview.summary}</div>}
    <p className="astron-sh-package-count">{preview.files} 个文件 · {(preview.bytes / 1024).toFixed(1)} KB</p>
  </section>;
}
export function importProgressView(operation: Pick<NativeOperation, 'phase' | 'warningCount' | 'recovery' | 'resultDetails'> | undefined, confirming: boolean, readFailed: boolean) {
  if (readFailed) return { label: '导入状态待核验', state: 'stopped' as const };
  if (!operation) return { label: confirming ? '正在确认导入' : '等待确认导入', state: confirming ? 'running' as const : 'ready' as const };
  if (operation.phase === 'completed') {
    if (['incomplete', 'unknown'].includes(operation.recovery ?? '')) return { label: '导入结果待核验', state: 'stopped' as const };
    const warning = operation.warningCount > 0 || !!operation.resultDetails?.warnings.length;
    return { label: warning ? '导入完成，需核验告警' : '导入成功', state: 'complete' as const };
  }
  if (['failed', 'unknown', 'recovery-required'].includes(operation.phase)) return { label: operation.phase === 'failed' ? '导入未完成' : '导入状态待核验', state: 'stopped' as const };
  const labels = { accepted: '准备导入', staging: '正在校验技能包', revalidating: '正在核验安装位置', committing: '正在写入技能', recovering: '正在恢复核验' };
  return { label: labels[operation.phase as keyof typeof labels] ?? '正在导入', state: 'running' as const };
}
export function PackageImport({ transfer, revision, contextKey, services, onCompleted }: {
  transfer: PackageTransferServices; revision: string; contextKey: string;
  services: { sessionId: string; skillUse?: SkillUseServices; list?(revision: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillList>>; operation(id: string, host: string): Promise<RemoteResult<NativeOperation | null>> }; onCompleted(): void;
}) {
  const [container, setContainer] = useState<HTMLSpanElement | null>(null);
  const [open, setOpen] = useState(false);
  const [preview, setPreview] = useState<PackagePreview>();
  const [scope, setScope] = useState<'user' | 'project'>('project');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [accepted, setAccepted] = useState<NativeOperation>();
  const request = useRef<AbortController>();
  const requestKey = useRef('');
  const confirming = useRef(false);
  const file = useRef<HTMLInputElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const receipt = useInstallationOperation(services, accepted?.context.hostInstanceId ?? '', accepted?.operationId);
  const operation = receipt.data ?? accepted;
  const progress = importProgressView(operation, busy && !!preview, receipt.isError || receipt.data === null);
  const installed = useQuery({ queryKey: ['skillhub-import-result', contextKey, operation?.operationId],
    enabled: progress.state === 'complete' && !!services.list, retry: false,
    queryFn: async ({ signal }) => {
      const response = await services.list!(revision, signal);
      signal.throwIfAborted();
      if (!response.ok || operation?.context.sourceIdentity !== 'local:import' || operation.namespace !== 'local' || operation.slug !== preview?.name) throw new Error('无法核验导入记录。');
      const match = exactInstalledSkill(response.value, { registry: 'local:import', namespace: 'local', slug: operation.slug, version: 'local', scope });
      return match?.sourceKind === 'local-import' ? match : null;
    } });
  useEffect(() => () => request.current?.abort(), [contextKey]);
  useEffect(() => {
    request.current?.abort(); setOpen(false); setPreview(undefined); setAccepted(undefined); setBusy(false); setError('');
  }, [contextKey]);
  useEffect(() => { if (receipt.data?.phase === 'completed') onCompleted(); }, [receipt.data?.phase]);
  async function choose(selected: File) {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setOpen(true); setBusy(true); setError(''); setPreview(undefined); setAccepted(undefined); setScope('project');
    try {
      if (!selected.name.toLowerCase().endsWith('.zip') || selected.size > MAX_TRANSFER_BYTES) throw new Error('请选择不超过 8 MB 的 ZIP 技能包。');
      const bytes = new Uint8Array(await selected.arrayBuffer()); controller.signal.throwIfAborted();
      const response = await transfer.preview(revision, encodePackage(bytes), controller.signal);
      controller.signal.throwIfAborted();
      if (!response.ok) throw new Error('无法读取技能包，请检查包格式或重新选择。');
      setPreview(response.value); requestKey.current = crypto.randomUUID();
    } catch (failure) { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : '无法读取技能包。'); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }
  async function confirm() {
    if (!preview || busy || accepted || confirming.current) return;
    confirming.current = true;
    const controller = new AbortController(); request.current = controller; setBusy(true); setError('');
    try {
      const response = await transfer.confirm(revision, preview.previewId, scope, requestKey.current, controller.signal);
      controller.signal.throwIfAborted();
      if (!response.ok || !response.value) throw new Error('未能确认导入，请检查安装位置是否已有同名技能或预览是否过期。');
      setAccepted(response.value);
    } catch (failure) { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : '导入未确认。'); }
    finally { confirming.current = false; if (!controller.signal.aborted) setBusy(false); }
  }
  return <span ref={setContainer}>
    <input ref={file} type="file" accept=".zip,application/zip" aria-label="选择技能 ZIP" hidden onChange={event => { const selected = event.target.files?.[0]; event.target.value = ''; if (selected) void choose(selected); }} />
    <Button ref={trigger} disabled={!!accepted && !['completed', 'failed', 'recovery-required', 'unknown'].includes(operation?.phase ?? '')} onClick={() => file.current?.click()}>导入技能包</Button>
    <Dialog open={open} onOpenChange={next => { if (!next) { request.current?.abort(); setBusy(false); } setOpen(next); }}><DialogContent container={container} className="astron-sh-dialog astron-sh-package-dialog" onCloseAutoFocus={event => {
      event.preventDefault();
      if (trigger.current && !trigger.current.disabled) trigger.current.focus();
      else trigger.current?.closest('.skillhub-installed-toolbar')?.querySelector<HTMLInputElement>('input[type=search]')?.focus();
    }}>
      <DialogTitle>导入技能包</DialogTitle><DialogDescription>安装到本机，不上传到技能市场。</DialogDescription>
      <div className="astron-sh-package-body">
      {preview && <PackagePreviewContent preview={preview} />}
      {busy && !preview && <p role="status">正在检查技能包…</p>}
      {(error || receipt.isError) && <p role="alert">{error || '暂时无法查询导入状态，请关闭后刷新已安装。'}</p>}
      </div>
      {preview && <div className="astron-sh-package-scope"><SelectField label="安装范围" value={scope} onValueChange={value => setScope(value as 'user' | 'project')} disabled={busy || !!accepted} options={[{ value: 'project', label: '仅此项目' }, { value: 'user', label: '所有项目' }]} /></div>}
      {preview && <section className="astron-sh-package-result" aria-label="导入结果" data-state={progress.state}>
        <strong role={progress.state === 'stopped' ? 'alert' : 'status'}>{progress.label}</strong>
        {progress.state !== 'ready' && <div className="astron-sh-install-progress-track" data-state={progress.state} role="progressbar" aria-label="导入进度" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress.state === 'complete' ? 100 : undefined} aria-valuetext={progress.label}><span /></div>}
        <div className="astron-sh-package-result-action">{progress.state === 'stopped' && <p>请在已安装中核对当前状态。</p>}{progress.state === 'complete' && (installed.isError || installed.data === null) && <p>安装记录暂未确认，请在已安装中核对。</p>}{progress.state === 'complete' && installed.data && services.skillUse && <UseSkillButton services={services.skillUse} revision={revision} label="使用技能" resolve={async signal => {
          const inventory = await services.list!(revision, signal);
          if (!inventory.ok) throw new Error('无法核验导入记录，请刷新已安装。');
          const exact = exactInstalledSkill(inventory.value, { registry: 'local:import', namespace: 'local', slug: operation!.slug, version: 'local', scope });
          if (exact?.sourceKind !== 'local-import') throw new Error('导入记录已变化，请刷新已安装。');
          return exact.itemKey;
        }} onUsed={() => setOpen(false)} onHandoff={() => setOpen(false)} onError={setError} />}</div>
      </section>}
      <div className="astron-sh-dialog-actions"><Button onClick={() => { request.current?.abort(); setBusy(false); setOpen(false); }}>关闭</Button>{preview && !accepted && <Button variant="default" disabled={busy} onClick={() => { void confirm(); }}>确认导入</Button>}</div>
    </DialogContent></Dialog>
  </span>;
}
