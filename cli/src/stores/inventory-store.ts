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
  targets: InventoryTarget[]
}

export interface Inventory {
  items: InventoryItem[]
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
    const payload = JSON.stringify(inventory, null, 2)
    JSON.parse(payload)

    const lockPath = `${this.path}.lock`
    const tmpPath = `${this.path}.${process.pid}.${Date.now()}.tmp`

    let lockHandle: Awaited<ReturnType<typeof open>> | null = null
    try {
      // Acquire exclusive lock with retry and stale lock detection
      lockHandle = await this.acquireLock(lockPath)

      await writeFile(tmpPath, payload)
      JSON.parse(await readFile(tmpPath, 'utf-8'))
      await rename(tmpPath, this.path)
    } finally {
      // Clean up temp file if it still exists
      await rm(tmpPath, { force: true }).catch(() => {})

      // Release lock
      if (lockHandle) {
        await lockHandle.close().catch(() => {})
        await rm(lockPath, { force: true }).catch(() => {})
      }
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
    target: InventoryTarget
  ): Promise<void> {
    const inventory = await this.read()
    let item = inventory.items.find(
      i => i.registry === registry && i.namespace === namespace && i.slug === slug
    )
    if (!item) {
      item = { registry, namespace, slug, version, targets: [] }
      inventory.items.push(item)
    }
    item.version = version
    const existingIdx = item.targets.findIndex(t => t.installDir === target.installDir)
    if (existingIdx >= 0) {
      item.targets[existingIdx] = target
    } else {
      item.targets.push(target)
    }
    await this.writeAtomic(inventory)
  }

  async removeTarget(registry: string, namespace: string, slug: string, installDir: string): Promise<boolean> {
    const inventory = await this.read()
    const item = inventory.items.find(i => i.registry === registry && i.namespace === namespace && i.slug === slug)
    if (!item) return false
    const idx = item.targets.findIndex(t => t.installDir === installDir)
    if (idx < 0) return false
    item.targets.splice(idx, 1)
    if (item.targets.length === 0) {
      inventory.items = inventory.items.filter(i => i !== item)
    }
    await this.writeAtomic(inventory)
    return true
  }

  async removeTargetsByInstallDir(installDir: string): Promise<number> {
    const inventory = await this.read()
    let removed = 0
    for (const item of inventory.items) {
      const before = item.targets.length
      item.targets = item.targets.filter(t => t.installDir !== installDir)
      removed += before - item.targets.length
    }
    if (removed > 0) {
      inventory.items = inventory.items.filter(item => item.targets.length > 0)
      await this.writeAtomic(inventory)
    }
    return removed
  }
}
