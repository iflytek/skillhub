import { z } from 'zod';
import { executePlanRequest } from '../host/install-request.js';
import type { HostExecutePlanRequest, NativeOperation } from '../host/types.js';

const savedRequest = z.object({ schemaVersion: z.literal(1), request: executePlanRequest,
  hostInstanceId: z.uuid(), operationId: z.uuid().optional() }).strict();
type RequestStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> & Partial<Pick<Storage, 'length' | 'key'>>;
const planKey = z.tuple([z.string().min(1), z.string().min(1), z.string().min(1), z.string().min(1), z.enum(['user', 'project'])]);
export function upgradeRequestKey(sessionId: string, source: string, item: { namespace: string; slug: string; scope: string }): string {
  return JSON.stringify([sessionId, source, item.namespace, item.slug, item.scope]);
}
export interface UpgradeAttempt {
  request: HostExecutePlanRequest; hostInstanceId: string; operationId?: string;
  state: 'submitting' | 'accepted' | 'unresolved'; operation: NativeOperation | null;
}

/** One explicit confirmation keeps its identity across response loss and browser reload. */
class PlanRequests {
  private readonly attempts = new Map<string, UpgradeAttempt>();
  private readonly pending = new Map<string, Promise<void>>();
  private readonly listeners = new Set<() => void>();
  private disposed = false;
  protected storageFailed = false;
  private revision = 0;
  getRevision = (): number => this.revision;
  constructor(protected readonly storage: () => RequestStorage, private readonly kind: 'upgrade' | 'removal') {}
  protected get prefix(): string { return `skillhub:${this.kind}:v1:`; }
  private get label(): string { return this.kind === 'upgrade' ? '升级' : '卸载'; }
  private accepts(operation: NativeOperation): boolean {
    return this.kind === 'upgrade' ? operation.kind === 'upgrade' : ['uninstall', 'remove-stale-record'].includes(operation.kind);
  }
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener); return () => { this.listeners.delete(listener); };
  };
  get(key: string): UpgradeAttempt | undefined {
    const cached = this.attempts.get(key);
    if (cached || this.disposed) return cached;
    try {
      const raw = this.storage().getItem(this.prefix + key);
      if (raw === null) return undefined;
      if (raw.length > 16384) throw new Error('Invalid saved plan request');
      const saved = savedRequest.parse(JSON.parse(raw));
      const attempt: UpgradeAttempt = { ...saved, operation: null, state: saved.operationId ? 'accepted' : 'unresolved' };
      this.attempts.set(key, attempt); return attempt;
    } catch { this.storageFailed = true; return undefined; }
  }
  submit(key: string, planId: string, hostInstanceId: string,
    send: (request: HostExecutePlanRequest) => Promise<NativeOperation | null>): Promise<void> {
    if (this.disposed) return Promise.reject(new Error(`${this.label}页面已关闭。`));
    const pending = this.pending.get(key);
    if (pending) return pending;
    const previous = this.get(key);
    if (this.storageFailed) return Promise.reject(new Error(`无法读取已保存的${this.label}请求，请先恢复浏览器存储。`));
    if (previous?.state === 'accepted') return Promise.resolve();
    const request = previous?.request ?? { planId, requestKey: crypto.randomUUID() };
    const host = previous?.hostInstanceId ?? hostInstanceId;
    try { this.save(key, request, host); }
    catch { return Promise.reject(new Error(`无法保存${this.label}请求，尚未提交。`)); }
    this.attempts.set(key, { request, hostInstanceId: host, state: 'submitting', operation: null });
    const task = Promise.resolve().then(async () => {
      try {
        const operation = await send({ ...request });
        if (this.disposed) return;
        if (operation && (!this.accepts(operation) || operation.context.hostInstanceId !== host)) throw new Error('Plan operation identity changed');
        this.attempts.set(key, { request, hostInstanceId: host, operation, operationId: operation?.operationId,
          state: operation ? 'accepted' : 'unresolved' });
        if (operation) { try { this.save(key, request, host, operation.operationId); } catch { /* Pre-send request remains recoverable. */ } }
      } catch {
        if (!this.disposed) this.attempts.set(key, { request, hostInstanceId: host, state: 'unresolved', operation: null });
      } finally { this.pending.delete(key); if (!this.disposed) this.notify(); }
    });
    this.pending.set(key, task); this.notify(); return task;
  }
  list(sessionId: string, source: string): Array<{ key: string; namespace: string; slug: string; scope: 'user' | 'project' }> {
    try {
      const storage = this.storage();
      if (typeof storage.length !== 'number' || typeof storage.key !== 'function') throw new Error('Storage enumeration unavailable');
      const result: Array<{ key: string; namespace: string; slug: string; scope: 'user' | 'project' }> = [];
      for (let index = 0; index < storage.length; index++) {
        const storedKey = storage.key(index);
        if (!storedKey?.startsWith(this.prefix)) continue;
        const key = storedKey.slice(this.prefix.length);
        const [session, registry, namespace, slug, scope] = planKey.parse(JSON.parse(key));
        if (session === sessionId && registry === source && this.get(key)) result.push({ key, namespace, slug, scope });
      }
      if (this.storageFailed) throw new Error('Invalid plan storage');
      return result;
    } catch {
      this.storageFailed = true;
      throw new Error(`无法读取已保存的${this.label}请求，请先恢复浏览器存储。`);
    }
  }
  dispose(): void { this.disposed = true; this.listeners.clear(); this.attempts.clear(); }
  /** Caller supplies a freshly queried terminal outcome; uncertain or recovery-required work cannot be dismissed. */
  closeResult(key: string, operation: NativeOperation): void {
    const attempt = this.get(key);
    if (this.disposed || this.pending.has(key) || !attempt?.operationId || attempt.operationId !== operation.operationId
      || attempt.hostInstanceId !== operation.context.hostInstanceId || !this.accepts(operation)
      || !(operation.phase === 'completed' || (operation.phase === 'failed' && ['restored', 'not-needed'].includes(operation.recovery)))) {
      throw new Error('操作结果尚未完成核验，不能清除原请求。');
    }
    this.storage().removeItem(this.prefix + key);
    this.attempts.delete(key); this.notify();
  }
  private notify(): void { this.revision++; for (const listener of this.listeners) listener(); }
  private save(key: string, request: HostExecutePlanRequest, hostInstanceId: string, operationId?: string): void {
    const value = savedRequest.parse({ schemaVersion: 1, request, hostInstanceId, ...(operationId ? { operationId } : {}) });
    this.storage().setItem(this.prefix + key, JSON.stringify(value));
  }
}

export class UpgradeRequests extends PlanRequests {
  constructor(storage: () => RequestStorage) { super(storage, 'upgrade'); }
}

/** Persisted coordinates keep removal results reachable after the installed row disappears. */
export class RemovalRequests extends PlanRequests {
  constructor(storage: () => RequestStorage & Pick<Storage, 'length' | 'key'>) {
    super(storage, 'removal');
  }
}
