import { describe, expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import type { CatalogContext, InstalledSkillList, NativeOperation } from '../src/host/types.js';
import type { InstallResult } from '../src/client/install-result.js';
import { readReceiptInventory, resolveReceiptContext, receiptSessionGuard, type ReceiptReads } from '../src/client/receipt-context.js';
import { receiptInventoryIssue } from '../src/client/install-receipt-presentation.js';

function fixture() {
  const receipt: InstallResult = { schemaVersion: 1, kind: 'skillhub-install', status: 'accepted', toolCallId: 'call',
    operationId: randomUUID(), hostInstanceId: randomUUID(), sourceRevision: randomUUID(),
    namespace: 'demo', slug: 'writer', version: '1.2.0', targetLabel: '当前项目', hostLabel: 'DSH Host',
    sourceLabel: 'https://registry.example', createdAt: '2026-10-14T00:00:00Z' };
  let context: CatalogContext = { configured: true, registryUrl: receipt.sourceLabel, sourceRevision: randomUUID(),
    detailTabs: { versions: 'auto', reviews: 'auto', securityAudit: 'auto' } };
  let operation: NativeOperation | null = { operationId: receipt.operationId, revision: 1, kind: 'install',
    namespace: receipt.namespace, slug: receipt.slug, version: receipt.version, targetLabel: receipt.targetLabel,
    context: { sourceIdentity: receipt.sourceLabel, sourceLabel: receipt.sourceLabel, sourceRevision: receipt.sourceRevision,
      hostInstanceId: receipt.hostInstanceId, hostLabel: receipt.hostLabel, createdAt: receipt.createdAt,
      updatedAt: receipt.createdAt, initiatedFrom: 'conversation' }, phase: 'completed', files: 'installed',
    recovery: 'not-needed', discovery: 'confirmed', warningCount: 0 };
  let current = true;
  let inventory: InstalledSkillList = { complete: true, unavailable: [], issues: [], items: [
    { namespace: receipt.namespace, slug: receipt.slug, version: receipt.version, scope: 'project', state: 'present', itemKey: 'fresh-item-key' },
  ] };
  const revisions: string[] = [];
  const scope = {}; let cwd = '/project-a';
  const guard = receiptSessionGuard(() => ({ scope: current ? scope : undefined, cwd }));
  const reads: ReceiptReads = {
    sessionGeneration: randomUUID(),
    assertCurrent: guard,
    context: async () => ({ ok: true, value: context }),
    operation: async (id, host) => { expect(id).toBe(receipt.operationId); expect(host).toBe(receipt.hostInstanceId); return { ok: true, value: operation }; },
    installed: async (revision, signal) => { expect(signal.aborted).toBe(false); revisions.push(revision); return { ok: true, value: inventory }; },
  };
  return { receipt, reads, revisions, signal: new AbortController().signal, get context() { return context; },
    set context(value: CatalogContext) { context = value; }, get operation() { return operation!; },
    set operation(value: NativeOperation | null) { operation = value; },
    get inventory() { return inventory; }, set inventory(value: InstalledSkillList) { inventory = value; },
    changeProject: () => { cwd = '/project-b'; }, closeSession: () => { current = false; } };
}

describe('read-only historical installation receipt reconnect', () => {
  test('same durable Host and Session may read the same canonical registry at its fresh revision', async () => {
    const f = fixture();
    const context = await resolveReceiptContext(f.reads, f.receipt, f.signal);
    expect(context.sourceRevision).not.toBe(f.receipt.sourceRevision);
    expect(await readReceiptInventory(f.reads, f.receipt, context, f.signal)).toEqual(f.inventory);
    expect(f.revisions).toEqual([context.sourceRevision]);
    expect(f.revisions).not.toContain(f.receipt.sourceRevision);
  });

  test('current inventory truthfully reports an upgrade or uninstall, without replaying installation', async () => {
    const f = fixture();
    f.inventory.items[0]!.version = '1.3.0';
    let inventory = await readReceiptInventory(f.reads, f.receipt, f.context, f.signal);
    expect(receiptInventoryIssue(f.receipt, inventory, false)).toContain('v1.3.0');
    f.inventory = { ...f.inventory, items: [] };
    inventory = await readReceiptInventory(f.reads, f.receipt, f.context, f.signal);
    expect(receiptInventoryIssue(f.receipt, inventory, false)).toContain('已无此技能');
    expect(Object.keys(f.reads).sort()).toEqual(['assertCurrent', 'context', 'installed', 'operation', 'sessionGeneration']);
  });

  test('different source, Host, closed/replaced Session, or inaccessible durable operation fails closed', async () => {
    for (const change of [
      (f: ReturnType<typeof fixture>) => { f.context = { ...f.context, registryUrl: 'https://other.example' }; },
      (f: ReturnType<typeof fixture>) => { f.operation!.context.hostInstanceId = randomUUID(); },
      (f: ReturnType<typeof fixture>) => f.closeSession(),
      (f: ReturnType<typeof fixture>) => f.changeProject(),
      (f: ReturnType<typeof fixture>) => { f.operation = null; },
    ]) {
      const f = fixture(); change(f);
      await expect(readReceiptInventory(f.reads, f.receipt, f.context, f.signal)).rejects.toThrow();
      expect(f.revisions).toEqual([]);
    }
  });

  test('all original receipt identity fields are validated against the durable operation', async () => {
    const changes: Partial<NativeOperation>[] = [{ operationId: randomUUID() }, { kind: 'upgrade' },
      { namespace: 'other' }, { slug: 'other' }, { version: '9.0.0' }, { targetLabel: '当前用户' }, { phase: 'accepted' }];
    for (const change of changes) {
      const f = fixture(); f.operation = { ...f.operation!, ...change };
      await expect(resolveReceiptContext(f.reads, f.receipt, f.signal)).rejects.toThrow();
    }
    for (const change of [{ sourceIdentity: 'https://other.example' }, { sourceRevision: randomUUID() }]) {
      const f = fixture(); f.operation!.context = { ...f.operation!.context, ...change };
      await expect(resolveReceiptContext(f.reads, f.receipt, f.signal)).rejects.toThrow();
    }
  });

  test('source revision changing during durable-operation resolution is discarded, including A to B to A', async () => {
    const f = fixture(); const original = f.reads.operation;
    f.reads.operation = async (...args) => { const response = await original(...args); f.context = { ...f.context, sourceRevision: randomUUID() }; return response; };
    await expect(resolveReceiptContext(f.reads, f.receipt, f.signal)).rejects.toThrow();
    expect(f.revisions).toEqual([]);
  });

  test('late inventory is discarded after source change, Session replacement, Host replacement or cancellation', async () => {
    for (const change of [
      (f: ReturnType<typeof fixture>) => { f.context = { ...f.context, sourceRevision: randomUUID() }; },
      (f: ReturnType<typeof fixture>) => f.closeSession(),
      (f: ReturnType<typeof fixture>) => f.changeProject(),
      (f: ReturnType<typeof fixture>) => { f.operation!.context.hostInstanceId = randomUUID(); },
      (_f: ReturnType<typeof fixture>, controller: AbortController) => controller.abort(),
    ]) {
      const f = fixture(); const controller = new AbortController(); const original = f.reads.installed;
      f.reads.installed = async (...args) => { const response = await original(...args); change(f, controller); return response; };
      await expect(readReceiptInventory(f.reads, f.receipt, f.context, controller.signal)).rejects.toThrow();
    }
  });

  test('never substitutes a newer revision for a previously resolved inventory query key', async () => {
    const f = fixture(); const expected = f.context;
    f.context = { ...f.context, sourceRevision: randomUUID() };
    await expect(readReceiptInventory(f.reads, f.receipt, expected, f.signal)).rejects.toThrow();
    expect(f.revisions).toEqual([]);
  });
});
