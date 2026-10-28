import { createHash } from 'node:crypto'
import { z } from 'zod'
import { isDeepStrictEqual } from 'node:util'
import { lstat, open, readFile, rename, rm } from 'node:fs/promises'
import { dirname, isAbsolute, relative, resolve } from 'node:path'
import { lock } from 'proper-lockfile'
import { acquireAcquisitionGate } from '../services/skill-target-lock'
import { canonicalizeExistingPath, pathsOverlap, joinPath, userStateDir, ensureDir } from '../platform/paths'

export interface InventoryTarget {
  installationState?: 'enabled' | 'disabled'
  agent: string
  rootDir: string
  installDir: string
  installedAt: string
  version: string
  fingerprint?: string
  /** Sources owning this position; legacy inheritance is expanded by parseInventory. */
  installedBy?: string[]
}

export interface InventoryItem {
  registry: string
  namespace: string
  slug: string
  targets: InventoryTarget[]
}

export interface InventorySuiteMember {
  namespace: string
  slug: string
  version: string
  fingerprint: string
  installDirs: string[]
}

export interface InventorySuite {
  registry: string
  namespace: string
  slug: string
  version: string
  fingerprint: string
  members: InventorySuiteMember[]
}

export interface Inventory {
  items: InventoryItem[]
  suites?: InventorySuite[]
}

export function installedBy(item: InventoryItem): string[] {
  return [...new Set(item.targets.flatMap(target => target.installedBy ?? ['direct']))]
}

export function targetInstalledBy(item: InventoryItem, target: InventoryTarget): string[] {
  void item
  return target.installedBy ?? ['direct']
}

function addDirectSource(item: InventoryItem, target: InventoryTarget): InventoryTarget {
  return {
    ...target,
    installedBy: Array.from(new Set([...targetInstalledBy(item, target), 'direct']))
  }
}

export function installedSuites(inventory: Inventory): InventorySuite[] {
  return inventory.suites ?? []
}

export class InventoryCommitUnknownError extends Error {
  constructor(cause: unknown) {
    super('inventory commit outcome could not be verified', { cause })
    this.name = 'InventoryCommitUnknownError'
  }
}

const text = z.string().min(1)
const targetShape = { agent: text, rootDir: text, installDir: text, installedAt: text, installedBy: z.array(text).optional() }
const identity = { registry: text, namespace: text, slug: text }
const suiteSchema = z.object({ ...identity, version: text, fingerprint: text, members: z.array(z.object({
  namespace: text, slug: text, version: text, fingerprint: text, installDirs: z.array(text)
}).strict()) }).strict()
const v2TargetSchema = z.object({ ...targetShape, version: text, fingerprint: text.optional() }).strict()
export const inventoryTargetSchema = v2TargetSchema.extend({ installationState: z.enum(['enabled', 'disabled']).optional() }).strict()
const v3TargetSchema = inventoryTargetSchema.extend({ installationState: z.enum(['enabled', 'disabled']) })
export const inventoryRecordSchema = z.object({ ...identity, targets: z.array(inventoryTargetSchema) }).strict()
export const inventoryMemorySchema = z.object({ items: z.array(inventoryRecordSchema), suites: z.array(suiteSchema).optional() }).strict()
export const legacyInventorySchema = z.object({ items: z.array(z.object({ ...identity, version: text,
  fingerprint: text.optional(), installedBy: z.array(text).optional(), targets: z.array(z.object(targetShape).strict()) }).strict()),
  suites: z.array(suiteSchema).optional() }).strict()
const inventoryDiskSchema = z.object({ schemaVersion: z.literal(2), records: z.array(z.object({ ...identity, targets: z.array(v2TargetSchema) }).strict()),
  suites: z.array(suiteSchema).optional() }).strict()

const inventoryV3Schema = z.object({ schemaVersion: z.literal(3), records: z.array(z.object({ ...identity, targets: z.array(v3TargetSchema) }).strict()), suites: z.array(suiteSchema).optional() }).strict()

/** A legacy read is a projection only. Missing integrity evidence is never synthesized. */
export function parseInventory(value: unknown): Inventory {
  if (typeof value !== 'object' || value === null) throw new Error('Invalid inventory')
  if ('schemaVersion' in value) {
    const parsed = (value.schemaVersion === 3 ? inventoryV3Schema : inventoryDiskSchema).parse(value)
    return { items: parsed.records.map(item => ({ ...item, targets: item.targets.map(target => ({ installationState: 'enabled' as const, ...target })) })), ...(parsed.suites === undefined ? {} : { suites: parsed.suites }) } as Inventory
  }
  const legacy = legacyInventorySchema.parse(value)
  return { items: legacy.items.map(item => ({ registry: item.registry, namespace: item.namespace, slug: item.slug,
    targets: item.targets.map(target => ({ ...target, installationState: 'enabled', version: item.version,
      ...(item.fingerprint === undefined ? {} : { fingerprint: item.fingerprint }),
      installedBy: target.installedBy ?? item.installedBy ?? ['direct'] })) })),
    ...(legacy.suites === undefined ? {} : { suites: legacy.suites }) } as Inventory
}

/** Validate ownership aliases before writing; content of unselected skills is not inspected. */
async function validateInventoryClaims(inventory: Inventory): Promise<void> {
  const sources = new Set<string>()
  const paths = new Set<string>()
  for (const item of inventory.items) {
    const source = JSON.stringify([item.registry.replace(/\/+$/, ''), item.namespace, item.slug])
    if (sources.has(source)) throw new Error('Duplicate inventory source identity')
    sources.add(source)
    for (const target of item.targets) {
      if (!isAbsolute(target.rootDir) || !isAbsolute(target.installDir)) throw new Error('Inventory target paths must be absolute')
      const rel = relative(resolve(target.rootDir), resolve(target.installDir))
      if (!rel || rel === '..' || rel.startsWith('../') || rel.startsWith('..\\') || isAbsolute(rel)) throw new Error('Inventory target is outside its root')
      const canonical = await canonicalizeExistingPath(target.installDir)
      if ([...paths].some(path => pathsOverlap(path, canonical))) throw new Error('Ambiguous inventory target ownership')
      paths.add(canonical)
    }
  }
}

export class InventoryStore {
  readonly path: string

  constructor(home?: string) {
    this.path = joinPath(userStateDir(home), 'inventory.json')
  }

  async read(): Promise<Inventory> {
    try { return parseInventory(JSON.parse(await readFile(this.path, 'utf-8'))) }
    catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return { items: [] }
      throw error
    }
  }

  async write(inventory: Inventory): Promise<void> { await this.writeAtomic(inventory) }

  /** Call before any file mutation. Migration lock is released before target locks. */
  async ensureWritable(): Promise<void> {
    await ensureDir(dirname(this.path))
    const release = await this.acquireLock()
    try { await this.migrateUnderLock() } finally { await release() }
  }

  async diskVersion(): Promise<1 | 2 | 3> {
    try {
      const value: unknown = JSON.parse(await readFile(this.path, 'utf8'))
      parseInventory(value)
      return typeof value === 'object' && value !== null && 'schemaVersion' in value ? value.schemaVersion as 2 | 3 : 1
    } catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return 3; throw error }
  }

  private async migrateUnderLock(): Promise<void> {
    let raw: Buffer
    try { raw = await readFile(this.path) } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') return
      throw error
    }
    const value: unknown = JSON.parse(raw.toString('utf8'))
    const inventory = parseInventory(value)
    await validateInventoryClaims(inventory)
    if (typeof value === 'object' && value !== null && 'schemaVersion' in value && value.schemaVersion === 3) return
    const digest = createHash('sha256').update(raw).digest('hex')
    const backup = joinPath(dirname(this.path), `inventory.v${typeof value === 'object' && value !== null && 'schemaVersion' in value ? 2 : 1}.${digest}.backup.json`)
    try {
      const file = await open(backup, 'wx', 0o600)
      try { await file.writeFile(raw); await file.sync() } finally { await file.close() }
    }
    catch (error) { if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error }
    const backupInfo = await lstat(backup)
    if (!backupInfo.isFile() || backupInfo.isSymbolicLink()) throw new Error('Unsafe inventory migration backup')
    if (!(await readFile(backup)).equals(raw)) throw new Error('Inventory migration backup mismatch')
    try { await this.writeUnderLock(inventory) }
    catch (error) {
      try {
        if (await this.diskVersion() === 3 && isDeepStrictEqual(await this.read(), inventory)) return
      } catch { /* Preserve the original migration failure. */ }
      throw error
    }
  }

  async writeAtomic(inventory: Inventory): Promise<void> {
    await ensureDir(dirname(this.path))
    let release: (() => Promise<void>) | null = null
    try {
      release = await this.acquireLock()
      await this.migrateUnderLock()
      await this.writeUnderLock(inventory)
    } finally {
      if (release) await release().catch(() => {})
    }
  }

  /** Hold the inventory lock while a lifecycle reader verifies files and persists its journal result. */
  async withLockedSnapshot<T>(inspect: (inventory: Inventory) => Promise<T>): Promise<T> {
    await ensureDir(dirname(this.path))
    const release = await this.acquireLock()
    try { return await inspect(await this.read()) }
    finally { await release() }
  }

  async mutateAtomic<T>(
    mutate: (inventory: Inventory) => T,
    beforeCommit?: (before: Inventory, after: Inventory) => Promise<void>
  ): Promise<T> {
    await ensureDir(dirname(this.path))
    let release: (() => Promise<void>) | null = null
    try {
      release = await this.acquireLock()
      await this.migrateUnderLock()
      const inventory = await this.read()
      const before = structuredClone(inventory)
      const result = mutate(inventory)
      for (const item of inventory.items) for (const target of item.targets) target.installationState ??= 'enabled'
      if (beforeCommit) await beforeCommit(structuredClone(before), structuredClone(inventory))
      try { await this.writeUnderLock(inventory) }
      catch (writeError) {
        let actual: Inventory
        try { actual = await this.read() }
        catch (readError) { throw new InventoryCommitUnknownError(new AggregateError([writeError, readError])) }
        // Still holding the inventory lock: a failed transport response is not proof of failed rename.
        if (isDeepStrictEqual(actual, inventory)) return result
        if (!isDeepStrictEqual(actual, before)) throw new InventoryCommitUnknownError(writeError)
        throw writeError
      }
      return result
    } finally {
      if (release) await release().catch(() => {})
    }
  }

  protected async writeUnderLock(inventory: Inventory): Promise<void> {
    inventoryMemorySchema.parse(inventory)
    await validateInventoryClaims(inventory)
    const payload = JSON.stringify(inventoryV3Schema.parse({ schemaVersion: 3, records: inventory.items.map(item => ({ ...item, targets: item.targets.map(target => ({ ...target, installationState: target.installationState ?? 'enabled' })) })), suites: inventory.suites }), null, 2)
    parseInventory(JSON.parse(payload))
    const tmpPath = `${this.path}.${process.pid}.${Date.now()}.tmp`
    try {
      const file = await open(tmpPath, 'wx', 0o600)
      try { await file.writeFile(payload); await file.sync() } finally { await file.close() }
      parseInventory(JSON.parse(await readFile(tmpPath, 'utf-8')))
      await rename(tmpPath, this.path)
      if (process.platform !== 'win32') {
        const directory = await open(dirname(this.path), 'r')
        try { await directory.sync() } finally { await directory.close() }
      }
    } finally {
      await rm(tmpPath, { force: true }).catch(() => {})
    }
  }

  private async acquireLock(): Promise<() => Promise<void>> {
    // proper-lockfile stale stat/remove is not atomic: serialize acquisition so a delayed
    // recoverer cannot remove another process's newly acquired inventory lock.
    const deadline = Date.now() + 10_000
    const releaseGate = await acquireAcquisitionGate(`${this.path}.lock.acquire`, 10_000)
    let release: () => Promise<void>
    try {
      const remaining = deadline - Date.now()
      if (remaining <= 0) throw Object.assign(new Error('Inventory lock acquisition timed out'), { code: 'ELOCKED' })
      release = await lock(this.path, {
        lockfilePath: `${this.path}.lock`,
        realpath: false,
        stale: 30_000,
        update: 10_000,
        retries: {
          retries: 10,
          maxRetryTime: remaining,
          factor: 2,
          minTimeout: 100,
          maxTimeout: 1_000,
          randomize: true
        }
      })
    } catch (operationError) {
      try { await releaseGate() }
      catch (cleanupError) { throw new AggregateError([operationError, cleanupError], 'inventory acquisition and gate cleanup both failed') }
      throw operationError
    }
    try { await releaseGate() }
    catch (gateError) {
      try { await release() }
      catch (cleanupError) { throw new AggregateError([gateError, cleanupError], 'inventory and gate cleanup both failed') }
      throw gateError
    }
    return release
  }

  async upsertTarget(
    registry: string,
    namespace: string,
    slug: string,
    version: string,
    target: Omit<InventoryTarget, 'version' | 'fingerprint'>,
    fingerprint?: string
  ): Promise<void> {
    await this.mutateAtomic(inventory => {
      const existing = inventory.items.find(
        i => i.registry.replace(/\/+$/, '') === registry.replace(/\/+$/, '') && i.namespace === namespace && i.slug === slug
      )
      const item: InventoryItem = existing ?? { registry, namespace, slug, targets: [] }
      if (!existing) inventory.items.push(item)
      const existingIdx = item.targets.findIndex(t => t.installDir === target.installDir)
      const previous = existingIdx >= 0 ? item.targets[existingIdx]! : { ...target, version, ...(fingerprint === undefined ? {} : { fingerprint }) }
      const next = addDirectSource(item, { ...target, version, ...(fingerprint === undefined ? {} : { fingerprint }), installedBy: targetInstalledBy(item, previous) })
      if (existingIdx >= 0) item.targets[existingIdx] = next
      else item.targets.push(next)
    })
  }

  async removeTarget(registry: string, namespace: string, slug: string, installDir: string): Promise<boolean> {
    return this.mutateAtomic(inventory => {
      const item = inventory.items.find(i => i.registry.replace(/\/+$/, '') === registry.replace(/\/+$/, '') && i.namespace === namespace && i.slug === slug)
      if (!item) return false
      const idx = item.targets.findIndex(t => t.installDir === installDir)
      if (idx < 0) return false
      item.targets.splice(idx, 1)
      if (item.targets.length === 0) inventory.items = inventory.items.filter(i => i !== item)
      return true
    })
  }

  async removeTargetsByInstallDir(installDir: string): Promise<number> {
    return this.mutateAtomic(inventory => {
      let removed = 0
      for (const item of inventory.items) {
        const before = item.targets.length
        item.targets = item.targets.filter(t => t.installDir !== installDir)
        removed += before - item.targets.length
      }
      inventory.items = inventory.items.filter(item => item.targets.length > 0)
      return removed
    })
  }

  async replaceTargetAtInstallDir(
    registry: string,
    namespace: string,
    slug: string,
    version: string,
    target: Omit<InventoryTarget, 'version' | 'fingerprint'>,
    fingerprint?: string
  ): Promise<void> {
    await this.mutateAtomic(inventory => {
      for (const item of inventory.items) {
        item.targets = item.targets.filter(existing => existing.installDir !== target.installDir)
      }
      inventory.items = inventory.items.filter(item => item.targets.length > 0)

      let item = inventory.items.find(candidate =>
        candidate.registry.replace(/\/+$/, '') === registry.replace(/\/+$/, '') && candidate.namespace === namespace && candidate.slug === slug)
      if (!item) {
        item = { registry, namespace, slug, targets: [] }
        inventory.items.push(item)
      }
      item.targets.push({ ...target, version, ...(fingerprint === undefined ? {} : { fingerprint }), installedBy: ['direct'] })
    })
  }

  async replaceTargetsAtInstallDirs(
    registry: string,
    namespace: string,
    slug: string,
    version: string,
    targets: Array<Omit<InventoryTarget, 'version' | 'fingerprint'>>,
    fingerprint?: string,
    replacedInstallDirs: string[] = [],
    beforeCommit?: (before: Inventory, after: Inventory) => Promise<void>
  ): Promise<void> {
    await this.mutateAtomic(inventory => {
      const installDirs = new Set([
        ...targets.map(target => target.installDir),
        ...replacedInstallDirs
      ])
      for (const item of inventory.items) {
        item.targets = item.targets.filter(existing => !installDirs.has(existing.installDir))
      }
      inventory.items = inventory.items.filter(item => item.targets.length > 0)

      let item = inventory.items.find(candidate =>
        candidate.registry.replace(/\/+$/, '') === registry.replace(/\/+$/, '') && candidate.namespace === namespace && candidate.slug === slug)
      if (!item) {
        item = { registry, namespace, slug, targets: [] }
        inventory.items.push(item)
      }
      item.targets.push(...targets.map(target => ({ ...target, version, ...(fingerprint === undefined ? {} : { fingerprint }), installedBy: ['direct'] })))
    }, beforeCommit)
  }
}
