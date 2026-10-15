import type { HostInstallRequest, NativeOperation } from '../host/types.js';
import { z } from 'zod';
import { installRequest } from '../host/install-request.js';

const storedRequest = z.object({ schemaVersion: z.literal(1), request: installRequest, operationId: z.uuid().optional() }).strict();
const storagePrefix = 'skillhub:installation:v1:';

export interface InstallationAttempt {
  request: HostInstallRequest;
  state: 'submitting' | 'accepted' | 'unresolved';
  operation: NativeOperation | null;
  operationId?: string;
}

/** Shared across surfaces: response loss retains the original request, never a fresh install key. */
export class InstallationRequests {
  private readonly attempts = new Map<string, InstallationAttempt>();
  private readonly pending = new Map<string, Promise<void>>();
  private readonly listeners = new Set<() => void>();
  private disposed = false;
  private storageFailed = false;

  constructor(private readonly storage?: () => Pick<Storage, 'getItem' | 'setItem'>) {}

  get(key: string): InstallationAttempt | undefined {
    const cached = this.attempts.get(key);
    if (cached || !this.storage || this.disposed) return cached;
    try {
      const raw = this.storage().getItem(storagePrefix + key);
      if (raw === null) return undefined;
      if (raw.length > 16384) throw new Error('Invalid saved request');
      const saved = storedRequest.parse(JSON.parse(raw));
      const attempt: InstallationAttempt = { request: saved.request, operation: null, operationId: saved.operationId,
        state: saved.operationId ? 'accepted' : 'unresolved' };
      this.attempts.set(key, attempt);
      return attempt;
    } catch { this.storageFailed = true; return undefined; }
  }
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };

  submit(key: string, input: Omit<HostInstallRequest, 'requestKey' | 'mode'>,
    send: (request: HostInstallRequest) => Promise<NativeOperation | null>, supersededOperationId?: string): Promise<void> {
    if (this.disposed) return Promise.reject(new Error('Installation view is unavailable'));
    const pending = this.pending.get(key);
    if (pending) return pending;
    const previous = this.get(key);
    if (this.storageFailed) return Promise.reject(new Error('无法读取浏览器保存的安装请求，暂不能提交安装。'));
    if (supersededOperationId && (previous?.state !== 'accepted' || previous.operationId !== supersededOperationId)) {
      return Promise.reject(new Error('原安装请求已变化，请重新核验当前状态。'));
    }
    if (previous?.state === 'accepted' && !supersededOperationId) return Promise.resolve();
    // Only an explicit new installation may supersede an accepted receipt; retries retain its key.
    const request: HostInstallRequest = previous && !supersededOperationId ? { ...previous.request, mode: 'retry' }
      : { ...input, requestKey: crypto.randomUUID(), mode: 'new' };
    try { this.save(key, request); }
    catch { return Promise.reject(new Error('无法保存安装请求，请检查浏览器存储后重试。')); }
    this.attempts.set(key, { request, state: 'submitting', operation: null });
    // Register before notifying subscribers or invoking transport; reentrant clicks share this task.
    const task = Promise.resolve().then(async () => {
      try {
        const operation = await send({ ...request });
        if (!this.disposed) {
          this.attempts.set(key, { request, operation, operationId: operation?.operationId,
            state: operation ? 'accepted' : 'unresolved' });
          if (operation) {
            try { this.save(key, request, operation.operationId); }
            catch { /* The pre-send request remains sufficient for a same-key retry after reload. */ }
          }
        }
      } catch {
        if (!this.disposed) this.attempts.set(key, { request, state: 'unresolved', operation: null });
      } finally {
        this.pending.delete(key);
        if (!this.disposed) this.notify();
      }
    });
    this.pending.set(key, task);
    this.notify();
    return task;
  }

  dispose(): void {
    this.disposed = true;
    this.listeners.clear();
    this.attempts.clear();
  }

  private notify(): void { for (const listener of this.listeners) listener(); }
  private save(key: string, request: HostInstallRequest, operationId?: string): void {
    this.storage?.().setItem(storagePrefix + key, JSON.stringify({ schemaVersion: 1, request, ...(operationId ? { operationId } : {}) }));
  }
}
