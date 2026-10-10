import { readFile, rename, rm, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import { lock } from 'proper-lockfile'
import { joinPath, userStateDir, ensureDir, pathExists } from '../platform/paths'

export interface InventoryTarget {
  agent: string
  rootDir: string
  installDir: string
  installedAt: string
  /** Sources that own this exact target. Missing values inherit the legacy item-level sources. */
  installedBy?: string[]
}

export interface InventoryItem {
  registry: string
  namespace: string
  slug: string
  version: string
  fingerprint?: string
  /** Missing on legacy records and therefore interpreted as a direct install. */
  installedBy?: string[]
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
  return item.installedBy ?? ['direct']
}

export function targetInstalledBy(item: InventoryItem, target: InventoryTarget): string[] {
  return target.installedBy ?? installedBy(item)
}

function addDirectSource(item: InventoryItem, target: InventoryTarget): InventoryTarget {
  return {
    ...target,
    installedBy: Array.from(new Set([...targetInstalledBy(item, target), 'direct']))
  }
}

function refreshItemSources(item: InventoryItem): void {
  item.installedBy = Array.from(new Set(item.targets.flatMap(target => targetInstalledBy(item, target))))
}

export function installedSuites(inventory: Inventory): InventorySuite[] {
  return inventory.suites ?? []
}

export class InventoryVersionConflictError extends Error {
  constructor(readonly retainedTargets: InventoryTarget[]) {
    super('partial-target install would create inconsistent versions')
    this.name = 'InventoryVersionConflictError'
  }
}

export class InventoryStore {
  readonly path: string

  constructor(home?: string) {
    this.path = joinPath(userStateDir(home), 'inventory.json')
  }

  async read(): Promise<Inventory> {
    if (!(await pathExists(this.path))) return { items: [] }
    return JSON.parse(await readFile(this.path, 'utf-8')) as Inventory
  }

  async write(inventory: Inventory): Promise<void> {
    await ensureDir(dirname(this.path))
    await writeFile(this.path, JSON.stringify(inventory, null, 2))
  }

  async writeAtomic(inventory: Inventory): Promise<void> {
    await ensureDir(dirname(this.path))
    let release: (() => Promise<void>) | null = null
    try {
      release = await this.acquireLock()
      await this.writeUnderLock(inventory)
    } finally {
      if (release) await release().catch(() => {})
    }
  }

  async mutateAtomic<T>(mutate: (inventory: Inventory) => T): Promise<T> {
    await ensureDir(dirname(this.path))
    let release: (() => Promise<void>) | null = null
    try {
      release = await this.acquireLock()
      const inventory = await this.read()
      const result = mutate(inventory)
      await this.writeUnderLock(inventory)
      return result
    } finally {
      if (release) await release().catch(() => {})
    }
  }

  private async writeUnderLock(inventory: Inventory): Promise<void> {
    const payload = JSON.stringify(inventory, null, 2)
    JSON.parse(payload)
    const tmpPath = `${this.path}.${process.pid}.${Date.now()}.tmp`
    try {
      await writeFile(tmpPath, payload)
      JSON.parse(await readFile(tmpPath, 'utf-8'))
      await rename(tmpPath, this.path)
    } finally {
      await rm(tmpPath, { force: true }).catch(() => {})
    }
  }

  private acquireLock(): Promise<() => Promise<void>> {
    return lock(this.path, {
      lockfilePath: `${this.path}.lock`,
      realpath: false,
      stale: 30_000,
      update: 10_000,
      retries: {
        retries: 10,
        factor: 2,
        minTimeout: 100,
        maxTimeout: 1_000,
        randomize: true
      }
    })
  }

  async upsertTarget(
    registry: string,
    namespace: string,
    slug: string,
    version: string,
    target: InventoryTarget,
    fingerprint?: string
  ): Promise<void> {
    await this.mutateAtomic(inventory => {
      const existing = inventory.items.find(
        i => i.registry === registry && i.namespace === namespace && i.slug === slug
      )
      const item: InventoryItem = existing ?? { registry, namespace, slug, version, targets: [] }
      if (!existing) inventory.items.push(item)
      item.version = version
      if (fingerprint !== undefined) item.fingerprint = fingerprint
      const existingIdx = item.targets.findIndex(t => t.installDir === target.installDir)
      const previous = existingIdx >= 0 ? item.targets[existingIdx]! : target
      const next = addDirectSource(item, { ...target, installedBy: targetInstalledBy(item, previous) })
      if (existingIdx >= 0) item.targets[existingIdx] = next
      else item.targets.push(next)
      refreshItemSources(item)
    })
  }

  async removeTarget(registry: string, namespace: string, slug: string, installDir: string): Promise<boolean> {
    return this.mutateAtomic(inventory => {
      const item = inventory.items.find(i => i.registry === registry && i.namespace === namespace && i.slug === slug)
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
    target: InventoryTarget,
    fingerprint?: string
  ): Promise<void> {
    await this.mutateAtomic(inventory => {
      for (const item of inventory.items) {
        item.targets = item.targets.filter(existing => existing.installDir !== target.installDir)
      }
      inventory.items = inventory.items.filter(item => item.targets.length > 0)

      let item = inventory.items.find(candidate =>
        candidate.registry === registry && candidate.namespace === namespace && candidate.slug === slug)
      if (!item) {
        item = { registry, namespace, slug, version, installedBy: ['direct'], targets: [] }
        inventory.items.push(item)
      }
      item.version = version
      if (fingerprint !== undefined) item.fingerprint = fingerprint
      item.targets.push({ ...target, installedBy: ['direct'] })
      refreshItemSources(item)
    })
  }

  async replaceTargetsAtInstallDirs(
    registry: string,
    namespace: string,
    slug: string,
    version: string,
    targets: InventoryTarget[],
    fingerprint?: string,
    replacedInstallDirs: string[] = []
  ): Promise<void> {
    await this.mutateAtomic(inventory => {
      const installDirs = new Set([
        ...targets.map(target => target.installDir),
        ...replacedInstallDirs
      ])
      const existingItem = inventory.items.find(candidate =>
        candidate.registry === registry && candidate.namespace === namespace && candidate.slug === slug)
      const retainedTargets = existingItem?.targets.filter(target => !installDirs.has(target.installDir)) ?? []
      if (existingItem && retainedTargets.length > 0 &&
          (existingItem.version !== version || existingItem.fingerprint !== fingerprint)) {
        throw new InventoryVersionConflictError(retainedTargets)
      }

      for (const item of inventory.items) {
        item.targets = item.targets.filter(existing => !installDirs.has(existing.installDir))
      }
      inventory.items = inventory.items.filter(item => item.targets.length > 0)

      let item = inventory.items.find(candidate =>
        candidate.registry === registry && candidate.namespace === namespace && candidate.slug === slug)
      if (!item) {
        item = { registry, namespace, slug, version, installedBy: ['direct'], targets: [] }
        inventory.items.push(item)
      }
      item.version = version
      if (fingerprint !== undefined) item.fingerprint = fingerprint
      item.targets.push(...targets.map(target => ({ ...target, installedBy: ['direct'] })))
      refreshItemSources(item)
    })
  }
}
