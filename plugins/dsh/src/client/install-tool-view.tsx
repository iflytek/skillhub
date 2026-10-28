import { exactInstalledSkill, UseSkillButton } from './use-skill.js';
import { matchesReceipt, resolveReceiptContext, readReceiptInventory, type ReceiptReads } from './receipt-context.js';
import { receiptInventoryIssue, receiptPresentation } from './install-receipt-presentation.js';
import { Button } from './ui.js';
import { ConversationStyles } from './conversation-styles.js';
import { SkillAvatar } from './skill-card.js';
import { readableDate } from './skill-presentation.js';
import { OperationDetails } from './operation-details.js';
import { NativeControlStyles } from './native-control-styles.js';
import type { ToolCallViewProps } from '@deepseek-ai/dsh-client-ui-tool/client';
import { useState } from 'react';
import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';
import type { InstallationServices } from './install-control.js';
import { parseInstallResult, parseInstallFailure, parsePendingInstallResult, isCancelledInstallResult } from './install-result.js';
import type { InstallFailure } from './install-result.js';
import type { InstallResult, PendingInstallResult } from './install-result.js';
import { SkillInstallDialog } from './install-dialog.js';
import type { SearchViewServices } from './conversation-detail.js';
import { useInstallationOperation } from './use-installation-operation.js';

export interface InstallViewServices {
  client: QueryClient;
  receipt: ReceiptReads;
  confirmation?: SearchViewServices;
  installation: Pick<InstallationServices, 'sessionId' | 'operation' | 'installed' | 'skillUse'>;
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
    <NativeControlStyles /><ConversationStyles />
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
  if (isCancelledInstallResult(props.block.meta)) return <p role="status">安装未提交，未写入技能文件。</p>;
  const failure = parseInstallFailure(props.block.meta);
  if (failure) return <section aria-label="SkillHub 安装请求未确认">
    <p role="alert">{failureMessages[failure.code]}</p>
    {props.inspect && <button onClick={props.inspect}>查看调用详情</button>}
  </section>;
  const pending = parsePendingInstallResult(props.block.meta);
  if (pending) return <PendingInstallation result={pending} services={props.services.confirmation} />;
  const result = parseInstallResult(props.block.meta);
  if (!result) return <section aria-label="SkillHub 安装记录">
    <p>这条记录的格式无法识别，不能核验安装结果。</p>
    {props.inspect && <button onClick={props.inspect}>查看调用详情</button>}
  </section>;
  return <QueryClientProvider client={props.services.client}>
    <InstallationResult result={result} services={props.services.installation} receipt={props.services.receipt} openInstalled={props.services.openInstalled} />
  </QueryClientProvider>;
}

function InstallationResult({ result, services, receipt, openInstalled }: {
  result: InstallResult; services: InstallViewServices['installation']; receipt: ReceiptReads; openInstalled: InstallViewServices['openInstalled'];
}) {
  const [actionStatus, setActionStatus] = useState('');
  const [opening, setOpening] = useState(false);
  const query = useInstallationOperation(services, result.hostInstanceId, result.operationId);
  const operation = query.data;
  const mismatch = !!operation && !matchesReceipt(operation, result);
  const canInspectCurrent = !query.isError && !mismatch && (operation?.phase === 'completed'
    || (operation?.phase === 'failed' && ['not-needed', 'restored'].includes(operation.recovery)));
  const context = useQuery({ queryKey: ['skillhub-receipt-context', services.sessionId, result.hostInstanceId,
    result.operationId, result.sourceLabel, result.sourceRevision, receipt.sessionGeneration], retry: false, enabled: canInspectCurrent,
    queryFn: ({ signal }) => resolveReceiptContext(receipt, result, signal) });
  const current = useQuery({ queryKey: ['skillhub-installed', services.sessionId, context.data?.sourceRevision,
    'receipt', result.hostInstanceId, result.operationId, result.sourceLabel, receipt.sessionGeneration], retry: false,
    enabled: canInspectCurrent && !!context.data && !context.isFetching && !context.isError,
    queryFn: ({ signal }) => readReceiptInventory(receipt, result, context.data!, signal) });
  const entries = current.data?.items.filter(item => item.namespace === result.namespace && item.slug === result.slug) ?? [];
  const inventoryIssue = canInspectCurrent ? receiptInventoryIssue(result, context.isError ? undefined : current.data, context.isError || current.isError) : null;
  const inventoryPending = canInspectCurrent && (!context.isError && (context.isPending || context.isFetching || current.isPending || current.isFetching));
  const presentation = receiptPresentation(operation, query.isError || !!mismatch, inventoryPending, inventoryIssue);
  return <section aria-label="SkillHub 安装结果" className="astron-sh-conversation-result astron-sh-conversation-install">
    <header className="astron-sh-conversation-identity"><SkillAvatar slug={result.slug} namespace={result.namespace} /><div><h4>{result.slug}</h4><p>@{result.namespace}/{result.slug}</p></div><span className="astron-sh-install-badge" data-tone={presentation.tone}>{presentation.label}</span></header>
    <p className="astron-sh-conversation-caption">v{result.version} · {result.targetLabel}</p>
    <p className="astron-sh-install-outcome" role={presentation.tone === 'warning' ? 'alert' : 'status'}>{presentation.message}</p>
    <div className="astron-sh-install-actions">{canInspectCurrent && operation?.phase === 'completed' && !context.isError && context.data && entries.some(item => item.state === 'present' && item.installationState !== 'disabled' && item.version === result.version && (item.scope === 'user' ? '当前用户' : '当前项目') === operation.targetLabel) && <UseSkillButton services={services.skillUse} revision={context.data.sourceRevision} resolve={async signal => {
      const inventory = await readReceiptInventory(receipt, result, context.data!, signal);
      const scope = operation.targetLabel === '当前用户' ? 'user' : operation.targetLabel === '当前项目' ? 'project' : undefined;
      return scope ? exactInstalledSkill(inventory, { ...result, scope })?.itemKey ?? '' : '';
    }} />}<Button variant="outline" disabled={query.isFetching || inventoryPending} onClick={() => {
      // Settled operation reads invalidate the enabled current-inventory query in the shared hook.
      void query.refetch();
    }}>{query.isFetching || inventoryPending ? '核验中…' : '核验状态'}</Button>
    {canInspectCurrent && <Button variant="ghost" disabled={opening} onClick={() => {
      setOpening(true); setActionStatus('');
      void openInstalled(result).catch(error => setActionStatus(error instanceof CatalogUnavailableError
        ? 'SkillHub 广场入口暂不可用，请在此卡片查询原操作。'
        : '会话或来源已变化，请从 SkillHub 入口打开当前已安装列表。'))
        .finally(() => setOpening(false));
    }}>在已安装中查看</Button>}</div>
    <details className="astron-sh-install-record"><summary>安装记录</summary>
      {!query.isError && !mismatch && operation?.phase === 'completed' && <p>
        上次核验：{operation.discovery === 'confirmed' ? 'DSH 已发现此次安装的技能内容' : '尚未确认 DSH 发现技能内容'}
        {query.dataUpdatedAt > 0 && <> · {readableDate(new Date(query.dataUpdatedAt).toISOString(), true)}</>}
      </p>}
    {canInspectCurrent && <section aria-label="当前安装记录">
      <h4>当前会话可见的安装记录</h4>
      {context.isError ? <p role="alert">{context.error.message}</p> : context.isFetching || current.isPending || current.isFetching ? <p role="status">正在核验当前安装记录…</p>
        : current.isError ? <p role="alert">{current.error.message}</p>
          : <>
            {!current.data.complete && <p role="alert">当前安装记录读取不完整，不能判断所有范围是否仍安装。</p>}
            {entries.length ? <ul>{entries.map(item => <li key={item.itemKey}>
              {item.scope === 'project' ? '当前项目' : '当前用户'} · {item.version} · {item.installationState === 'disabled' ? '已停用' : item.state === 'present' ? '文件存在' : '本地状态异常，请前往已安装核验'}
            </li>)}</ul> : current.data.complete ? <p>当前会话可见范围内已无此技能的安装记录。</p> : null}
          </>}
    </section>}

    {!query.isError && !mismatch && operation && <OperationDetails operation={operation} />}
    <details className="astron-sh-conversation-provenance"><summary>操作信息</summary><p>{result.sourceLabel} · {result.hostLabel}</p><p>原请求已接受于 <time dateTime={result.createdAt}>{readableDate(result.createdAt, true)}</time></p><p>操作 ID：<code>{result.operationId}</code></p>
      <button onClick={() => {
        if (!navigator.clipboard?.writeText) { setActionStatus('无法访问剪贴板，请手动选择操作 ID 复制。'); return; }
        void navigator.clipboard.writeText(result.operationId).then(() => setActionStatus('操作 ID 已复制。'),
          () => setActionStatus('复制失败，请手动选择操作 ID 复制。'));
      }}>复制操作 ID</button>
    </details>
    </details>
    {actionStatus && <p role="status">{actionStatus}</p>}
  </section>;
}

export function PendingInstallation({ result, services }: { result: PendingInstallResult; services?: SearchViewServices }) {
  const [open, setOpen] = useState(false);
  return <section className="astron-sh-conversation-result astron-sh-conversation-install" aria-label="选择安装范围">
    <header className="astron-sh-conversation-identity"><SkillAvatar slug={result.slug} namespace={result.namespace} />
      <div><h4>{result.slug}</h4><p>@{result.namespace}/{result.slug} · v{result.version}</p></div>
      <span className="astron-sh-install-badge" data-tone="warning">等待确认</span></header>
    <p>尚未安装。请选择安装范围并确认。</p>
    <p className="astron-sh-conversation-caption">当前项目：供这个项目中的 DSH 会话使用。<br />当前用户：可在其他项目中发现，读取仍受会话权限限制。</p>
    <Button disabled={!services} onClick={() => setOpen(true)}>选择范围并安装</Button>
    {!services && <p role="alert">当前会话无法打开确认界面，请重新搜索并选择技能。</p>}
    <p className="astron-sh-conversation-caption">这是等待确认时的记录；关闭或取消不会安装。来源或会话变化后请重新搜索。</p>
    {open && services && <SkillInstallDialog client={services.client} services={services.installation} initiatedFrom="conversation"
      identity={JSON.stringify([result.toolCallId, result.selectionRef])} close={() => setOpen(false)} load={async signal => {
        const [context, detail] = await Promise.all([services.remote.getContext(), services.openDetail(result.selectionRef, signal)]);
        if (!context.ok || !detail.ok || context.value.sourceRevision !== result.sourceRevision) throw new Error('原选择已失效，请重新搜索。');
        return { detail: detail.value, sourceRevision: context.value.sourceRevision };
      }} />}
  </section>;
}
