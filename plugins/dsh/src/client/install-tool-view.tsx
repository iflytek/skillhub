import { OperationDetails } from './operation-details.js';
import { NativeControlStyles } from './native-control-styles.js';
import { DiscoveryStatus } from './discovery-status.js';
import type { ToolCallViewProps } from '@deepseek-ai/dsh-client-ui-tool/client';
import { useState } from 'react';
import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';
import type { InstallationServices } from './install-control.js';
import { parseInstallResult, parseInstallFailure } from './install-result.js';
import type { InstallFailure } from './install-result.js';
import type { InstallResult } from './install-result.js';
import { useInstallationOperation } from './use-installation-operation.js';

export interface InstallViewServices {
  client: QueryClient;
  installation: Pick<InstallationServices, 'sessionId' | 'operation' | 'installed'>;
  openInstalled(result: InstallResult): Promise<void>;
}

export class CatalogUnavailableError extends Error {}

const failureMessages: Record<InstallFailure['code'], string> = {
  CONTEXT_STALE: '原技能选择或会话上下文已失效，请在当前会话重新查看并选择技能。',
  REQUEST_CONFLICT: '同一安装请求的参数发生变化，请先核对原操作，不要重复安装。',
  SOURCE_NOT_CONFIGURED: '尚未配置 SkillHub 来源，请在插件设置中填写地址。',
  SOURCE_DENIED: '当前来源不允许匿名访问此技能，请确认该技能是否公开。',
  SKILL_UNAVAILABLE: '指定技能或版本已不可用，请重新查看技能详情。',
  RATE_LIMITED: 'SkillHub 来源暂时限流，请稍后重新核验原请求。',
  SOURCE_UNAVAILABLE: '暂时无法读取 SkillHub 来源，请检查连接后重新核验原请求。',
  TARGET_UNAVAILABLE: '指定安装范围不可用，请检查当前会话的项目和 DSH 设置。',
  UNCONFIRMED: '尚未确认安装请求结果，请先核对原操作，不要直接重复安装。',
};

export function InstallToolView(props: ToolCallViewProps & { services: InstallViewServices }) {
  return <div className="skillhub-controls" style={{ minWidth: 0, overflowWrap: 'anywhere', font: 'inherit' }}>
    <NativeControlStyles />
    <InstallToolViewContent {...props} />
  </div>;
}

function InstallToolViewContent(props: ToolCallViewProps & { services: InstallViewServices }) {
  if (props.phase === 'preparing') return <p role="status">正在准备 SkillHub 安装请求…</p>;
  if (props.phase === 'start') return <p role="status">正在核验技能与安装位置…</p>;
  if (props.block.isError) return <section aria-label="SkillHub 安装请求未确认">
    <p role="alert">本次安装请求未确认。请检查原操作或重新查看技能详情，不要直接重复安装。</p>
    {props.inspect && <button onClick={props.inspect}>查看调用详情</button>}
  </section>;
  const failure = parseInstallFailure(props.block.meta);
  if (failure) return <section aria-label="SkillHub 安装请求未确认">
    <p role="alert">{failureMessages[failure.code]}</p>
    {props.inspect && <button onClick={props.inspect}>查看调用详情</button>}
  </section>;
  const result = parseInstallResult(props.block.meta);
  if (!result) return <section aria-label="SkillHub 安装记录">
    <p>这条记录的格式无法识别，不能核验安装结果。</p>
    {props.inspect && <button onClick={props.inspect}>查看调用详情</button>}
  </section>;
  return <QueryClientProvider client={props.services.client}>
    <InstallationResult result={result} services={props.services.installation} openInstalled={props.services.openInstalled} />
  </QueryClientProvider>;
}

function InstallationResult({ result, services, openInstalled }: {
  result: InstallResult; services: InstallViewServices['installation']; openInstalled: InstallViewServices['openInstalled'];
}) {
  const [actionStatus, setActionStatus] = useState('');
  const [opening, setOpening] = useState(false);
  const query = useInstallationOperation(services, result.hostInstanceId, result.operationId);
  const operation = query.data;
  const mismatch = operation && (operation.operationId !== result.operationId || operation.kind !== 'install'
    || operation.context.hostInstanceId !== result.hostInstanceId || operation.namespace !== result.namespace
    || operation.slug !== result.slug || operation.version !== result.version
    || operation.context.sourceIdentity !== result.sourceLabel || operation.context.sourceRevision !== result.sourceRevision
    || operation.targetLabel !== result.targetLabel);
  const canInspectCurrent = !query.isError && !mismatch && (operation?.phase === 'completed'
    || (operation?.phase === 'failed' && ['not-needed', 'restored'].includes(operation.recovery)));
  const current = useQuery({ queryKey: ['skillhub-installed', services.sessionId, result.sourceRevision], retry: false,
    enabled: canInspectCurrent,
    queryFn: async ({ signal }) => {
      const response = await services.installed(result.sourceRevision, signal);
      if (!response.ok) throw new Error('当前安装记录无法核验，来源或会话可能已变化。');
      return response.value;
    } });
  const entries = current.data?.items.filter(item => item.namespace === result.namespace && item.slug === result.slug) ?? [];
  return <section aria-label="SkillHub 安装结果" style={{ border: '1px solid #8885', borderRadius: 12, padding: 16, overflowWrap: 'anywhere' }}>
    <h3>SkillHub · {result.slug}</h3>
    <p>@{result.namespace}/{result.slug} · {result.version}</p>
    <p>{result.sourceLabel} · {result.hostLabel} · {result.targetLabel}</p>
    <p>原请求已接受于 <time dateTime={result.createdAt}>{result.createdAt}</time></p>
    {query.isError || operation === null || mismatch ? <p role="alert">原操作结果无法核验，请勿重复安装。</p>
      : !operation ? <p role="status">正在核验原操作状态…</p>
        : operation.phase === 'completed' ? <>
          <p>原操作结果：</p>
          <p>{operation.files === 'already-installed' ? '此版本已安装，无需重复安装。' : '技能文件安装完成。'}</p>
          <DiscoveryStatus confirmed={operation.discovery === 'confirmed'} checkedAt={query.dataUpdatedAt}
            refreshing={query.isFetching} refresh={() => { void query.refetch(); }} />
          <p>以上结果属于原操作，不代表当前仍安装此版本。</p>
        </> : operation.phase === 'failed' ? <>
          <p role="alert">本次安装未完成。{operation.recovery === 'not-needed' ? '无需恢复文件。'
            : operation.recovery === 'restored' ? '原文件已恢复。' : '请检查操作记录和恢复状态。'}</p>
          {canInspectCurrent && <p>本次请求失败不代表技能未安装，请以下方当前安装记录为准。</p>}
        </>
          : ['unknown', 'recovery-required'].includes(operation.phase) ? <p role="alert">操作需要恢复核验，请勿重复安装。</p>
            : <p role="status">正在安装，请稍候…</p>}
    <button disabled={query.isFetching} onClick={() => { void query.refetch(); }}>查询原操作</button>
    {canInspectCurrent && <section aria-label="当前安装记录">
      <h4>当前会话可见的安装记录</h4>
      {current.isPending || current.isFetching ? <p role="status">正在核验当前安装记录…</p>
        : current.isError ? <p role="alert">{current.error.message}</p>
          : <>
            {!current.data.complete && <p role="alert">当前安装记录读取不完整，不能判断所有范围是否仍安装。</p>}
            {entries.length ? <ul>{entries.map(item => <li key={item.itemKey}>
              {item.scope === 'project' ? '当前项目' : '当前用户'} · {item.version} · {item.state === 'present' ? '文件存在' : '本地状态异常，请前往已安装核验'}
            </li>)}</ul> : current.data.complete ? <p>当前会话可见范围内已无此技能的安装记录。</p> : null}
          </>}
      <button disabled={current.isFetching} onClick={() => { void current.refetch(); }}>核验当前安装状态</button>
    </section>}
    {canInspectCurrent && <button disabled={opening} onClick={() => {
      setOpening(true); setActionStatus('');
      void openInstalled(result).catch(error => setActionStatus(error instanceof CatalogUnavailableError
        ? 'SkillHub 广场入口暂不可用，请在此卡片查询原操作。'
        : '会话或来源已变化，请从 SkillHub 入口打开当前已安装列表。'))
        .finally(() => setOpening(false));
    }}>在已安装中查看</button>}
    {!query.isError && !mismatch && operation && <OperationDetails operation={operation} />}
    <details><summary>操作信息</summary><p>操作 ID：<code>{result.operationId}</code></p>
      <button onClick={() => {
        if (!navigator.clipboard?.writeText) { setActionStatus('无法访问剪贴板，请手动选择操作 ID 复制。'); return; }
        void navigator.clipboard.writeText(result.operationId).then(() => setActionStatus('操作 ID 已复制。'),
          () => setActionStatus('复制失败，请手动选择操作 ID 复制。'));
      }}>复制操作 ID</button>
    </details>
    {actionStatus && <p role="status">{actionStatus}</p>}
  </section>;
}
