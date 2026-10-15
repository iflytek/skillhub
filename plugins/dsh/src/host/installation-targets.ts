import { randomUUID } from 'node:crypto';
import { RemoteError } from '@deepseek-ai/dsh-typert-protocol';
import type { SelectionScope } from './conversation-selections.js';

export interface TargetContext extends SelectionScope { detailToken: string }
export interface ResolvedInstallationTarget {
  scope: 'user' | 'project';
  /** Canonical paths resolved by the Host's filesystem policy adapter. */
  root: string;
  dshHome: string;
}
interface TargetRecord { context: TargetContext; target: ResolvedInstallationTarget }

/** Private references only; accepting a reference never substitutes for execution-time path checks. */
export class InstallationTargets {
  private readonly records = new Map<string, TargetRecord>();

  clear(): void { this.records.clear(); }

  issue(context: TargetContext, target: ResolvedInstallationTarget): { targetToken: string; scope: 'user' | 'project' } {
    if (this.records.size >= 400) this.records.delete(this.records.keys().next().value!);
    const targetToken = randomUUID();
    this.records.set(targetToken, { context: { ...context }, target: { ...target } });
    return { targetToken, scope: target.scope };
  }

  async resolve(
    token: string,
    context: TargetContext,
    resolveCurrent: (scope: 'user' | 'project') => Promise<ResolvedInstallationTarget>,
  ): Promise<ResolvedInstallationTarget> {
    // Capture caller state before awaiting a Host filesystem resolution.
    const snapshot = { ...context };
    const record = this.lookup(token, snapshot);
    const current = await resolveCurrent(record.target.scope);
    // Source reload/disposal/eviction may have invalidated the reference during resolution.
    if (this.lookup(token, snapshot) !== record || current.scope !== record.target.scope
      || current.root !== record.target.root || current.dshHome !== record.target.dshHome) this.stale();
    return { ...current };
  }

  private lookup(token: string, context: TargetContext): TargetRecord {
    const record = this.records.get(token);
    if (!record || record.context.sessionId !== context.sessionId || record.context.cwd !== context.cwd
      || record.context.sourceRevision !== context.sourceRevision || record.context.detailToken !== context.detailToken) this.stale();
    return record;
  }

  private stale(): never {
    throw new RemoteError('skillhub/context-stale', 'Reopen the skill detail and choose the installation target again.', { retryable: false });
  }
}
