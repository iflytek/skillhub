import { inventoryMemorySchema, inventoryRecordSchema, legacyInventorySchema, parseInventory, type Inventory, type InventoryItem } from '../stores/inventory-store'
import { z } from 'zod'
import type { InstallTransactionObserver } from '../services/install-transaction'
import type { OperationStore } from './operation-store'

const text = z.string().min(1).max(2048)
const source = text.refine(value => {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password && !url.search && !url.hash }
  catch { return false }
}, 'Expected a credential-free registry identity')
// Legacy snapshots remain raw on disk; normalization is only the recovery read projection.
const inventory = z.union([inventoryMemorySchema, legacyInventorySchema.transform(value => parseInventory(value))])
  .transform(value => value as Inventory)
  .refine(value => [...value.items, ...value.suites ?? []].every(item => source.safeParse(item.registry).success), 'Unsafe snapshot source')
const item = z.union([inventoryRecordSchema, legacyInventorySchema.shape.items.element.transform(value => parseInventory({ items: [value] }).items[0]!)])
  .transform(value => value as InventoryItem)
  .refine(value => source.safeParse(value.registry).success, 'Unsafe snapshot source')
const move = z.object({ phase: z.enum(['backup', 'install']), index: z.number().int().nonnegative(), agent: text,
  canonicalSkillDir: text, stagingDir: text, backupDir: text.nullable(), from: text, to: text }).strict()
const commit = z.object({ before: inventory, after: inventory }).strict()
export const inventorySnapshotSchema = inventory
export const inventoryItemSchema = item
const cleanup = z.object({ path: text, removed: z.boolean(), error: text.optional() }).strict()
const staging = z.object({ canonicalSkillDir: text, rootDir: text, stagingDir: text }).strict()
const preparation = z.object({ before: inventory,
  release: z.object({ registry: source, namespace: text, slug: text, version: text, fingerprint: text }).strict(),
  targets: z.array(z.object({ agent: text, canonicalSkillDir: text, skillDir: text, stagingDir: text, installedAt: text }).strict()).min(1).max(100)
}).strict()
export const recoveryAction = z.object({ kind: z.enum(['return-staging', 'restore-backup', 'remove-staging']),
  index: z.number().int().nonnegative(), from: text, to: text.nullable() }).strict()
export type RecoveryAction = z.infer<typeof recoveryAction>
export const transactionEvent = z.discriminatedUnion('type', [
  z.object({ type: z.literal('beforeStaging'), staging }).strict(),
  z.object({ type: z.literal('beforePreparation'), preparation }).strict(),
  z.object({ type: z.literal('beforeMove'), move }).strict(),
  z.object({ type: z.literal('afterMove'), move }).strict(),
  z.object({ type: z.literal('beforeInventoryCommit'), commit }).strict(),
  z.object({ type: z.literal('afterInventoryCommit') }).strict(),
  z.object({ type: z.literal('cleanupResult'), cleanup }).strict()
])
export const transactionState = z.object({
  staging: z.array(staging).max(100).optional(),
  preparation: preparation.optional(),
  recoveryActions: z.array(z.object({ action: recoveryAction, completed: z.boolean() }).strict()).max(300).optional(),
  moves: z.array(z.object({ move, completed: z.boolean() }).strict()).max(200),
  inventoryCommit: commit.extend({ committed: z.boolean() }).optional(),
  cleanup: z.array(cleanup).max(100)
}).strict()
export type TransactionEvent = z.infer<typeof transactionEvent>
export type TransactionState = z.infer<typeof transactionState>

/** Bind the shared core's awaited hooks to one already-started operation, never execute the operation. */
export async function createInstallTransactionObserver(store: OperationStore, operationId: string, ownerId: string): Promise<InstallTransactionObserver> {
  const initial = await store.get(operationId, ownerId)
  if (!initial?.operation.targetPaths || !['install', 'upgrade'].includes(initial.operation.kind) || initial.operation.status.phase !== 'staging') {
    throw new Error('Installation journal requires an owned operation in staging')
  }
  let revision = initial.revision
  let prepared = false
  const append = async (event: TransactionEvent) => {
    const current = await store.recordTransaction(operationId, ownerId, revision, event)
    revision = current.revision
  }
  return {
    beforeTargetMutation: targets => store.assertTargetsAvailable(targets, { operationId, ownerId }),
    beforeStaging: staging => append({ type: 'beforeStaging', staging }),
    beforePreparation: async preparation => {
      await append({ type: 'beforePreparation', preparation })
      prepared = true
    },
    beforeMove: move => {
      if (!prepared) throw new Error('Installation preparation must be persisted before moving files')
      return append({ type: 'beforeMove', move })
    },
    afterMove: move => append({ type: 'afterMove', move }),
    beforeInventoryCommit: commit => append({ type: 'beforeInventoryCommit', commit }),
    afterInventoryCommit: () => append({ type: 'afterInventoryCommit' }),
    cleanupResult: cleanup => append({ type: 'cleanupResult', cleanup })
  }
}
