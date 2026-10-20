import { useSyncExternalStore } from 'react';
import type { NativeOperation } from '../host/types.js';
import type { InstallationAttempt } from './installation-requests.js';
import type { InstallationServices } from './install-control.js';
import { useInstallationOperation } from './use-installation-operation.js';
import { Button } from './ui.js';
import { SkillAvatar } from './skill-card.js';

export interface InstallationProgressJob {
  key: string; sessionId: string; sourceRevision: string; hostInstanceId: string;
  namespace: string; slug: string; version: string; title: string; scope: 'user' | 'project';
}

export function installationProgressState(attempt: InstallationAttempt | undefined, operation: NativeOperation | null | undefined, readFailed: boolean) {
  if (attempt?.state === 'submitting') return { label: '正在提交安装', message: '请稍候…', state: 'running' as const };
  if (!attempt || attempt.state === 'unresolved' || readFailed || operation === null) {
    return { label: '安装状态待核验', message: '尚不能确认结果，请核验原请求，不要重复安装。', state: 'stopped' as const };
  }
  if (!operation) return { label: '正在提交安装', message: '请稍候…', state: 'running' as const };
  if (operation.phase === 'completed') {
    const warning = operation.warningCount > 0 || !!operation.resultDetails?.warnings.length;
    return { label: warning ? '安装完成，需核验告警' : '安装完成',
      message: warning ? '请在已安装中核验当前状态。' : operation.discovery === 'confirmed' ? '技能已就绪。' : '文件已安装，DSH 发现尚待确认。', state: 'complete' as const };
  }
  if (operation.phase === 'failed') return { label: '安装未完成', message: '请核验当前状态后再处理。', state: 'stopped' as const };
  if (operation.phase === 'recovery-required' || operation.phase === 'unknown') return { label: '安装结果待核验', message: '操作需要恢复核验，请在已安装中处理。', state: 'stopped' as const };
  const labels = { accepted: '准备安装', staging: '正在下载并校验', revalidating: '正在核验安装位置', committing: '正在写入技能', recovering: '正在恢复核验' };
  return { label: labels[operation.phase], message: '正在处理，请稍候。', state: 'running' as const };
}

/** Indeterminate until a verified completion; phase names are not percentages. */
export function InstallationProgressSummary({ attempt, operation, readFailed, onCheck }: {
  attempt: InstallationAttempt | undefined; operation: NativeOperation | null | undefined; readFailed: boolean; onCheck?(): void;
}) {
  const view = installationProgressState(attempt, operation, readFailed);
  return <div className="astron-sh-install-progress" data-state={view.state}>
    <strong role={view.state === 'stopped' ? 'alert' : 'status'}>{view.label}</strong>
    <div role="progressbar" aria-label="安装进度" aria-valuemin={0} aria-valuemax={100} aria-valuenow={view.state === 'complete' ? 100 : undefined} aria-valuetext={view.label}
      className="astron-sh-install-progress-track" data-state={view.state}><span /></div>
    <p>{view.message}</p>
    {onCheck && view.state !== 'running' && (view.state !== 'complete' || operation?.discovery !== 'confirmed' || !!operation.warningCount)
      && <Button size="sm" variant="ghost" onClick={onCheck}>核验状态</Button>}
  </div>;
}

export function InstallationProgressPanel({ job, services }: { job: InstallationProgressJob; services: InstallationServices }) {
  const attempt = useSyncExternalStore(services.requests.subscribe, () => services.requests.get(job.key), () => undefined);
  const operation = useInstallationOperation(services, job.hostInstanceId, attempt?.operationId ?? attempt?.operation?.operationId);
  async function check() {
    if (!attempt) return;
    if (attempt.state === 'unresolved') await services.requests.submit(job.key, attempt.request, async input => {
      const response = await services.start(input, new AbortController().signal);
      if (!response.ok) throw new Error('暂不能确认原请求。');
      return response.value;
    });
    else await operation.refetch();
  }
  return <section className="astron-sh-install-progress-item" aria-label={`${job.title} 安装进度`}>
    <header><SkillAvatar name={job.title} namespace={job.namespace} slug={job.slug} /><div><strong>{job.title}</strong><p>v{job.version} · {job.scope === 'project' ? '仅此项目' : '所有项目'}</p></div>
    </header>
    <InstallationProgressSummary attempt={attempt} operation={operation.data ?? attempt?.operation} readFailed={operation.isError || operation.data === null} onCheck={() => { void check(); }} />
  </section>;
}
