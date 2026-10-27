import { canonicalizeExistingPath, pathsOverlap } from '../platform/paths'
import { targetInstalledBy, type Inventory } from '../stores/inventory-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

/** Direct lifecycle commands cannot replace or remove another manager's position. */
export async function assertDirectTargetOwnership(inventory: Inventory, paths: readonly string[]): Promise<void> {
  const selected = await Promise.all(paths.map(canonicalizeExistingPath))
  const overlaps = (path: string) => selected.some(target => pathsOverlap(target, path))
  const fail = () => { throw new CliError('shared or Suite-owned target cannot be changed directly; use Suite management', EXIT.validation) }
  for (const item of inventory.items) for (const target of item.targets) {
    if (!overlaps(await canonicalizeExistingPath(target.installDir))) continue
    const owners = targetInstalledBy(item, target)
    if (owners.length !== 1 || owners[0] !== 'direct') fail()
  }
  for (const suite of inventory.suites ?? []) for (const member of suite.members) {
    for (const path of member.installDirs) if (overlaps(await canonicalizeExistingPath(path))) fail()
  }
}
