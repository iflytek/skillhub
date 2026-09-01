import { open, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import { joinPath, userStateDir, ensureDir, pathExists } from '../platform/paths'

export interface InventoryTarget {
  agent: string
  rootDir: string
  installDir: string
  installedAt: string
}

export interface InventoryItem {
  registry: string
  namespace: string
  slug: string
  version: string
  fingerprint?: string
  targets: InventoryTarget[]
}

export interface Inventory {
  items: InventoryItem[]
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
    const lockPath = `${this.path}.lock`
    let lockHandle: Awaited<ReturnType<typeof open>> | null = null
    try {
      lockHandle = await this.acquireLock(lockPath)
      await this.writeUnderLock(inventory)
    } finally {
      if (lockHandle) {
        await lockHandle.close().catch(() => {})
        await rm(lockPath, { force: true }).catch(() => {})
      }
    }
  }

  private async mutateAtomic<T>(mutate: (inventory: Inventory) => T): Promise<T> {
    await ensureDir(dirname(this.path))
    const lockPath = `${this.path}.lock`
    let lockHandle: Awaited<ReturnType<typeof open>> | null = null
    try {
      lockHandle = await this.acquireLock(lockPath)
      const inventory = await this.read()
      const result = mutate(inventory)
      await this.writeUnderLock(inventory)
      return result
    } finally {
      if (lockHandle) {
        await lockHandle.close().catch(() => {})
        await rm(lockPath, { force: true }).catch(() => {})
      }
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

  private async acquireLock(lockPath: string, maxRetries = 10, retryDelayMs = 100): Promise<Awaited<ReturnType<typeof open>>> {
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        // Try to create lock file with PID and timestamp
        const lockHandle = await open(lockPath, 'wx')
        const lockData = JSON.stringify({ pid: process.pid, timestamp: Date.now() })
        await writeFile(lockPath, lockData)
        return lockHandle
      } catch (err) {
        if (err instanceof Error && 'code' in err && err.code !== 'EEXIST') throw err

        // Lock exists, check if it's stale (older than 30 seconds)
        // 30s threshold chosen to balance between:
        // - Allowing slow operations to complete (e.g., large inventory writes)
        // - Recovering quickly from crashed processes
        try {
          const lockContent = await readFile(lockPath, 'utf-8')
          const lockData = JSON.parse(lockContent) as { pid: number; timestamp: number }
          const ageMs = Date.now() - lockData.timestamp

          if (ageMs > 30000) {
            // Stale lock detected - verify the process is actually dead
            try {
              // process.kill(pid, 0) throws if process doesn't exist
              process.kill(lockData.pid, 0)
              // Process still alive, wait and retry
            } catch {
              // Process is dead, safe to remove stale lock
              await rm(lockPath, { force: true }).catch(() => {})
              continue
            }
          }
        } catch {
          // Lock file disappeared or corrupted, retry
          continue
        }

        // Lock is held by another active process, wait and retry with exponential backoff
        if (attempt < maxRetries - 1) {
          await new Promise(resolve => setTimeout(resolve, retryDelayMs * Math.pow(2, attempt)))
        }
      }
    }
    throw new Error(`Failed to acquire lock after ${maxRetries} attempts`)
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
      if (existingIdx >= 0) item.targets[existingIdx] = target
      else item.targets.push(target)
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
        item = { registry, namespace, slug, version, targets: [] }
        inventory.items.push(item)
      }
      item.version = version
      if (fingerprint !== undefined) item.fingerprint = fingerprint
      item.targets.push(target)
    })
  }

  async replaceTargetsAtInstallDirs(
    registry: string,
    namespace: string,
    slug: string,
    version: string,
    targets: InventoryTarget[],
    fingerprint?: string
  ): Promise<void> {
    await this.mutateAtomic(inventory => {
      const installDirs = new Set(targets.map(target => target.installDir))
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
        item = { registry, namespace, slug, version, targets: [] }
        inventory.items.push(item)
      }
      item.version = version
      if (fingerprint !== undefined) item.fingerprint = fingerprint
      item.targets.push(...targets)
    })
  }
}
