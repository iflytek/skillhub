import { rm, stat } from 'node:fs/promises'
import { relative, isAbsolute } from 'node:path'
import { InventoryStore } from '../stores/inventory-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

/**
 * Validate that child path is strictly under parent directory.
 * Prevents path traversal attacks during remove operations.
 */
function isPathUnder(child: string, parent: string): boolean {
  const rel = relative(parent, child)
  return !rel.startsWith('..') && !isAbsolute(rel) && rel.length > 0
}

export interface RemoveLocalOptions {
  registry: string
  slug: string
  agents?: string[] | undefined
  all?: boolean | undefined
  home?: string | undefined
}

export interface RemoveResult {
  removed: Array<{ namespace: string; agent: string; dir: string; existed: boolean }>
}

export async function removeLocalSkill(options: RemoveLocalOptions): Promise<RemoveResult> {
  const store = new InventoryStore(options.home)
  const inventory = await store.read()

  const items = inventory.items.filter(i => i.registry === options.registry && i.slug === options.slug)
  if (items.length === 0) {
    throw new CliError(`skill not found locally: ${options.slug}`, EXIT.generic, {
      next: 'run `skillhub list` to see installed skills'
    })
  }

  const targetsToRemove = items.flatMap(item => {
    const targets = options.agents?.length
      ? item.targets.filter(t => options.agents!.includes(t.agent))
      : item.targets
    return targets.map(target => ({ item, target }))
  })
  if (targetsToRemove.length === 0) {
    throw new CliError(`no matching targets for agents: ${options.agents?.join(', ')}`, EXIT.generic)
  }

  const removed: RemoveResult['removed'] = []

  for (const { item, target } of targetsToRemove) {
    // Validate installDir is strictly under the recorded rootDir
    if (!target.rootDir || !isPathUnder(target.installDir, target.rootDir)) {
      throw new CliError(`unsafe remove path: ${target.installDir} is not under ${target.rootDir ?? 'unknown root'}`, EXIT.filesystem, {
        path: target.installDir,
        next: 'verify inventory integrity with `skillhub doctor`'
      })
    }

    let existed = true
    try {
      await stat(target.installDir)
    } catch {
      existed = false
    }

    if (existed) {
      await rm(target.installDir, { recursive: true })
    }

    await store.removeTarget(options.registry, item.namespace, options.slug, target.installDir)
    removed.push({ namespace: item.namespace, agent: target.agent, dir: target.installDir, existed })
  }

  return { removed }
}
