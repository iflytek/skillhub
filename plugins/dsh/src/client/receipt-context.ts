import type { RemoteResult } from '@deepseek-ai/dsh-typert-protocol';
import type { CatalogContext, InstalledSkillList, NativeOperation } from '../host/types.js';
import type { InstallResult } from './install-result.js';

export interface ReceiptReads {
  sessionGeneration: string;
  context(): Promise<RemoteResult<CatalogContext>>;
  operation(id: string, host: string): Promise<RemoteResult<NativeOperation | null>>;
  installed(revision: string, signal: AbortSignal): Promise<RemoteResult<InstalledSkillList>>;
  /** Captures the exact controller-owned Session generation, not just its id. */
  assertCurrent(): void;
}

/** Project identity can change while the controller retains the same AgentContext object. */
export function receiptSessionGuard(current: () => { scope: object | undefined; cwd: string | undefined }): () => void {
  const captured = current();
  return () => {
    const now = current();
    if (!captured.scope || !captured.cwd || now.scope !== captured.scope || now.cwd !== captured.cwd) {
      throw new Error('当前会话或项目已变化。');
    }
  };
}

function read<T>(response: RemoteResult<T>): T {
  if (!response.ok) throw new Error('当前安装记录无法核验，来源或会话可能已变化。');
  return response.value;
}

export function matchesReceipt(operation: NativeOperation | null | undefined, result: InstallResult): boolean {
  return !!operation && operation.operationId === result.operationId && operation.kind === 'install'
    && operation.context.hostInstanceId === result.hostInstanceId && operation.namespace === result.namespace
    && operation.slug === result.slug && operation.version === result.version
    && operation.context.sourceIdentity === result.sourceLabel && operation.context.sourceRevision === result.sourceRevision
    && operation.targetLabel === result.targetLabel;
}

/** Durable receipts authorize only a fresh read, never revive expired selections or write references. */
export async function resolveReceiptContext(reads: ReceiptReads, result: InstallResult, signal: AbortSignal): Promise<CatalogContext> {
  const check = () => { signal.throwIfAborted(); reads.assertCurrent(); };
  check();
  const before = read(await reads.context());
  check();
  if (!before.configured || before.registryUrl !== result.sourceLabel) throw new Error('技能来源已变化。');
  const operation = read(await reads.operation(result.operationId, result.hostInstanceId));
  check();
  if (!matchesReceipt(operation, result) || !(operation!.phase === 'completed'
    || (operation!.phase === 'failed' && ['not-needed', 'restored'].includes(operation!.recovery)))) {
    throw new Error('原安装回执无法核验。');
  }
  const context = read(await reads.context());
  check();
  if (!context.configured || context.registryUrl !== result.sourceLabel || context.sourceRevision !== before.sourceRevision) throw new Error('技能来源已变化。');
  return context;
}

export async function readReceiptInventory(reads: ReceiptReads, result: InstallResult, expected: CatalogContext,
  signal: AbortSignal): Promise<InstalledSkillList> {
  const current = await resolveReceiptContext(reads, result, signal);
  if (current.sourceRevision !== expected.sourceRevision) throw new Error('技能来源已变化，请重新核验。');
  const inventory = read(await reads.installed(current.sourceRevision, signal));
  signal.throwIfAborted(); reads.assertCurrent();
  const after = await resolveReceiptContext(reads, result, signal);
  signal.throwIfAborted(); reads.assertCurrent();
  if (after.sourceRevision !== current.sourceRevision || after.registryUrl !== current.registryUrl) throw new Error('技能来源已变化。');
  return inventory;
}
