import { z } from 'zod'
import { inventoryItemSchema } from './transaction-journal'
import { join } from 'node:path'
import { isDeepStrictEqual } from 'node:util'
import type { OperationRecord } from './operation-store'
import { sameInstalledSkillSource } from '../services/installed-skill-metadata'

const state = z.enum(['enabled', 'disabled'])
export const toggleJournal = z.object({
  inventoryItem: inventoryItemSchema,
  target: z.object({ scope: z.enum(['user', 'project']), root: z.string(), path: z.string() }).strict(),
  files: z.record(z.string(), z.string()), beforeState: state, afterState: state,
  from: z.string(), to: z.string(),
  stage: z.enum(['intent', 'linked', 'moved', 'restoring', 'restore-linked', 'committed', 'restored'])
}).strict()
export type ToggleJournal = z.infer<typeof toggleJournal>

export function validateToggleJournal(record: OperationRecord, value: ToggleJournal): void {
  const item = value.inventoryItem
  const target = item.targets[0]
  if (record.schemaVersion !== 3 || !['enable', 'disable'].includes(record.operation.kind)
    || item.targets.length !== 1 || !target || target.agent !== 'dsh'
    || !sameInstalledSkillSource(item, { registry: record.operation.context.sourceIdentity,
      namespace: record.operation.namespace, slug: record.operation.slug })
    || target.version !== record.operation.version || !target.fingerprint
    || !isDeepStrictEqual(target.installedBy ?? ['direct'], ['direct'])
    || !isDeepStrictEqual(record.operation.targetPaths, [target.installDir])
    || target.installDir !== value.target.path || target.rootDir !== value.target.root
    || join(value.target.root, item.slug) !== value.target.path
    || (target.installationState ?? 'enabled') !== value.beforeState || value.beforeState === value.afterState
    || value.afterState !== (record.operation.kind === 'enable' ? 'enabled' : 'disabled')
    || value.from !== join(target.installDir, value.beforeState === 'enabled' ? 'SKILL.md' : 'SKILL.md.disabled')
    || value.to !== join(target.installDir, value.afterState === 'enabled' ? 'SKILL.md' : 'SKILL.md.disabled')
    || !value.files['SKILL.md'] || value.files['SKILL.md.disabled']) throw new Error('Invalid toggle evidence')
}
