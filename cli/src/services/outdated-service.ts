import { SkillHubClient, type ResolveResponse } from '../clients/skillhub-client'
import { InventoryStore, type InventoryItem, type InventoryTarget } from '../stores/inventory-store'
import { inspectTargets, matchesTargetFilters } from './upgrade-service'
import { classifySkillVersionChange, type SkillVersionChange } from './skill-version-order'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import type { GithubSource } from '../shared/github-source'

export interface OutdatedOptions {
  /** Effective registry; only installations from this source are checked, as in `list`. */
  registry: string
  namespace?: string | undefined
  agents?: string[] | undefined
  dir?: string | undefined
  home?: string
  tokenForRegistry: (registry: string) => Promise<string | undefined>
}

export type OutdatedChange = Exclude<SkillVersionChange, 'none' | 'downgrade'>

/** Local file state from the same inspection `upgrade` uses before replacing files. */
export type OutdatedLocalState = 'clean' | 'modified' | 'unverified'

export interface OutdatedItem {
  coordinate: string
  registry: string
  agent: string
  installDir: string
  currentVersion: string
  remoteVersion: string | null
  change: OutdatedChange
  reason?: string
  localState: OutdatedLocalState
  localReason?: string
  changedFiles: string[]
  /** GitHub provenance of the remote version, when the registry records it. */
  remoteSource?: GithubSource
}

export interface OutdatedReport {
  registry: string
  checked: number
  outdated: number
  unknown: number
  failed: number
  failure?: CliError
  items: OutdatedItem[]
}

type RemoteLookup = { resolved: ResolveResponse } | { error: CliError }

/**
 * Read-only discovery of installations whose registry offers a newer version.
 * It never takes locks, writes inventory/metadata or downloads packages.
 */
export async function findOutdatedSkills(options: OutdatedOptions): Promise<OutdatedReport> {
  const registry = normalizeRegistry(options.registry)
  const inventory = await new InventoryStore(options.home).read()
  const lookups = new Map<string, Promise<RemoteLookup>>()
  const items: OutdatedItem[] = []
  let checked = 0
  let failure: CliError | undefined

  for (const item of inventory.items) {
    if (normalizeRegistry(item.registry) !== registry) continue
    if (options.namespace && item.namespace !== options.namespace) continue

    for (const target of item.targets) {
      if (!matchesTargetFilters(target, options.agents, options.dir)) continue
      checked += 1

      const key = `${item.namespace}\u0000${item.slug}`
      let lookup = lookups.get(key)
      if (!lookup) {
        lookup = lookupRemote(item, options.tokenForRegistry)
        lookups.set(key, lookup)
      }
      const remote = await lookup
      const base = {
        coordinate: `@${item.namespace}/${item.slug}`,
        registry: item.registry,
        agent: target.agent,
        installDir: target.installDir,
        currentVersion: target.version
      }

      if ('error' in remote) {
        failure ??= remote.error
        items.push({
          ...base,
          remoteVersion: null,
          change: 'unknown',
          reason: remote.error.message,
          ...(await inspectLocalState(item, target))
        })
        continue
      }

      const change = classifySkillVersionChange(target.version, remote.resolved.version)
      if (change === 'none' || change === 'downgrade') continue
      items.push({
        ...base,
        remoteVersion: remote.resolved.version,
        change,
        ...(change === 'unknown' ? { reason: 'cannot determine version order' } : {}),
        ...(remote.resolved.githubSource ? { remoteSource: remote.resolved.githubSource } : {}),
        ...(await inspectLocalState(item, target))
      })
    }
  }

  return {
    registry,
    checked,
    outdated: items.filter(item => item.change !== 'unknown').length,
    unknown: items.filter(item => item.change === 'unknown').length,
    failed: items.filter(item => item.remoteVersion === null).length,
    ...(failure ? { failure } : {}),
    items
  }
}

async function lookupRemote(
  item: InventoryItem,
  tokenForRegistry: OutdatedOptions['tokenForRegistry']
): Promise<RemoteLookup> {
  try {
    const token = await tokenForRegistry(item.registry)
    const resolved = await new SkillHubClient(item.registry, token).resolve(item.namespace, item.slug)
    if (resolved.namespace !== item.namespace || resolved.slug !== item.slug) {
      return { error: new CliError('registry resolved a different skill identity', EXIT.validation) }
    }
    return { resolved }
  } catch (error) {
    return { error: error instanceof CliError ? error : new CliError('remote version unavailable', EXIT.generic) }
  }
}

async function inspectLocalState(item: InventoryItem, target: InventoryTarget): Promise<{
  localState: OutdatedLocalState
  localReason?: string
  changedFiles: string[]
  /** GitHub provenance of the remote version, when the registry records it. */
  remoteSource?: GithubSource
}> {
  try {
    const inspection = await inspectTargets(item, [target])
    const hardConflict = inspection.hardConflicts[0]
    if (hardConflict) return { localState: 'unverified', localReason: hardConflict, changedFiles: [] }
    if (inspection.changedFiles.length > 0) {
      return { localState: 'modified', changedFiles: inspection.changedFiles }
    }
    if (inspection.baselineMissing) {
      return { localState: 'unverified', localReason: 'installed metadata has no file baseline', changedFiles: [] }
    }
    return { localState: 'clean', changedFiles: [] }
  } catch (error) {
    return {
      localState: 'unverified',
      localReason: error instanceof Error ? error.message : 'local files could not be inspected',
      changedFiles: []
    }
  }
}

function normalizeRegistry(registry: string): string {
  return registry.replace(/\/+$/, '')
}
