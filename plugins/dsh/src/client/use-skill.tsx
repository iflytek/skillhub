import { useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import type { InputActions, SessionInput, InputState } from '@deepseek-ai/dsh-client-ui-conversation/client';
import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { InstalledSkillList, SkillUsePreparation, SkillExample, SkillExamples } from '../host/types.js';
import { Button } from './ui.js';
import { NativeControlStyles } from './native-control-styles.js';

type SkillInputActions = Pick<InputActions, 'captureInsertion' | 'insertText'>;
type SkillInput = { state: Pick<SessionInput['state'], 'getSnapshot'>; focus(): void };
export type ExampleSelection = { locale: string; id: string } | { custom: true };
type ChooseExample = (items: SkillExample[], locale: string, signal: AbortSignal) => Promise<ExampleSelection>;
export function selectExampleLocale(examples: Extract<SkillExamples, { state: 'ready' }>, language?: string): string {
  const keys = Object.keys(examples.items);
  let candidate = language?.toLowerCase();
  while (candidate) { const match = keys.find(key => key.toLowerCase() === candidate); if (match) return match; candidate = candidate.includes('-') ? candidate.slice(0, candidate.lastIndexOf('-')) : undefined; }
  return keys.find(key => key.toLowerCase() === examples.defaultLocale.toLowerCase()) ?? examples.defaultLocale;
}
export function exampleTask(prompt: string, name: string, draft: string): string {
  const token = `/${name}`;
  if (prompt.includes('{{skill}}')) return prompt.replaceAll('{{skill}}', token);
  return `${draft.split(/\s+/u).includes(token) ? '' : `请使用 ${token} 完成本次任务。\n`}${prompt}`;
}
/** Read separator boundaries in the native detect projection; never alter captured coordinates. */
export function skillTaskInsertion(task: string, state: Pick<InputState, 'draft' | 'occurrences'>, at: number): string {
  let projection = '', offset = 0;
  for (const occurrence of state.occurrences) {
    projection += state.draft.slice(offset, occurrence.offset) + '\uFFFC';
    offset = occurrence.offset + occurrence.length;
  }
  projection += state.draft.slice(offset);
  const before = projection[at - 1], after = projection[at];
  const prefix = before !== undefined && before !== '\n' && before !== '\r' && !/^[\r\n]/u.test(task) ? '\n' : '';
  const suffix = after !== undefined && after !== '\n' && after !== '\r' && !/[\r\n]$/u.test(task) ? '\n' : '';
  return prefix + task + suffix;
}
type Item = InstalledSkillList['items'][number];
export interface SkillUseServices {
  useSkill(revision: string, itemKey: string | undefined, signal: AbortSignal, resolve?: (signal: AbortSignal) => Promise<string>, choose?: ChooseExample, onHandoff?: () => void): Promise<void>;
}
export interface SkillUseCapture {
  actions: SkillInputActions;
  input: SkillInput;
  assertCurrent(): void;
  dispose?(): void;
}
export interface SkillUseEnvironment {
  capture(sessionId: string, actions: SkillInputActions): SkillUseCapture;
  revision(): Promise<string>;
  prepare(sessionId: string, revision: string, itemKey: string, signal: AbortSignal): Promise<RemoteResult<SkillUsePreparation>>;
  locale?(): string | undefined;
  subscribeLocale?(listener: () => void): () => void;
  reveal(): void;
  newConversation?(sessionId: string, revision: string, itemKey: string, signal: AbortSignal): Promise<{ sessionId: string; itemKey: string }>;
  subscribeContext?(listener: () => void): () => void;
  current?(sessionId: string): boolean;
}
const blocked: Record<Extract<SkillUsePreparation, { status: 'blocked' }>['reason'], string> = {
  'installation-unverified': '安装记录已变化，请刷新已安装列表。',
  'operation-pending': '此技能仍有操作进行中，请完成后重试。',
  'shared-installation': '此安装暂不支持直接使用，请在 DSH 中选择技能。',
  'installation-disabled': '该安装已停用，请先启用。',
  'files-changed': '本地技能文件已变化，请先核对安装。',
  'skill-unavailable': '当前会话尚未发现此技能，请稍后重试。',
  'skill-shadowed': '当前会话实际使用的是另一份同名技能，无法直接使用所选安装。请检查安装位置，并选择当前会话生效的那份技能。',
  'user-invocation-disabled': '此技能不允许用户直接调用。',
  'unsupported-name': '此技能名称暂不支持原生调用。',
  'host-unavailable': 'DSH 技能服务暂不可用，请重新打开会话。',
};
class SkillUseCancelled extends Error {}
const stale = () => new Error('会话、来源或输入框已变化，请在当前会话重试。');

/** One path for every entry. All edits use native revision comparison; no whole-draft replacement. */
export interface SkillCasePanel {
  sessionId: string; name: string; items: SkillExample[]; locale: string;
  busy: boolean; error?: string; finish(selection?: ExampleSelection): void;
}
export class SkillUseController {
  private activeSessions = new Set<string>();
  private panel: SkillCasePanel | undefined;
  private catalogRequest: Promise<void> | undefined;
  private routeAbort: AbortController | undefined;
  readonly casePanel = () => this.panel;
  private publishPanel(panel: SkillCasePanel | undefined): void { this.panel = panel; for (const listener of this.listeners) listener(); }
  private choose(sessionId: string, name: string): ChooseExample {
    return (items, locale, signal) => new Promise((resolve, reject) => {
      this.panel?.finish();
      let settled = false;
      const abort = () => finish();
      const finish = (selection?: ExampleSelection) => {
        if (settled) return;
        settled = true; signal.removeEventListener('abort', abort);
        if (selection) { this.publishPanel({ ...this.panel!, busy: true }); resolve(selection); }
        else { this.publishPanel(undefined); reject(new SkillUseCancelled('已取消选择。')); }
      };
      signal.addEventListener('abort', abort, { once: true });
      if (signal.aborted) { finish(); return; }
      this.publishPanel({ sessionId, name, items, locale, finish, busy: false });
    });
  }
  private async waitForComposer(sessionId: string, signal: AbortSignal): Promise<void> {
    if (this.leases.get(sessionId)?.size) return;
    await new Promise<void>((resolve, reject) => {
      const done = (error?: Error) => { stop?.(); clearTimeout(timer); this.listeners.delete(check); signal.removeEventListener('abort', abort); if (error) reject(error); else resolve(); };
      const check = () => { if (this.environment.current?.(sessionId) === false) done(stale()); else if (this.leases.get(sessionId)?.size) done(); };
      const abort = () => done(stale());
      const timer = setTimeout(() => done(new Error('新对话输入框尚未就绪，请重新打开会话。')), 10000);
      this.listeners.add(check); const stop = this.environment.subscribeContext?.(check); signal.addEventListener('abort', abort, { once: true });
      if (signal.aborted) abort(); else check();
    });
  }
  catalogServices(sessionId: string): SkillUseServices {
    return { useSkill: (revision, itemKey, signal, resolve, _choose, onHandoff) => {
      if (this.catalogRequest) return this.catalogRequest;
      const route = new AbortController(); this.routeAbort = route;
      const run = async () => {
        let target: { sessionId: string; itemKey: string } | undefined;
        try {
          if (!this.environment.newConversation) throw new Error('DSH 新对话服务暂不可用。');
          const key = itemKey ?? await resolve?.(signal);
          if (signal.aborted || route.signal.aborted || this.disposed) throw stale();
          if (!key) throw new Error('原安装版本已变化，请刷新已安装列表。');
          target = await this.environment.newConversation(sessionId, revision, key, AbortSignal.any([signal, route.signal]));
          // The catalog unmount now releases its button. The resident controller owns the handoff.
          await this.waitForComposer(target.sessionId, route.signal);
          await this.use(target.sessionId, revision, target.itemKey, route.signal, undefined, undefined, onHandoff);
        } catch (cause) {
          if (target && !this.disposed && !route.signal.aborted && this.environment.current?.(target.sessionId) !== false && !(cause instanceof SkillUseCancelled) && !this.panel) {
            this.publishPanel({ sessionId: target.sessionId, name: '', items: [], locale: '', busy: false, error: cause instanceof Error ? cause.message : '暂时无法使用，请重试。', finish: () => this.publishPanel(undefined) });
          }
          throw cause;
        }
      };
      this.catalogRequest = run().finally(() => { this.catalogRequest = undefined; if (this.routeAbort === route) this.routeAbort = undefined; });
      return this.catalogRequest;
    } };
  }
  private leases = new Map<string, Set<{ actions: SkillInputActions }>>();
  private listeners = new Set<() => void>();
  private pendingFocus: { sessionId: string; capture: SkillUseCapture } | undefined;
  private disposed = false;
  private inFlight = new Set<AbortController>();
  bind(sessionId: string, actions: SkillInputActions): () => void {
    const entry = { actions };
    const leases = this.leases.get(sessionId) ?? new Set();
    this.leases.set(sessionId, leases); leases.add(entry);
    for (const listener of this.listeners) listener();
    return () => { leases.delete(entry); if (!leases.size) this.leases.delete(sessionId); };
  }
  constructor(private environment: SkillUseEnvironment) {}
  readonly subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  readonly focusRequest = () => this.pendingFocus;
  focus(sessionId: string): void {
    const pending = this.pendingFocus;
    if (!pending || pending.sessionId !== sessionId) return;
    this.pendingFocus = undefined;
    try { if (!this.disposed) { pending.capture.assertCurrent(); pending.capture.input.focus(); } } catch { /* Navigation invalidated the request; never focus a different input. */ }
  }
  services(sessionId: string): SkillUseServices {
    return { useSkill: (revision, itemKey, signal, resolve, choose, onHandoff) => this.use(sessionId, revision, itemKey, signal, resolve, choose, onHandoff) };
  }
  private async use(sessionId: string, revision: string, itemKey: string | undefined, signal: AbortSignal, resolve?: (signal: AbortSignal) => Promise<string>, choose?: ChooseExample, onHandoff?: () => void): Promise<void> {
    const actions = [...(this.leases.get(sessionId) ?? [])].at(-1)?.actions;
    if (this.disposed || !actions) throw new Error('请先打开当前会话，再使用此技能。');
    if (this.activeSessions.has(sessionId)) throw new Error('请先完成或关闭当前技能的案例选择。');
    const capture = this.environment.capture(sessionId, actions);
    this.activeSessions.add(sessionId);
    const controller = new AbortController();
    const abort = () => controller.abort();
    signal.addEventListener('abort', abort, { once: true });
    this.inFlight.add(controller);
    let detached = false;
    const stopLocale = this.environment.subscribeLocale?.(abort);
    const stopContext = this.environment.subscribeContext?.(() => { try { capture.assertCurrent(); } catch { abort(); } });
    const assertCurrent = () => {
      if (this.disposed || (!detached && signal.aborted) || controller.signal.aborted || ![...(this.leases.get(sessionId) ?? [])].some(lease => lease.actions === actions)) throw stale();
      capture.assertCurrent();
      if (capture.input.state.getSnapshot().phase !== 'plain') throw stale();
    };
    try {
      capture.assertCurrent();
      if (capture.input.state.getSnapshot().phase !== 'plain') throw new Error('请先完成或取消输入框中的命令，再使用技能。');
      const initialDraftRev = capture.input.state.getSnapshot().draftRev;
      let span: ReturnType<SkillInputActions['captureInsertion']> | undefined;
      assertCurrent();
      if (await this.environment.revision() !== revision) throw stale();
      assertCurrent();
      const key = itemKey ?? await resolve?.(controller.signal);
      assertCurrent();
      if (!key) throw new Error('原安装版本已变化，请在已安装中选择当前版本。');
      const response = await this.environment.prepare(sessionId, revision, key, controller.signal);
      assertCurrent();
      if (!response.ok) throw stale();
      if (response.value.status === 'blocked') throw new Error(blocked[response.value.reason]);
      if (await this.environment.revision() !== revision) throw stale();
      assertCurrent();
      let prepared = response.value;
      let task: string | undefined;
      const examples = prepared.examples;
      const language = this.environment.locale?.();
      const locale = examples.state === 'ready' ? selectExampleLocale(examples, language) : '';
      const items = examples.state === 'ready' ? examples.items[locale] ?? [] : [];
      if (items.length > 0 || examples.state === 'unavailable') {
        if (!choose || onHandoff) {
          detached = true; signal.removeEventListener('abort', abort);
          // Closing a modal unmounts its button and aborts the entry signal: ownership has transferred first.
          onHandoff?.(); this.environment.reveal(); assertCurrent();
        }
        const selection = await (choose ?? this.choose(sessionId, prepared.name))(items, locale, controller.signal);
        assertCurrent();
        if (this.environment.locale?.() !== language) throw stale();
        span = actions.captureInsertion();
        const latest = await this.environment.prepare(sessionId, revision, key, controller.signal);
        assertCurrent();
        if (!latest.ok || latest.value.status !== 'ready') throw stale();
        const fresh = latest.value;
        if (fresh.name !== prepared.name || fresh.version !== prepared.version || fresh.scope !== prepared.scope
          || this.environment.locale?.() !== language) throw stale();
        if (examples.state === 'ready' && (fresh.examples.state !== 'ready'
          || fresh.examples.digest !== examples.digest || selectExampleLocale(fresh.examples, language) !== locale)) throw new Error('技能或案例已变化，请重新选择。');
        if ('id' in selection) {
          if (selection.locale !== locale || fresh.examples.state !== 'ready') throw stale();
          const item = fresh.examples.items[locale]?.find(item => item.id === selection.id);
          if (!item) throw stale();
          task = item.prompt;
        }
        prepared = fresh;
        if (await this.environment.revision() !== revision) throw stale();
        assertCurrent();
      }
      if (!span) {
        span = actions.captureInsertion();
        if (span.draftRev !== initialDraftRev) throw new Error('草稿已被编辑，内容已保留，请再次点击使用。');
      }
      const name = prepared.name;
      // A non-command leading phrase avoids collisions with native slash commands.
      const snapshot = capture.input.state.getSnapshot();
      if (snapshot.draftRev !== span.draftRev) throw new Error('草稿已被编辑，内容已保留，请再次点击使用。');
      const draft = snapshot.draft;
      const token = `/${name}`;
      const exists = draft.split(/\s+/u).includes(token);
      const body = task !== undefined ? exampleTask(task, name, draft) : `请使用 ${token} 完成本次任务。请补充任务目标和输入材料。`;
      if ((task !== undefined || !exists) && !actions.insertText(skillTaskInsertion(body, snapshot, span.end), { start: span.end, end: span.end, draftRev: span.draftRev })) {
        throw new Error('草稿已被编辑，内容已保留，请再次点击使用。');
      }
      this.pendingFocus = { sessionId, capture };
      this.environment.reveal();
      for (const listener of this.listeners) listener();
    } catch (cause) {
      if (this.panel?.sessionId === sessionId && !(cause instanceof SkillUseCancelled) && !controller.signal.aborted) {
        this.publishPanel({ ...this.panel, items: [], busy: false, error: cause instanceof Error ? cause.message : '暂时无法使用，请重试。', finish: () => this.publishPanel(undefined) });
      }
      throw cause;
    } finally {
      if (this.panel?.sessionId === sessionId && !this.panel.error) this.publishPanel(undefined);
      this.activeSessions.delete(sessionId);
      stopContext?.(); stopLocale?.();
      capture.dispose?.();
      signal.removeEventListener('abort', abort); this.inFlight.delete(controller);
    }
  }
  dispose(): void { this.disposed = true; this.routeAbort?.abort(); this.panel?.finish(); for (const request of this.inFlight) request.abort(); this.inFlight.clear(); this.leases.clear(); this.pendingFocus = undefined; this.listeners.clear(); }
}

/** Standard native slot props supply actions; no editor DOM/private controller access. */
export function SkillUseBinding({ controller, sessionId, actions, children }: { controller: SkillUseController; sessionId?: string; actions?: InputActions; children?: ReactNode }) {
  useLayoutEffect(() => sessionId && actions ? controller.bind(sessionId, actions) : undefined, [controller, sessionId, actions]);
  return <>{children}</>;
}
/** Runs after the resident native composer mounts, including when returning from the catalog. */
export function SkillUseFocus({ controller, sessionId }: { controller: SkillUseController; sessionId: string }) {
  const request = useSyncExternalStore(controller.subscribe, controller.focusRequest, controller.focusRequest);
  useEffect(() => { if (request) controller.focus(sessionId); }, [controller, sessionId, request]);
  return null;
}

export function exactInstalledSkill(inventory: InstalledSkillList, identity: Pick<Item, 'namespace' | 'slug' | 'version' | 'scope'>): Item | undefined {
  if (!inventory.complete || inventory.unavailable.includes(identity.scope)) return;
  const matches = inventory.items.filter(item => item.namespace === identity.namespace && item.slug === identity.slug && item.version === identity.version && item.scope === identity.scope);
  return matches.length === 1 && matches[0]?.state === 'present' && matches[0].installationState !== 'disabled' ? matches[0] : undefined;
}

/** Request ownership survives renders but is revoked when the entry's target changes. */
export class SkillUseButtonRequests {
  private current: AbortController | undefined;
  private snapshot = { busy: false, error: '' };
  private listeners = new Set<() => void>();
  readonly subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  readonly getSnapshot = () => this.snapshot;
  private update(busy: boolean, error: string) {
    this.snapshot = { busy, error }; for (const listener of this.listeners) listener();
  }
  reset(): void { this.current?.abort(); this.current = undefined; this.update(false, ''); }
  async run(action: (signal: AbortSignal) => Promise<void>, onUsed?: () => void): Promise<void> {
    if (this.current) return;
    const request = new AbortController(); this.current = request; this.update(true, '');
    try {
      await action(request.signal);
      if (this.current === request && !request.signal.aborted) onUsed?.();
    } catch (cause) {
      if (this.current === request && !request.signal.aborted && !(cause instanceof SkillUseCancelled)) this.update(true, cause instanceof Error ? cause.message : '暂时无法使用，请重试。');
    } finally {
      if (this.current === request) { this.current = undefined; this.update(false, this.snapshot.error); }
    }
  }
}

/** Resident full-width native dock; catalog teardown cannot remove the pending choice. */
export function SkillCaseChoices({ controller, sessionId }: { controller: SkillUseController; sessionId: string }) {
  const panel = useSyncExternalStore(controller.subscribe, controller.casePanel, controller.casePanel);
  const [selected, setSelected] = useState('');
  useEffect(() => { setSelected(panel?.items[0]?.id ?? ''); }, [panel?.items]);
  if (!panel || panel.sessionId !== sessionId) return null;
  return <section className="skillhub-controls astron-sh-case-panel" aria-label="技能任务案例">
    <NativeControlStyles />
    <header><div><strong>{panel.name ? `使用 /${panel.name}` : '使用此技能'}</strong><span>选择任务后填入输入框，可继续编辑。</span></div><Button size="icon" variant="ghost" aria-label="关闭案例选择" disabled={panel.busy} onClick={() => panel.finish()}>×</Button></header>
    {panel.error ? <p role="alert">{panel.error}</p> : <>
      {!panel.items.length && <p>案例暂时无法读取，仍可自定义任务。</p>}
      <div className="astron-sh-case-grid" role="group" aria-label="可选任务">{panel.items.map(item => <button type="button" key={item.id} disabled={panel.busy} aria-pressed={selected === item.id} className="astron-sh-case" onClick={() => setSelected(item.id)}><strong>{item.title}</strong><span>{item.prompt.replaceAll('{{skill}}', `/${panel.name}`)}</span></button>)}</div>
      <footer><Button size="sm" variant="ghost" disabled={panel.busy} onClick={() => panel.finish({ custom: true })}>自定义任务</Button><Button size="sm" variant="default" disabled={panel.busy || !selected} onClick={() => panel.finish({ locale: panel.locale, id: selected })}>{panel.busy ? '核验中…' : '填入输入框'}</Button></footer>
    </>}
  </section>;
}

export function UseSkillButton({ services, revision, itemKey, resolve, onUsed, onHandoff, onError, renderAction, label = '使用此技能' }: {
  services?: SkillUseServices; revision: string; itemKey?: string; label?: string;
  /** Keep the request owner mounted while a transient menu action closes. */
  renderAction?(action: { disabled: boolean; run(): void }): React.ReactNode;
  resolve?(signal: AbortSignal): Promise<string>; onUsed?(): void; onHandoff?(): void; onError?(message: string): void;
}) {
  const ref = useRef<SkillUseButtonRequests>();
  if (!ref.current) ref.current = new SkillUseButtonRequests();
  const requests = ref.current;
  const { busy, error } = useSyncExternalStore(requests.subscribe, requests.getSnapshot, requests.getSnapshot);
  useLayoutEffect(() => { requests.reset(); return () => requests.reset(); }, [requests, services, revision, itemKey]);
  useEffect(() => { onError?.(error); }, [error, onError]);
  const run = () => {
    if (services) void requests.run(signal => services.useSkill(revision, itemKey, signal, resolve, undefined, onHandoff), onUsed);
  };
  if (renderAction) return <>{renderAction({ disabled: busy || !services, run })}{error && !onError && <span role="alert">{error}</span>}</>;
  return <span className="astron-sh-use-skill skillhub-controls"><Button size="sm" variant="default" disabled={busy || !services} onClick={run}>{busy ? '核验中…' : label}</Button>{error && !onError && <span role="alert">{error}</span>}</span>;
}
