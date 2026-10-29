import { assertEntryAbsent } from '../services/installed-skill-snapshot'
import { createHash } from 'node:crypto'
import { lstat, realpath } from 'node:fs/promises'
import { isAbsolute, join, resolve } from 'node:path'
import { z } from 'zod'
import { readInstallationState, type InventoryStore } from '../stores/inventory-store'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from '../services/installed-skill-metadata'

import type { InstalledProjection, InstalledProjectionItem, InstalledProjectionTarget } from './native-contract'
export type { InstalledProjection, InstalledProjectionItem, InstalledProjectionTarget } from './native-contract'

const text = z.string().min(1)
const itemSchema = z.object({ registry: text, namespace: text, slug: text, targets: z.array(z.object({
    installationState: z.enum(['enabled', 'disabled']).optional(), version: text, fingerprint: z.string().optional(), installedBy: z.array(text).optional(), agent: text, rootDir: text, installDir: text, installedAt: text,
  })) })

/** Read only the selected Registry's DSH records in Host-approved roots; never repair or hash all files. */
export async function readInstalledProjection(inventory: Pick<InventoryStore, 'read'>, registry: string,
  targets: readonly InstalledProjectionTarget[]): Promise<InstalledProjection> {
  let records: unknown[]
  try {
    const raw: unknown = await inventory.read()
    records = z.object({ items: z.array(z.unknown()) }).parse(raw).items
  } catch { return { items: [], complete: false, issues: ['inventory-unreadable'] } }
  return readInstalledProjectionRecords(records, registry, targets)
}

/** Internal projection primitive: callers must obtain records from a trusted inventory snapshot. */
export async function readInstalledProjectionRecords(records: readonly unknown[], registry: string,
  targets: readonly InstalledProjectionTarget[]): Promise<InstalledProjection> {
  const result: InstalledProjection = { items: [], complete: true, issues: [] }
  const issue = (code: InstalledProjection['issues'][number]) => {
    result.complete = false
    if (!result.issues.includes(code)) result.issues.push(code)
  }
  const roots = targets.map(target => {
    if (!isAbsolute(target.root)) throw new Error('Installed projection requires absolute Host roots')
    return { ...target, root: resolve(target.root) }
  })
  const seen = new Map<string, InstalledProjectionItem>()
  for (const record of records) {
    const parsed = itemSchema.safeParse(record)
    if (!parsed.success) { issue('invalid-record'); continue }
    const item = parsed.data
    if (!sameInstalledSkillSource(item, { registry, namespace: item.namespace, slug: item.slug })) continue
    if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(item.slug)) { issue('invalid-record'); continue }
    for (const entry of item.targets) {
      if (entry.agent !== 'dsh' || !isAbsolute(entry.rootDir)) continue
      for (const target of roots.filter(target => target.root === resolve(entry.rootDir))) {
        const path = join(target.root, item.slug)
        const projected: InstalledProjectionItem = { scope: target.scope, namespace: item.namespace,
          slug: item.slug, version: entry.version, ...(entry.fingerprint ? { fingerprint: entry.fingerprint } : {}), targetPath: path, state: 'present',
          recordRevision: createHash('sha256').update(JSON.stringify([item.registry.replace(/\/+$/, ''), item.namespace, item.slug,
            entry.installationState ?? 'enabled', entry.agent, entry.rootDir, entry.installDir, entry.version, entry.fingerprint ?? null, entry.installedAt,
            [...new Set(entry.installedBy ?? ['direct'])].sort()])).digest('hex') }
        const key = JSON.stringify([target.scope, path, item.namespace, item.slug])
        const previous = seen.get(key)
        if (previous) { previous.state = 'mismatch'; issue('invalid-record'); continue }
        seen.set(key, projected)
        result.items.push(projected)
        if (!isAbsolute(entry.installDir) || resolve(entry.installDir) !== path) {
          projected.state = 'mismatch'; issue('invalid-record'); continue
        }
        try {
          if (await realpath(target.root) !== target.root) throw new Error('Root changed')
          let directory
          try { directory = await lstat(path) }
          catch (error) {
            if ((error as NodeJS.ErrnoException).code === 'ENOENT') { projected.state = 'missing'; continue }
            throw error
          }
          if (!directory.isDirectory() || await realpath(path) !== path) {
            projected.state = 'mismatch'; issue('invalid-record'); continue
          }
          const installationState = await readInstallationState(path)
          if (!installationState) { projected.state = 'unreadable'; issue('target-unreadable'); continue }
          projected.installationState = installationState
          for (const [file, kind] of [[join(path, '.skillhub'), 'directory'], [join(path, '.skillhub/metadata.json'), 'file'], [join(path, installationState === 'disabled' ? 'SKILL.md.disabled' : 'SKILL.md'), 'file']] as const) {
            const stat = await lstat(file)
            if (kind === 'directory' ? !stat.isDirectory() : !stat.isFile()) throw new Error('Unexpected local file type')
          }
          await assertEntryAbsent(join(path, installationState === 'disabled' ? 'SKILL.md' : 'SKILL.md.disabled'))
          const metadata = await readInstalledSkillMetadata(path)
          if (metadata.status !== 'valid') { projected.state = 'metadata-invalid'; issue('target-unreadable'); continue }
          const local = metadata.metadata
          if (!sameInstalledSkillSource(local, item) || local.version !== entry.version || local.agent !== 'dsh'
            || local.installedAt !== entry.installedAt || local.fingerprint !== entry.fingerprint) {
            projected.state = 'mismatch'; issue('invalid-record')
          }
          if (await realpath(path) !== path) throw new Error('Target changed')
        } catch { projected.state = 'unreadable'; issue('target-unreadable') }
      }
    }
  }
  return result
}

/** A round-owned snapshot. Inventory identity is checked around every target projection. */
export async function openInstalledProjectionSnapshot(inventory: Pick<InventoryStore, 'read' | 'path'>,
  registry: string, targets: readonly InstalledProjectionTarget[]) {
  const identity = async () => {
    const stat = await lstat(inventory.path, { bigint: true }).catch(error => {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined
      throw error
    })
    if (!stat) return 'absent'
    if (!stat.isFile()) throw new Error('Inventory is not a regular file')
    return [stat.dev, stat.ino, stat.size, stat.mtimeNs, stat.ctimeNs].join(':')
  }
  const revision = await identity()
  const records = (await inventory.read()).items
  const assertCurrent = async () => {
    if (await identity() !== revision) throw new Error('Inventory changed during update scan')
  }
  await assertCurrent()
  const projection = await readInstalledProjectionRecords(records, registry, targets)
  await assertCurrent()
  const key = (item: Pick<InstalledProjectionItem, 'namespace' | 'slug' | 'scope'>) => JSON.stringify([item.namespace, item.slug, item.scope])
  const index = new Map<string, unknown[]>()
  for (const record of records) {
    for (const target of targets) {
      const entries = record.targets.filter(entry => isAbsolute(entry.rootDir) && resolve(entry.rootDir) === resolve(target.root) && entry.agent === 'dsh')
      if (!entries.length) continue
      const id = key({ ...record, scope: target.scope })
      const bucket = index.get(id) ?? []
      bucket.push({ ...record, targets: entries }); index.set(id, bucket)
    }
  }
  return { projection, async read(item: InstalledProjectionItem) {
    await assertCurrent()
    const result = await readInstalledProjectionRecords(index.get(key(item)) ?? [], registry, targets.filter(target => target.scope === item.scope))
    await assertCurrent()
    const current = result.items.find(value => value.targetPath === item.targetPath)
    if (!current || JSON.stringify(current) !== JSON.stringify(item)) throw new Error('Installed target changed during update scan')
    return current
  } }
}
