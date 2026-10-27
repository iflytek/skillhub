import { createContext, useContext, useId, useMemo, useRef, useState, type ReactNode } from 'react';
import { QueryClientProvider, useQuery, type QueryClient } from '@tanstack/react-query';
import type { ToolCallViewProps } from '@deepseek-ai/dsh-client-ui-tool/client';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { ConversationManagementResult, InstalledSkillList, InstalledUpdateCheck, SkillAvailability } from '../host/types.js';
import type { InstallationServices } from './install-control.js';
import { parseManagementResult, managementReason, type ManagementViewResult, type ManagementReceipt } from './management-result.js';
import { NativeControlStyles } from './native-control-styles.js';
import { ConversationStyles } from './conversation-styles.js';
import { Button, Tabs, TabsList, TabsTrigger, TabsContent } from './ui.js';
import { UseSkillButton } from './use-skill.js';
import { useInstallationOperation } from './use-installation-operation.js';

export interface ManagementViewServices {
  client: QueryClient;
  installation: InstallationServices;
  refreshInstalled(signal: AbortSignal): Promise<{ sourceRevision: string; list: InstalledSkillList }>;
  availability?(revision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<SkillAvailability>>;
  check(revision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<InstalledUpdateCheck>>;
  begin(revision: string, itemKey: string, action: 'upgrade' | 'uninstall', version: string | null, requestKey: string, signal: AbortSignal): Promise<RemoteResult<ConversationManagementResult>>;
  confirm(ref: string, confirmed: boolean, signal: AbortSignal): Promise<RemoteResult<ConversationManagementResult>>;
  assertCurrent(): void;
}
type Item = InstalledSkillList['items'][number];
const scopeLabel = (scope: string) => scope === 'user' ? '当前用户' : '当前项目';
class ManagementRequestError extends Error {
  constructor(readonly code: string) { super('技能管理请求未确认。'); }
}
function read<T>(response: RemoteResult<T>): T {
  if (!response.ok) throw new ManagementRequestError(response.error.code);
  return response.value;
}
function stale(error: unknown): boolean {
  return error instanceof ManagementRequestError && ['skillhub/context-stale', 'skillhub/request-conflict'].includes(error.code);
}
function failureMessage(error: unknown): string {
  return stale(error) ? '安装记录已变化。' : '暂时无法读取安装记录。';
}
/** Validate the historical reference without rebinding it to a newer version or another scope. */
export async function verifyManagementReference(services: ManagementViewServices, revision: string, itemKey: string, signal: AbortSignal): Promise<void> {
  services.assertCurrent();
  const list = read(await services.installation.installed(revision, signal));
  services.assertCurrent(); signal.throwIfAborted();
  if (!list.items.some(item => item.itemKey === itemKey)) {
    if (!list.complete || list.unavailable.length) throw new ManagementRequestError('skillhub/installation-unavailable');
    throw new ManagementRequestError('skillhub/context-stale');
  }
}
/** Only reference validation retires a row; task selection cancellation belongs to UseSkillButton. */
export function verifiedManagementUse(services: ManagementViewServices, revision: string, itemKey: string, onInvalid: (message: string) => void): InstallationServices['skillUse'] {
  const use = services.installation.skillUse;
  return use ? { useSkill: async (...args) => {
    try { await verifyManagementReference(services, revision, itemKey, args[2]); }
    catch (failure) { onInvalid(failureMessage(failure)); throw failure; }
    await use.useSkill(...args);
  } } : undefined;
}
export function ManagementToolView(props: ToolCallViewProps & { services: ManagementViewServices }) {
  const staleResponse = props.phase !== 'preparing' && props.phase !== 'start' && props.block.meta && typeof props.block.meta === 'object' && Reflect.get(props.block.meta, 'status') === 'not-confirmed' && Reflect.get(props.block.meta, 'reason') === 'CONTEXT_STALE';
  const result = props.phase === 'preparing' || props.phase === 'start' ? undefined : parseManagementResult(props.block.meta, props.callId);
  return <QueryClientProvider client={props.services.client}><div className="skillhub-controls">
    <NativeControlStyles /><ConversationStyles /><ManagementStyles />
    <section className={`astron-sh-conversation-result astron-sh-management-view ${result?.kind === 'skillhub-installed' ? 'astron-sh-management-catalog' : 'astron-sh-management-compact'}`} aria-label="SkillHub 技能管理">
      {props.phase === 'preparing' || props.phase === 'start' ? <p role="status">正在处理技能管理请求…</p>
        : props.block.isError || !result ? <ManagementRefreshBoundary services={props.services}><p role="alert">{staleResponse ? '安装记录已变化。' : '本次管理请求未确认。'}</p><RefreshInstalled /></ManagementRefreshBoundary>
          : <ManagementContent key={props.callId} result={result} services={props.services} />}
      {props.phase !== 'preparing' && props.phase !== 'start' && props.block.isError && props.inspect && <details className="astron-sh-conversation-provenance"><summary>操作详情</summary><Button size="sm" variant="ghost" onClick={props.inspect}>查看调用详情</Button></details>}
    </section>
  </div></QueryClientProvider>;
}
export function ManagementContent({ result, services }: { result: ManagementViewResult; services: ManagementViewServices }) {
  return <ManagementRefreshBoundary services={services} previousRevision={'sourceRevision' in result ? result.sourceRevision : undefined}><ManagementResultContent result={result} services={services} /></ManagementRefreshBoundary>;
}
function ManagementResultContent({ result, services }: { result: ManagementViewResult; services: ManagementViewServices }) {
  if (result.kind === 'skillhub-availability') return <AvailabilityResult value={result.diagnosis} standalone />;
  if (result.kind === 'skillhub-operation') return <ManagementOperation result={result} services={services} />;
  if (result.kind === 'skillhub-management') return <ManagementConfirmation initial={result} services={services} />;
  if (result.kind === 'skillhub-update-check') return <ManagementRow item={result.item} revision={result.sourceRevision} services={services} initialCheck={result.check} checkOnly />;
  return <InstalledList list={result} revision={result.sourceRevision} services={services} />;
}
export function AvailabilityResult({ value, standalone = false }: { value: SkillAvailability; standalone?: boolean }) {
  const messages: Record<SkillAvailability['reason'], [string, string]> = {
    observed: [value.association === 'not-associated' ? '当前会话已发现，来源未关联。' : '当前检查：文件与宿主加载一致。', '仅反映本次检查结果。'],
    'host-unavailable': ['宿主状态无法核验。', '请稍后重新检查。'],
    'files-missing': ['所选安装文件缺失。', '请重新查询安装记录。'],
    'files-unreadable': ['文件无法读取。', '请检查本地访问权限后重试。'],
    'local-changes': ['本地内容已修改。', '请核对修改是否符合预期。'],
    'baseline-unverified': ['安装内容基线无法核验。', '请重新查询安装记录。'],
    'definition-unverified': ['技能定义或宿主加载无法核验。', '请核对技能定义后重新检查。'],
    'snapshot-unavailable': ['宿主未提供完整性快照。', '请确认宿主技能服务状态。'],
    'snapshot-incomplete': ['宿主技能列表不完整。', '请稍后重新检查。'],
    'observation-changed': ['检查期间状态发生变化。', '请重新检查。'],
    'not-discovered': ['当前会话未发现此技能。', '请核对技能定义与当前会话。'],
    'same-name-winner': ['所选安装被同名技能覆盖。', '请核对当前会话的同名技能。'],
    'definition-changed': ['文件与宿主加载内容不一致。', '请等待宿主刷新后重新检查。'],
    'read-failed': ['暂时无法核验。', '请稍后重新检查。'],
  };
  const [message, advice] = messages[value.reason];
  return <div className="astron-sh-management-availability" role="status" aria-label="技能可用性">
    {standalone && <p><strong>{value.installation ? `@${value.installation.namespace}/${value.installation.slug} · v${value.installation.version}` : value.name ?? '技能可用性'}</strong>{value.scope && ` · ${scopeLabel(value.scope)}`}</p>}
    <p>{message}</p>{value.invocation && <p>用户调用{value.invocation.userInvocable ? '允许' : '禁用'} · 模型调用{value.invocation.modelInvocable ? '允许' : '禁用'}</p>}<small>{advice}</small>
  </div>;
}
function ManagementOperation({ result, services }: { result: Extract<ManagementViewResult, { kind: 'skillhub-operation' }>; services: ManagementViewServices }) {
  const original = result.operation;
  const query = useQuery({ queryKey: ['skillhub-management-operation', services.installation.sessionId, original?.context.hostInstanceId, original?.operationId],
    enabled: false, retry: false, queryFn: async () => {
      services.assertCurrent();
      if (!original) return null;
      const value = read(await services.installation.operation(original.operationId, original.context.hostInstanceId));
      services.assertCurrent(); return value;
    } });
  const op = query.data === undefined ? original : query.data;
  const verb = op?.kind === 'upgrade' ? '更新' : op?.kind === 'uninstall' ? '卸载' : '操作';
  const needsQuery = !!original && (query.isError || !op || op.phase !== 'completed' || op.warningCount > 0
    || (op.kind === 'upgrade' && op.discovery !== 'confirmed') || ['incomplete', 'unknown'].includes(op.recovery ?? ''));
  return <div className="astron-sh-management-operation" aria-label="操作查询结果">
    {op ? <><strong>{verb}{op.phase === 'completed' ? '已完成' : op.phase === 'failed' ? '未完成' : ['unknown', 'recovery-required'].includes(op.phase) ? '待核验' : '处理中'}</strong>
      <span>{op.namespace ? `@${op.namespace}/${op.slug}` : op.slug} · v{op.version} · {op.targetLabel}</span>{op.warningCount > 0 && <p role="alert">有告警需核验。</p>}
      {op.phase === 'completed' && op.kind === 'upgrade' && op.discovery !== 'confirmed' && <small>DSH 发现尚待确认</small>}{op.recovery && ['incomplete', 'unknown'].includes(op.recovery) && <small>恢复状态待核验</small>}</> : <p role="alert">暂时无法读取原操作。</p>}
    {query.isError && <p role="alert">查询失败，请重试。</p>}
    {needsQuery && <Button size="sm" variant="ghost" disabled={query.isFetching} onClick={() => void query.refetch()}>{query.isFetching ? '查询中…' : '查询原操作'}</Button>}
  </div>;
}
/** Input is already scoped to one verified Registry revision; no target is merged away. */
export function groupManagementItems(items: Item[]): Item[][] {
  const groups = new Map<string, Item[]>();
  for (const item of items) {
    const key = JSON.stringify([item.namespace, item.slug]);
    const group = groups.get(key);
    if (group) group.push(item); else groups.set(key, [item]);
  }
  return [...groups.values()];
}
export function managementTargetLabel(items: Item[], item: Item): string {
  const siblings = items.filter(candidate => candidate.scope === item.scope);
  return scopeLabel(item.scope) + (siblings.length > 1 ? ` ${siblings.findIndex(candidate => candidate.itemKey === item.itemKey) + 1}` : '');
}
function InstalledList({ list, revision, services, refreshed = false }: { list: InstalledSkillList; revision: string; services: ManagementViewServices; refreshed?: boolean }) {
  const [limit, setLimit] = useState(6);
  const groups = groupManagementItems(list.items);
  return <><header className="astron-sh-conversation-heading"><h3>{groups.length} 个技能 · {list.items.length} 处安装</h3><span>当前来源{refreshed ? ' · 已重新查询' : ''}</span></header>
    {(!list.complete || list.unavailable.length > 0) && <p role="alert">部分位置无法读取，列表可能不完整。{list.unavailable.map(scopeLabel).join('、')}{list.unavailable.length > 0 ? '状态未知。' : ''}</p>}
    {!list.items.length && <p>{list.complete && !list.unavailable.length ? '当前来源暂无安装记录。' : '暂未读取到安装记录。'}</p>}
    <div className="astron-sh-management-list" data-single={groups.length === 1}>{groups.slice(0, limit).map(items => <ManagementGroup key={JSON.stringify([items[0]!.namespace, items[0]!.slug])} items={items} revision={revision} services={services} />)}</div>
    {groups.length > limit && <Button size="sm" variant="ghost" onClick={() => setLimit(value => value + 6)}>查看更多</Button>}
    {(!list.complete || list.unavailable.length > 0) && <RefreshInstalled />}
  </>;
}
function ManagementGroup({ items, revision, services }: { items: Item[]; revision: string; services: ManagementViewServices }) {
  const [selected, setSelected] = useState(String(Math.max(0, items.findIndex(item => item.scope === 'project'))));
  const [pendingMutation, setPendingMutation] = useState(false);
  const [receipt, setReceipt] = useState<{ result: ConversationManagementResult; label: string }>();
  const item = items[Number(selected)] ?? items[0]!;
  const label = managementTargetLabel(items, item);
  if (receipt) return <ManagementConfirmation initial={receipt.result} services={services} targetLabel={receipt.label} onCancelled={() => setReceipt(undefined)} />;
  const row = <ManagementRow key={item.itemKey} item={item} revision={revision} services={services} grouped
    onPendingMutation={setPendingMutation} onManagement={result => setReceipt({ result, label })} />;
  return <article className="astron-sh-management-group" aria-label={`${item.namespace}/${item.slug}`}>
    <div className="astron-sh-management-name"><strong>{item.slug}</strong><span>@{item.namespace}</span></div>
    {items.length === 1 ? <div className="astron-sh-management-targets"><div className="astron-sh-management-position"><span>{label}</span></div><div className="astron-sh-management-target-content">{row}</div></div> : <Tabs className="astron-sh-management-targets" value={selected} onValueChange={value => { if (!pendingMutation) setSelected(value); }}>
      <div className="astron-sh-management-position"><TabsList aria-label="安装位置">{items.map((target, index) => <TabsTrigger key={target.itemKey} value={String(index)} disabled={pendingMutation}>{managementTargetLabel(items, target)}</TabsTrigger>)}</TabsList></div>
      <TabsContent key={item.itemKey} value={selected} className="astron-sh-management-target-content">
        {row}
      </TabsContent>
    </Tabs>}
  </article>;
}
const RefreshContext = createContext<{ pending: boolean; failed: boolean; refresh(): void } | undefined>(undefined);
/** A refresh replaces the tool body; it never nests current records under a historical installation. */
function ManagementRefreshBoundary({ services, previousRevision, children }: { services: ManagementViewServices; previousRevision?: string; children: ReactNode }) {
  const id = useId();
  const query = useQuery({ queryKey: ['skillhub-installed', services.installation.sessionId, 'management-refresh', id],
    enabled: false, retry: false, queryFn: async ({ signal }) => { services.assertCurrent(); const value = await services.refreshInstalled(signal); services.assertCurrent(); return value; } });
  const state = { pending: query.isFetching, failed: query.isError, refresh: () => { void query.refetch(); } };
  return <RefreshContext.Provider value={state}>{query.data ? <section aria-label="刷新后的安装记录">
    {previousRevision && previousRevision !== query.data.sourceRevision && <p role="status">技能来源已变化，以下是当前来源的安装记录。</p>}
    {query.isError ? <RefreshInstalled /> : <fieldset disabled={query.isFetching} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
      <InstalledList key={query.dataUpdatedAt} list={query.data.list} revision={query.data.sourceRevision} services={services} refreshed />
    </fieldset>}
  </section> : children}</RefreshContext.Provider>;
}
function RefreshInstalled() {
  const state = useContext(RefreshContext);
  if (!state) return null;
  return <div className="astron-sh-management-refresh"><Button size="sm" variant="outline" disabled={state.pending} onClick={state.refresh}>{state.pending ? '正在查询…' : '重新查询'}</Button>
    {state.failed && <p role="alert">读取失败，请重试。</p>}
  </div>;
}
function Identity({ item, compact = false, targetLabel }: { item: Item; compact?: boolean; targetLabel?: string }) {
  return <div className="astron-sh-management-identity"><strong>@{item.namespace}/{item.slug}</strong>{!compact && <span>v{item.version}</span>}<span className="astron-sh-management-location">{targetLabel ?? scopeLabel(item.scope)}</span></div>;
}
function ManagementRow({ item, revision, services, initialCheck, checkOnly = false, grouped = false, targetLabel, onPendingMutation, onManagement }: {
  item: Item; revision: string; services: ManagementViewServices; initialCheck?: InstalledUpdateCheck; checkOnly?: boolean; grouped?: boolean; targetLabel?: string;
  onPendingMutation?(pending: boolean): void; onManagement?(result: ConversationManagementResult): void;
}) {
  const [check, setCheck] = useState(initialCheck);
  const availabilityId = useId();
  const availability = useQuery({ queryKey: ['skillhub-availability', services.installation.sessionId, revision, item.itemKey, availabilityId],
    enabled: false, retry: false, gcTime: 0, queryFn: async ({ signal }) => {
      await verifyManagementReference(services, revision, item.itemKey, signal);
      if (!services.availability) throw new Error('Availability service unavailable');
      const value = read(await services.availability(revision, item.itemKey, signal));
      services.assertCurrent(); signal.throwIfAborted(); return value;
    } });

  const [moreOpen, setMoreOpen] = useState(false);
  const [useError, setUseError] = useState('');
  const moreTrigger = useRef<HTMLButtonElement>(null);
  const [pending, setPending] = useState(false);
  const lock = useRef(false);
  const [error, setError] = useState('');
  const [management, setManagement] = useState<ConversationManagementResult>();
  const request = useRef<{ action: string; version: string | null; key: string }>();
  const skillUse = useMemo(() => verifiedManagementUse(services, revision, item.itemKey, setError), [services, revision, item.itemKey]);
  async function run(action: 'check' | 'upgrade' | 'uninstall') {
    if (lock.current || error) return;
    lock.current = true; setPending(true); if (action !== 'check') onPendingMutation?.(true);
    try {
      const signal = new AbortController().signal;
      await verifyManagementReference(services, revision, item.itemKey, signal);
      if (action === 'check') setCheck(read(await services.check(revision, item.itemKey, signal)));
      else {
        const version = action === 'upgrade' ? check?.candidateVersion ?? null : null;
        if (!request.current || request.current.action !== action || request.current.version !== version) request.current = { action, version, key: crypto.randomUUID() };
        const result = read(await services.begin(revision, item.itemKey, action, version, request.current.key, signal));
        if (onManagement) onManagement(result); else setManagement(result);
      }
    } catch (failure) { setError(failureMessage(failure)); }
    finally { lock.current = false; setPending(false); if (action !== 'check') onPendingMutation?.(false); }
  }
  if (management) return <ManagementConfirmation initial={management} services={services} onCancelled={() => { request.current = undefined; setManagement(undefined); }} />;
  return <article className={checkOnly ? 'astron-sh-management-check' : 'astron-sh-management-row'} aria-label={checkOnly ? '更新检查' : undefined}
    onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setMoreOpen(false); }}
    onKeyDown={event => { if (event.key === 'Escape' && moreOpen) { event.preventDefault(); setMoreOpen(false); moreTrigger.current?.focus(); } }}>
    {grouped ? <span className="astron-sh-management-version">{targetLabel && `${targetLabel} · `}{check?.state !== 'upgradable' && `v${item.version}`}</span> : <Identity item={item} compact={checkOnly && check?.state === 'upgradable'} />}
    {error ? <><p role="alert">{error}</p><RefreshInstalled /></> : <>
      {item.state !== 'present' && <p role="alert">{item.state === 'missing' ? '安装文件缺失' : '安装记录待核验'}</p>}
      {check && <p role={check.state === 'unavailable' || check.state === 'blocked' ? 'alert' : 'status'}>{check.state === 'upgradable' ? `v${check.currentVersion} → v${check.candidateVersion}` : check.state === 'current' ? '已是最新版本' : check.state === 'unavailable' ? '更新检查未完成，请重试。' : managementReason(check.reason)}</p>}
      {checkOnly && check?.state === 'blocked' && ['local-record-unverified', 'record-changed', 'metadata-invalid'].includes(check.reason) && <RefreshInstalled />}
      {!checkOnly && item.state !== 'present' && item.state !== 'missing' && <RefreshInstalled />}
      <div className="astron-sh-management-actions">
        {check?.state === 'upgradable' && <Button size="sm" variant="default" disabled={pending} onClick={() => void run('upgrade')}>更新</Button>}
        {check?.state === 'unavailable' && <Button size="sm" variant="ghost" disabled={pending} onClick={() => void run('check')}>{pending ? '检查中…' : '重试检查'}</Button>}
        {!checkOnly && !check && item.state === 'present' && <UseSkillButton services={skillUse} revision={revision} itemKey={item.itemKey} onError={setUseError} />}
        {!checkOnly && (services.availability || (item.state === 'present' && (!check || check.state === 'upgradable'))) && <Button ref={moreTrigger} size="sm" variant="ghost" className="astron-sh-management-more-trigger" aria-label="更多操作" aria-expanded={moreOpen} disabled={pending} onClick={() => setMoreOpen(value => !value)}>···</Button>}
        {!checkOnly && item.state === 'missing' && <Button size="sm" disabled={pending} onClick={() => void run('uninstall')}>清理记录</Button>}
      </div>
      {useError && <p className="astron-sh-management-use-error" role="alert">{useError}</p>}
      {availability.isFetching ? <p role="status">正在检查可用性…</p> : availability.isError ? <><p role="alert">{stale(availability.error) ? '安装记录已变化，请重新查询。' : '可用性暂时无法核验。'}</p>{stale(availability.error) && <RefreshInstalled />}</> : availability.data && <AvailabilityResult value={availability.data} />}
      {moreOpen && <div className="astron-sh-management-more-content" aria-label="技能操作">
        {services.availability && <Button size="sm" variant="ghost" disabled={pending || availability.isFetching} onClick={() => { setMoreOpen(false); if (!availability.isFetching) void availability.refetch(); }}>检查可用性</Button>}
        {!check && item.state === 'present' && <Button size="sm" variant="ghost" disabled={pending} onClick={() => { setMoreOpen(false); void run('check'); }}>检查更新</Button>}
        {item.state === 'present' && <Button size="sm" variant="ghost" disabled={pending} onClick={() => { setMoreOpen(false); void run('uninstall'); }}>卸载</Button>}
      </div>}
    </>}
  </article>;
}
export function ManagementConfirmation({ initial, services, onCancelled, targetLabel }: { initial: ManagementReceipt; services: ManagementViewServices; onCancelled?(): void; targetLabel?: string }) {
  const [result, setResult] = useState(initial);
  const [pending, setPending] = useState(false);
  const lock = useRef(false);
  const [error, setError] = useState('');
  const [expired, setExpired] = useState(false);
  const decision = useRef(false);
  const operation = useInstallationOperation(services.installation, result.operation?.context.hostInstanceId ?? '', result.status === 'accepted' ? result.operation?.operationId : undefined);
  async function confirm(confirmed: boolean) {
    if (!result.confirmationRef || lock.current || expired) return;
    lock.current = true; decision.current = confirmed; setPending(true); setError('');
    try { services.assertCurrent(); const next = read(await services.confirm(result.confirmationRef, confirmed, new AbortController().signal)); setResult(next); if (next.status === 'cancelled') onCancelled?.(); }
    catch (failure) { setExpired(stale(failure)); setError(stale(failure) ? failureMessage(failure) : '确认结果暂时无法读取，请查询原请求。'); }
    finally { lock.current = false; setPending(false); }
  }
  const verb = result.action === 'upgrade' ? '更新' : result.item.state === 'missing' ? '清理记录' : '卸载';
  const completed = operation.data?.phase === 'completed';
  const warned = (operation.data?.warningCount ?? 0) > 0 || !!operation.data?.resultDetails?.warnings.length;
  const discoveryPending = completed && result.action === 'upgrade' && operation.data?.discovery !== 'confirmed';
  const recoveryPending = !!operation.data && ['incomplete', 'unknown'].includes(operation.data.recovery);
  return <article className={`astron-sh-management-receipt ${completed ? 'is-complete' : ''}`} aria-label={`${verb}技能`}><Identity item={result.item} compact targetLabel={targetLabel} />
    <p className="astron-sh-management-version">v{result.item.version}{result.action === 'upgrade' && result.candidateVersion ? ` → v${result.candidateVersion}` : ''}</p>
    {expired ? <><p role="alert">{error}</p><RefreshInstalled /></> : <>
      {result.status === 'awaiting-confirmation' && (error ? <><p role="alert">{error}</p><Button size="sm" disabled={pending} onClick={() => void confirm(decision.current)}>重试原请求</Button></> : <><p>确认{verb}这份安装？{result.item.scope === 'user' ? '影响当前用户的多个项目。' : '仅影响当前项目。'}</p>
        <div className="astron-sh-management-actions"><Button size="sm" disabled={pending} onClick={() => void confirm(false)}>取消</Button><Button size="sm" variant={result.action === 'uninstall' ? 'destructive' : 'default'} disabled={pending || !result.confirmationRef} onClick={() => void confirm(true)}>{pending ? '处理中…' : `确认${verb}`}</Button></div></>)}
      {result.status === 'cancelled' && <><p role="status">已取消，未执行{verb}。</p></>}
      {result.status === 'blocked' && <><p role="alert">{managementReason(result.reason)}</p>{['record-changed', 'local-record-unverified', 'metadata-invalid'].includes(result.reason) && <RefreshInstalled />}</>}
      {result.status === 'accepted' && <div aria-live="polite">
        {operation.isError || operation.data === null ? <p role="alert">操作状态暂时无法确认，请重试。</p>
          : completed ? <p role="status">{verb}已完成{warned ? '，有告警需核验。' : '。'}</p>
            : operation.data?.phase === 'failed' ? <p role="alert">{verb}未完成，请核验当前安装。</p>
              : operation.data && ['unknown', 'recovery-required'].includes(operation.data.phase) ? <p role="alert">结果待核验，请勿重复操作。</p> : <p role="status">正在{verb}…</p>}
        {discoveryPending && <p>文件已更新，DSH 发现尚待确认。</p>}{recoveryPending && <p role="alert">恢复状态待核验。</p>}
        {(warned || discoveryPending || recoveryPending || operation.isError || operation.data === null || (!!operation.data && ['failed', 'unknown', 'recovery-required'].includes(operation.data.phase))) && <Button size="sm" variant="ghost" disabled={operation.isFetching} onClick={() => void operation.refetch()}>查询操作状态</Button>}
      </div>}
    </>}
  </article>;
}
function ManagementStyles() {
  return <style>{`
.skillhub-controls .astron-sh-management-view{width:100%;max-width:380px;box-sizing:border-box;padding:10px 12px;border:0;border-radius:6px;background:var(--astron-sh-soft);font-size:13px;line-height:20px}
.skillhub-controls .astron-sh-management-view .astron-sh-conversation-heading{margin:0 0 4px;gap:8px}
.skillhub-controls .astron-sh-management-view .astron-sh-conversation-heading h3{font-size:12px;font-weight:500;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-management-view .astron-sh-conversation-heading>span{font-size:11px}
.skillhub-controls .astron-sh-management-view p{margin:0;font-size:12px;line-height:20px}
.skillhub-controls .astron-sh-management-view:has(.astron-sh-management-list){max-width:720px;padding:0;background:transparent}
.skillhub-controls .astron-sh-management-view:has(.astron-sh-management-list[data-single=true]){max-width:380px}
.skillhub-controls .astron-sh-management-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,280px),1fr));align-items:stretch;gap:10px}
.skillhub-controls .astron-sh-management-list>.astron-sh-management-group,.skillhub-controls .astron-sh-management-list>.astron-sh-management-receipt{padding:10px;border:1px solid var(--astron-sh-border);border-radius:7px;background:var(--astron-sh-soft)}
.skillhub-controls .astron-sh-management-row,.skillhub-controls .astron-sh-management-check,.skillhub-controls .astron-sh-management-receipt{display:flex;align-items:center;flex-wrap:wrap;gap:4px 10px;min-width:0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-management-row{padding:5px 0}
.skillhub-controls .astron-sh-management-row+.astron-sh-management-row{border-top:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-management-identity{display:flex;align-items:baseline;flex-wrap:wrap;gap:2px 8px;min-width:0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-management-identity strong{font-size:13px;font-weight:600;min-width:0}
.skillhub-controls .astron-sh-management-identity span,.skillhub-controls .astron-sh-management-version{font-size:12px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-management-actions{display:flex;flex-wrap:wrap;align-items:center;gap:4px;margin-left:auto}
.skillhub-controls .astron-sh-management-view .astron-sh-button{min-height:28px;padding:3px 8px;margin:0;font-size:12px;line-height:20px;border-radius:5px}
.skillhub-controls .astron-sh-management-receipt>div[aria-live]{display:flex;align-items:center;flex-wrap:wrap;gap:4px 8px}
.skillhub-controls .astron-sh-management-receipt.is-complete [role=status]{color:var(--astron-sh-accent)}
.skillhub-controls .astron-sh-management-operation{display:flex;flex-wrap:wrap;align-items:baseline;gap:4px 10px;font-size:12px;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-management-operation small{color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-management-refresh{display:flex;align-items:center;flex-wrap:wrap;gap:4px 8px}
.skillhub-controls .astron-sh-management-view .astron-sh-conversation-provenance{margin-top:4px}
.skillhub-controls .astron-sh-management-group{min-width:0;container-type:inline-size;display:grid;grid-template-rows:minmax(20px,auto) auto;align-content:start;gap:4px}
.skillhub-controls .astron-sh-management-name{display:flex;align-items:baseline;flex-wrap:wrap;gap:4px 8px;margin-bottom:0;min-height:20px;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-management-name strong{font-size:13px}.skillhub-controls .astron-sh-management-name span{font-size:11px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-management-targets{display:grid;grid-template-rows:minmax(28px,auto) auto;gap:4px;min-width:0}
.skillhub-controls .astron-sh-management-position{display:flex;align-items:center;min-height:28px;min-width:0;font-size:11px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-management-targets .astron-sh-tabs-list{margin:0;padding:2px;gap:2px;border:1px solid var(--astron-sh-border);border-radius:5px;background:transparent;flex-wrap:wrap}
.skillhub-controls .astron-sh-management-targets .astron-sh-tabs-trigger{min-height:22px;padding:1px 6px;font-size:11px;line-height:20px;border:0;border-radius:3px;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-management-targets .astron-sh-tabs-trigger[data-state=active]{background:color-mix(in srgb,var(--astron-sh-fg) 9%,transparent);color:var(--astron-sh-fg);box-shadow:none}
.skillhub-controls .astron-sh-management-target-content{min-width:0;margin:0}
.skillhub-controls .astron-sh-management-target-content>.astron-sh-management-row{padding:0;min-height:28px}
.skillhub-controls .astron-sh-management-view .astron-sh-management-more-trigger{width:28px;min-width:28px;padding:3px 0;border:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-management-more-content{display:flex;justify-content:flex-end;align-items:center;gap:4px;flex-basis:100%;min-width:0;padding-top:3px;border-top:1px solid var(--astron-sh-border)}
.skillhub-controls .astron-sh-management-availability{flex-basis:100%;min-width:0;overflow-wrap:anywhere}
.skillhub-controls .astron-sh-management-use-error{flex-basis:100%;min-width:0;overflow-wrap:anywhere;color:var(--astron-sh-muted)}
.skillhub-controls .astron-sh-management-actions:empty{display:none}
@container(max-width:440px){.skillhub-controls .astron-sh-management-row>.astron-sh-management-identity{flex-basis:100%}.skillhub-controls .astron-sh-management-actions{margin-left:auto}}
`}</style>;
}
