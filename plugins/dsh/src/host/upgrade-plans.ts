import { randomUUID } from 'node:crypto';
import { RemoteError } from '@deepseek-ai/dsh-typert-protocol';
import type { SelectionScope } from './conversation-selections.js';
import type { UpgradeBaseline, RemovalBaseline, ToggleBaseline } from './generated/native-contract.js';
import type { ResolvedSkill } from './registry-adapter.js';

export interface UpgradePlanSnapshot {
  baseline: Extract<UpgradeBaseline, { status: 'ready' }>;
  candidate: ResolvedSkill;
}
interface PlanRecord<Snapshot> {
  context: SelectionScope;
  snapshot: Snapshot;
  expiresAt: number;
  requestKey?: string;
}

/** Pending confirmations only. Accepted requests must be replayed from the durable operation store first. */
class ConfirmationPlans<Snapshot> {
  private readonly records = new Map<string, PlanRecord<Snapshot>>();
  constructor(private readonly now: () => number = Date.now) {}

  clear(): void { this.records.clear(); }
  has(planId: string): boolean { return this.records.has(planId); }

  issue(context: SelectionScope, snapshot: Snapshot): { planId: string; expiresAt: string } {
    const now = this.now();
    for (const [key, record] of this.records) if (record.expiresAt <= now) this.records.delete(key);
    if (this.records.size >= 400) this.records.delete(this.records.keys().next().value!);
    const planId = randomUUID();
    const expiresAt = now + 5 * 60_000;
    this.records.set(planId, { context: { ...context }, snapshot: structuredClone(snapshot), expiresAt });
    return { planId, expiresAt: new Date(expiresAt).toISOString() };
  }

  read(planId: string, context: SelectionScope): Snapshot {
    return structuredClone(this.lookup(planId, context).snapshot);
  }

  /** Bind this confirmation to one durable request key before any asynchronous acceptance. */
  claim(planId: string, context: SelectionScope, requestKey: string): Snapshot {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(requestKey)) {
      throw new RemoteError('skillhub/invalid-installation-request', 'Invalid request key.', { retryable: false });
    }
    const record = this.lookup(planId, context);
    if (record.requestKey && record.requestKey !== requestKey) {
      throw new RemoteError('skillhub/request-conflict', 'This plan is bound to another request.', { retryable: false });
    }
    record.requestKey = requestKey;
    return structuredClone(record.snapshot);
  }

  private lookup(planId: string, context: SelectionScope): PlanRecord<Snapshot> {
    const record = this.records.get(planId);
    if (!record || record.expiresAt <= this.now() || record.context.sessionId !== context.sessionId
      || record.context.cwd !== context.cwd || record.context.sourceRevision !== context.sourceRevision) {
      throw new RemoteError('skillhub/context-stale', 'Prepare the operation plan again.', { retryable: false });
    }
    return record;
  }
}

export class UpgradePlans extends ConfirmationPlans<UpgradePlanSnapshot> {}
export class RemovalPlans extends ConfirmationPlans<Extract<RemovalBaseline, { status: 'ready' }>> {}

export class TogglePlans extends ConfirmationPlans<Extract<ToggleBaseline, { status: 'ready' }>> {}
