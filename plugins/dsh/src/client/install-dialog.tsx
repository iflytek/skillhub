import { useRef, useState } from 'react';
import { QueryClientProvider, useQuery, type QueryClient } from '@tanstack/react-query';
import type { SkillDetail, HostInstallRequest, InstalledSkillList } from '../host/types.js';
import { InstallationProgressPanel, type InstallationProgressJob } from './installation-progress.js';
import { InstallControl, type InstallationServices } from './install-control.js';
import { Dialog, DialogContent, DialogDescription, DialogTitle, Button } from './ui.js';
import { NativeControlStyles } from './native-control-styles.js';
import { SkillAvatar } from './skill-card.js';

/** A fresh, identity-bound read for confirmation; no navigation or write on opening. */
export function SkillInstallDialog({ client, identity, load, services, initiatedFrom, close, onManage }: {
  client: QueryClient; identity: string; load(signal: AbortSignal): Promise<{ detail: SkillDetail; sourceRevision: string }>;
  services: InstallationServices; initiatedFrom: HostInstallRequest['initiatedFrom']; close(): void; onManage?(item: InstalledSkillList['items'][number]): void;
}) {
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const opener = useRef(typeof document === 'undefined' ? null : document.activeElement);
  return <div ref={setContainer} className="skillhub-controls"><NativeControlStyles />{container && <Dialog open onOpenChange={open => { if (!open) close(); }}>
    <DialogContent className="astron-sh-dialog astron-sh-dialog-expanded" container={container} onCloseAutoFocus={event => { event.preventDefault(); if (opener.current instanceof HTMLElement && opener.current.isConnected) opener.current.focus(); }}>
      <DialogTitle>安装技能</DialogTitle><DialogDescription>选择技能的可用范围</DialogDescription>
      <QueryClientProvider client={client}><Content identity={identity} load={load} services={services} initiatedFrom={initiatedFrom} close={close} onManage={onManage} /></QueryClientProvider>
    </DialogContent>
  </Dialog>}</div>;
}
function Content({ identity, load, services, initiatedFrom, close, onManage }: {
  identity: string; load(signal: AbortSignal): Promise<{ detail: SkillDetail; sourceRevision: string }>;
  services: InstallationServices; initiatedFrom: HostInstallRequest['initiatedFrom']; close(): void; onManage?(item: InstalledSkillList['items'][number]): void;
}) {
  const [job, setJob] = useState<InstallationProgressJob | null>(null);
  const result = useQuery({ queryKey: ['skillhub-install-confirmation', services.sessionId, identity], gcTime: 0, retry: false,
    refetchOnWindowFocus: false, refetchOnReconnect: false, queryFn: ({ signal }) => load(signal) });
  if (result.isPending) return <><p role="status">正在核验技能版本与安装位置…</p><Button onClick={close}>取消</Button></>;
  if (result.isError) return <><p role="alert">无法读取安装信息，请重新选择技能或重试。</p><div className="astron-sh-dialog-actions"><Button onClick={close}>取消</Button><Button onClick={() => { void result.refetch(); }}>重试</Button></div></>;
  return <div className="astron-sh-install-dialog-layout"><div>
    <div className="astron-sh-install-dialog-identity"><SkillAvatar name={result.data.detail.title} namespace={result.data.detail.namespace} slug={result.data.detail.slug} />
      <div><strong>{result.data.detail.title}</strong><p>@{result.data.detail.namespace}/{result.data.detail.slug} · {result.data.detail.version}</p></div></div>
    {job ? <><dl className="astron-sh-install-confirmed-scope"><dt>安装范围</dt><dd>{job.scope === 'project' ? '仅此项目' : '所有项目'}</dd></dl>
      <p className="astron-sh-install-close-note">关闭窗口不会取消已提交的安装。</p><Button autoFocus onClick={close}>关闭</Button></>
      : <InstallControl key={result.data.sourceRevision} detail={result.data.detail} sourceRevision={result.data.sourceRevision} services={services} initiatedFrom={initiatedFrom}
        presentation="dialog" onClose={close} onManage={onManage} onSubmitted={value => { setJob(value); }} onContextStale={() => { void result.refetch(); }} />}
    </div><aside aria-label="安装进度">{job ? <InstallationProgressPanel job={job} services={services} onUsed={close} /> : <section className="astron-sh-install-progress-item"><header><SkillAvatar name={result.data.detail.title} namespace={result.data.detail.namespace} slug={result.data.detail.slug} /><div><strong>{result.data.detail.title}</strong><p>v{result.data.detail.version}</p></div></header><div className="astron-sh-install-progress"><strong>等待确认安装</strong><p>确认范围后，安装进度将显示在这里。</p></div></section>}</aside>
  </div>;
}
