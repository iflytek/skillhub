import { randomUUID } from 'node:crypto';
import { RemoteError } from '@deepseek-ai/dsh-typert-protocol';
import type { ConversationSearchContext } from './search-tool.js';

export interface SelectionScope { sessionId: string; cwd: string; sourceRevision: string }
export interface SelectionSnapshot {
  namespace: string; slug: string; version: string; versionId: number; fingerprint: string;
}
type Candidate = SelectionScope & { callId: string; namespace: string; slug: string; version: string };
type Verified = { detailToken: string; snapshot: SelectionSnapshot };
type Search = Candidate & { kind: 'search'; pending?: string; viewed?: string; identity?: string; ambiguous?: boolean; verified?: Verified; verifying?: Promise<Verified> };
type Fixed = Candidate & { kind: 'fixed'; parent: string; detailToken: string; snapshot: SelectionSnapshot; viewed: boolean };

/** Host-only references: opening a detail does not make it an acknowledged selection. */
export class ConversationSelections {
  private readonly records = new Map<string, Search | Fixed>();
  clear(): void { this.records.clear(); }

  add(context: ConversationSearchContext & { sourceRevision: string }, coordinate: Pick<SelectionSnapshot, 'namespace' | 'slug' | 'version'>): string {
    return this.put({ ...context, ...coordinate, kind: 'search' });
  }

  read(reference: string, scope: SelectionScope): Readonly<Candidate> {
    const { sessionId, cwd, sourceRevision, callId, namespace, slug, version } = this.lookup(reference, scope);
    return { sessionId, cwd, sourceRevision, callId, namespace, slug, version };
  }

  private lookup(reference: string, scope: SelectionScope): Search | Fixed {
    const record = this.records.get(reference);
    if (!record || record.sessionId !== scope.sessionId || record.cwd !== scope.cwd || record.sourceRevision !== scope.sourceRevision) this.stale();
    return record;
  }

  stage(reference: string, scope: SelectionScope, detailToken: string, snapshot: SelectionSnapshot): string {
    const candidate = this.lookup(reference, scope);
    const parentRef = candidate.kind === 'fixed' ? candidate.parent : reference;
    const parent = this.lookup(parentRef, scope);
    if (parent.kind !== 'search' || snapshot.namespace !== candidate.namespace || snapshot.slug !== candidate.slug || snapshot.version !== candidate.version) this.stale();
    const fixed = this.put({ sessionId: parent.sessionId, cwd: parent.cwd, callId: parent.callId,
      sourceRevision: parent.sourceRevision, namespace: snapshot.namespace, slug: snapshot.slug, version: snapshot.version,
      kind: 'fixed', parent: parentRef, detailToken, snapshot: { ...snapshot }, viewed: false });
    parent.pending = fixed;
    return fixed;
  }

  acknowledge(reference: string, scope: SelectionScope, detailToken: string): boolean {
    const candidate = this.lookup(reference, scope);
    const fixedRef = candidate.kind === 'search' ? candidate.pending : reference;
    if (!fixedRef) this.stale();
    const fixed = this.lookup(fixedRef, scope);
    if (fixed.kind !== 'fixed' || fixed.detailToken !== detailToken) this.stale();
    const parent = this.lookup(fixed.parent, scope);
    if (parent.kind !== 'search' || parent.pending !== fixedRef) this.stale();
    const identity = JSON.stringify([fixed.snapshot.version, fixed.snapshot.versionId, fixed.snapshot.fingerprint]);
    if (parent.identity !== undefined && parent.identity !== identity) parent.ambiguous = true;
    parent.identity = identity;
    parent.viewed = fixedRef;
    fixed.viewed = true;
    return true;
  }

  /** Installation adapters must use this exact snapshot and revalidate it before committing. */
  resolve(reference: string, scope: SelectionScope): { selectionRef: string; detailToken: string; snapshot: SelectionSnapshot } {
    const candidate = this.lookup(reference, scope);
    if (candidate.kind === 'search' && (candidate.ambiguous || !candidate.viewed)) this.stale();
    const fixedRef = candidate.kind === 'search' ? candidate.viewed! : reference;
    const fixed = this.lookup(fixedRef, scope);
    if (fixed.kind !== 'fixed' || !fixed.viewed) this.stale();
    this.lookup(fixed.parent, scope);
    return { selectionRef: fixedRef, detailToken: fixed.detailToken, snapshot: { ...fixed.snapshot } };
  }

  /** A direct install verifies a search candidate without pretending its detail was displayed. */
  async resolveForInstall(reference: string, scope: SelectionScope,
    verify: (candidate: Readonly<Candidate>) => Promise<Verified>): Promise<Verified> {
    const candidate = this.lookup(reference, scope);
    if (candidate.kind === 'fixed' || candidate.viewed || candidate.ambiguous) return this.resolve(reference, scope);
    if (!candidate.verifying && !candidate.verified) {
      candidate.verifying = verify(this.read(reference, scope));
    }
    try {
      const verified = candidate.verified ?? await candidate.verifying!;
      if (this.lookup(reference, scope) !== candidate) this.stale();
      // A detail acknowledged during the request takes precedence, including ambiguity checks.
      if (candidate.viewed || candidate.ambiguous) return this.resolve(reference, scope);
      if (verified.snapshot.namespace !== candidate.namespace || verified.snapshot.slug !== candidate.slug
        || verified.snapshot.version !== candidate.version || !verified.snapshot.fingerprint) this.stale();
      candidate.verified = { detailToken: verified.detailToken, snapshot: { ...verified.snapshot } };
      return { detailToken: verified.detailToken, snapshot: { ...verified.snapshot } };
    } finally {
      candidate.verifying = undefined;
    }
  }

  private put(record: Search | Fixed): string {
    if (this.records.size >= 400) this.records.delete(this.records.keys().next().value!);
    const reference = randomUUID();
    this.records.set(reference, record);
    return reference;
  }
  private stale(): never {
    throw new RemoteError('skillhub/context-stale', 'Reopen and select the skill detail in this conversation.', { retryable: false });
  }
}
