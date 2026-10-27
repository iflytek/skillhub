import { assertDirectTargetOwnership } from './inventory-ownership'
import { captureInstallInventory } from './inventory-cas'
import { canonicalizeExistingPath } from '../platform/paths'
import { rm, stat } from 'node:fs/promises'
import { relative, isAbsolute } from 'node:path'
import { InventoryStore } from '../stores/inventory-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { acquireSkillTargetLock } from './skill-target-lock'

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
  namespace?: string | undefined
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
  await store.ensureWritable()
  const inventory = await store.read()

  const items = inventory.items.filter(item =>
    item.registry === options.registry &&
    item.slug === options.slug &&
    (options.namespace === undefined || item.namespace === options.namespace)
  )
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

  const paths = await Promise.all(targetsToRemove.map(({ target }) => canonicalizeExistingPath(target.installDir)))
  await assertDirectTargetOwnership(inventory, paths)
  const baseline = await captureInstallInventory(inventory, items[0]!, new Set(paths))
  const removed: RemoveResult['removed'] = []
  const releases: Array<() => Promise<void>> = []

  for (const { target } of targetsToRemove) {
    // Validate installDir is strictly under the recorded rootDir
    if (!target.rootDir || !isPathUnder(target.installDir, target.rootDir)) {
      throw new CliError(`unsafe remove path: ${target.installDir} is not under ${target.rootDir ?? 'unknown root'}`, EXIT.filesystem, {
        path: target.installDir,
        next: 'verify inventory integrity with `skillhub doctor`'
      })
    }
  }

  try {
    for (const { target } of [...targetsToRemove]
      .sort((left, right) => left.target.installDir.localeCompare(right.target.installDir))) {
      releases.push(await acquireSkillTargetLock(target.rootDir, options.slug))
    }

    const current = await store.read()
    await assertDirectTargetOwnership(current, paths)
    if (await captureInstallInventory(current, items[0]!, new Set(paths)) !== baseline) {
      throw new CliError('installed skill records changed before removal', EXIT.validation)
    }
    for (const { item, target } of targetsToRemove) {
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
  } finally {
    for (const release of releases.reverse()) await release()
  }

  return { removed }
}
