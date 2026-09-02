import { isAbsolute, relative, resolve } from 'node:path'
import { canonicalizeExistingPath, pathExists } from '../platform/paths'
import { SkillHubClient, type ResolveResponse } from '../clients/skillhub-client'
import { InventoryStore, type InventoryItem, type InventoryTarget } from '../stores/inventory-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { hasExplicitNamespace, parseSkillName, resolveSkillName } from '../shared/skill-name-parser'
import { diffSkillFiles, snapshotSkillDirectory } from './skill-fingerprint'
import { installSkill } from './install-service'
import { readInstalledSkillMetadata, sameInstalledSkillSource } from './installed-skill-metadata'
import { compareSkillVersions } from './skill-version-order'

const MAX_UPGRADE_SELECTION = 50

export interface UpgradeSelectionOptions {
  coordinates: string[]
  namespace?: string | undefined
  registry?: string | undefined
  agents?: string[] | undefined
  dir?: string | undefined
  force: boolean
  home?: string
  tokenForRegistry: (registry: string) => Promise<string | undefined>
}

export type UpgradePlanAction = 'upgrade' | 'unchanged' | 'blocked'

export interface UpgradePlanItem {
  coordinate: string
  registry: string
  currentVersion: string
  remoteVersion?: string
  action: UpgradePlanAction
  reason?: string
  changedFiles: string[]
  targets: Array<{ agent: string; dir: string }>
  resolved?: ResolveResponse
  inventoryItem: InventoryItem
  selectedTargets: InventoryTarget[]
  expectedTargetFiles: Record<string, Record<string, string>>
  allowTargetDrift: boolean
}

export interface UpgradePlan {
  items: UpgradePlanItem[]
  blocked: number
  upgrades: number
  unchanged: number
}

export type UpgradeExecutionAction = 'upgraded' | 'unchanged' | 'failed' | 'not-attempted'

export interface UpgradeExecutionResult {
  items: Array<{
    coordinate: string
    action: UpgradeExecutionAction
    reason?: string
    warnings?: string[]
    exitCode?: number
  }>
  upgraded: number
  unchanged: number
  failed: number
  notAttempted: number
}

type UpgradeExecutionOptions = Pick<UpgradeSelectionOptions, 'home' | 'tokenForRegistry'> & {
  installSkillFn?: typeof installSkill
}

export async function planSkillUpgrades(options: UpgradeSelectionOptions): Promise<UpgradePlan> {
  if (options.coordinates.length === 0) {
    throw new CliError('provide at least one installed skill coordinate', EXIT.usage)
  }
  if (options.coordinates.length > MAX_UPGRADE_SELECTION) {
    throw new CliError(`upgrade accepts at most ${MAX_UPGRADE_SELECTION} coordinates`, EXIT.usage)
  }

  const store = new InventoryStore(options.home)
  const inventory = await store.read()
  const selected = selectInventoryItems(inventory.items, options)
  const items: UpgradePlanItem[] = []

  for (const selection of selected) {
    const coordinate = `@${selection.item.namespace}/${selection.item.slug}`
    const base = {
      coordinate,
      registry: selection.item.registry,
      currentVersion: selection.item.version,
      changedFiles: [] as string[],
      targets: selection.targets.map(target => ({ agent: target.agent, dir: target.installDir })),
      inventoryItem: selection.item,
      selectedTargets: selection.targets,
      expectedTargetFiles: {} as Record<string, Record<string, string>>,
      allowTargetDrift: options.force
    }

    if (selection.targets.length !== selection.item.targets.length) {
      items.push({
        ...base,
        action: 'blocked',
        reason: 'partial-target upgrades are not supported because one inventory item has one shared version'
      })
      continue
    }

    let resolved: ResolveResponse
    try {
      const token = await options.tokenForRegistry(selection.item.registry)
      resolved = await new SkillHubClient(selection.item.registry, token)
        .resolve(selection.item.namespace, selection.item.slug)
    } catch (error) {
      items.push({
        ...base,
        action: 'blocked',
        reason: error instanceof Error ? error.message : 'remote version unavailable'
      })
      continue
    }

    if (resolved.namespace !== selection.item.namespace || resolved.slug !== selection.item.slug) {
      items.push({
        ...base,
        remoteVersion: resolved.version,
        action: 'blocked',
        reason: 'registry resolved a different skill identity'
      })
      continue
    }

    const inspection = await inspectTargets(selection.item, selection.targets)
    const hardConflict = inspection.hardConflicts[0]
    if (hardConflict) {
      items.push({
        ...base,
        remoteVersion: resolved.version,
        action: 'blocked',
        reason: hardConflict,
        changedFiles: inspection.changedFiles,
        expectedTargetFiles: inspection.currentFiles,
        resolved
      })
      continue
    }
    if (inspection.changedFiles.length > 0 && !options.force) {
      items.push({
        ...base,
        remoteVersion: resolved.version,
        action: 'blocked',
        reason: 'local changes detected; pass --force to replace same-source files',
        changedFiles: inspection.changedFiles,
        expectedTargetFiles: inspection.currentFiles,
        resolved
      })
      continue
    }
    if (inspection.baselineMissing && !options.force) {
      items.push({
        ...base,
        remoteVersion: resolved.version,
        action: 'blocked',
        reason: 'installed metadata has no file baseline; pass --force to migrate this same-source installation',
        expectedTargetFiles: inspection.currentFiles,
        resolved
      })
      continue
    }

    const versionOrder = compareSkillVersions(selection.item.version, resolved.version)
    if (versionOrder === 'remote-older') {
      items.push({
        ...base,
        remoteVersion: resolved.version,
        action: 'blocked',
        reason: 'remote version is older than the installed version; local files were kept',
        changedFiles: inspection.changedFiles,
        expectedTargetFiles: inspection.currentFiles,
        resolved
      })
      continue
    }
    if (versionOrder === 'unknown') {
      items.push({
        ...base,
        remoteVersion: resolved.version,
        action: 'blocked',
        reason: 'cannot determine version order; use explicit install after verifying the release',
        changedFiles: inspection.changedFiles,
        expectedTargetFiles: inspection.currentFiles,
        resolved
      })
      continue
    }

    const unchanged = versionOrder === 'same' &&
      selection.item.fingerprint === resolved.fingerprint &&
      inspection.metadataCurrent
    if (versionOrder === 'same' && !unchanged) {
      items.push({
        ...base,
        remoteVersion: resolved.version,
        action: 'blocked',
        reason: 'remote content changed without a newer version; use explicit install after verifying the release',
        changedFiles: inspection.changedFiles,
        expectedTargetFiles: inspection.currentFiles,
        resolved
      })
      continue
    }
    items.push({
      ...base,
      remoteVersion: resolved.version,
      action: unchanged ? 'unchanged' : 'upgrade',
      changedFiles: inspection.changedFiles,
      expectedTargetFiles: inspection.currentFiles,
      resolved
    })
  }

  return {
    items,
    blocked: items.filter(item => item.action === 'blocked').length,
    upgrades: items.filter(item => item.action === 'upgrade').length,
    unchanged: items.filter(item => item.action === 'unchanged').length
  }
}

export async function executeSkillUpgradePlan(
  plan: UpgradePlan,
  options: UpgradeExecutionOptions
): Promise<UpgradeExecutionResult> {
  if (plan.blocked > 0) {
    throw new CliError('upgrade plan contains blocked skills', EXIT.validation)
  }

  const items: UpgradeExecutionResult['items'] = []
  let stopped = false
  for (const item of plan.items) {
    if (item.action === 'unchanged') {
      items.push({ coordinate: item.coordinate, action: 'unchanged' })
      continue
    }
    if (item.action !== 'upgrade' || !item.resolved) continue
    if (stopped) {
      items.push({ coordinate: item.coordinate, action: 'not-attempted' })
      continue
    }

    try {
      const token = await options.tokenForRegistry(item.registry)
      const installed = await (options.installSkillFn ?? installSkill)({
        registry: item.registry,
        token,
        namespace: item.inventoryItem.namespace,
        slug: item.inventoryItem.slug,
        resolved: item.resolved,
        targets: item.selectedTargets.map(target => ({
          agent: target.agent,
          rootDir: target.rootDir,
          scope: 'project',
          source: 'explicit'
        })),
        force: true,
        home: options.home,
        expectedTargetFiles: item.expectedTargetFiles,
        allowTargetDrift: item.allowTargetDrift,
        requireExistingTargets: true
      })
      items.push({
        coordinate: item.coordinate,
        action: 'upgraded',
        ...(installed.warnings?.length ? { warnings: installed.warnings } : {})
      })
    } catch (error) {
      items.push({
        coordinate: item.coordinate,
        action: 'failed',
        reason: error instanceof Error ? error.message : 'unexpected upgrade failure',
        ...(error instanceof CliError ? { exitCode: error.exitCode } : {})
      })
      stopped = true
    }
  }

  return {
    items,
    upgraded: items.filter(item => item.action === 'upgraded').length,
    unchanged: items.filter(item => item.action === 'unchanged').length,
    failed: items.filter(item => item.action === 'failed').length,
    notAttempted: items.filter(item => item.action === 'not-attempted').length
  }
}

function selectInventoryItems(
  items: InventoryItem[],
  options: Pick<UpgradeSelectionOptions, 'coordinates' | 'namespace' | 'registry' | 'agents' | 'dir'>
): Array<{ item: InventoryItem; targets: InventoryTarget[] }> {
  const selected = new Map<string, { item: InventoryItem; targets: InventoryTarget[] }>()

  for (const coordinate of options.coordinates) {
    const explicitNamespace = hasExplicitNamespace(coordinate)
    const parsed = explicitNamespace
      ? resolveSkillName(coordinate, options.namespace)
      : parseSkillName(coordinate)
    const namespace = explicitNamespace ? parsed.namespace : options.namespace

    const matches = items.flatMap(item => {
      if (item.slug !== parsed.slug) return []
      if (namespace && item.namespace !== namespace) return []
      if (options.registry && normalizeRegistry(item.registry) !== normalizeRegistry(options.registry)) return []
      const targets = item.targets.filter(target => matchesTargetFilters(target, options.agents, options.dir))
      return targets.length > 0 ? [{ item, targets }] : []
    })

    if (matches.length === 0) {
      throw new CliError(`skill "${coordinate}" is not installed`, EXIT.usage, {
        next: `use skillhub install ${coordinate}`
      })
    }
    if (matches.length > 1) {
      throw new CliError(`installed skill "${coordinate}" is ambiguous`, EXIT.usage, {
        matches: matches.map(match => `${match.item.registry} @${match.item.namespace}/${match.item.slug}`),
        next: 'use a full coordinate and --registry to select one installation source'
      })
    }

    const match = matches[0]!
    const key = `${normalizeRegistry(match.item.registry)}\u0000${match.item.namespace}\u0000${match.item.slug}`
    selected.set(key, match)
  }

  if (selected.size > MAX_UPGRADE_SELECTION) {
    throw new CliError(`upgrade resolves to at most ${MAX_UPGRADE_SELECTION} skills`, EXIT.usage)
  }
  return [...selected.values()]
}

async function inspectTargets(item: InventoryItem, targets: InventoryTarget[]): Promise<{
  hardConflicts: string[]
  changedFiles: string[]
  baselineMissing: boolean
  metadataCurrent: boolean
  currentFiles: Record<string, Record<string, string>>
}> {
  const hardConflicts: string[] = []
  const changedFiles = new Set<string>()
  let baselineMissing = false
  let metadataCurrent = true
  const currentFiles: Record<string, Record<string, string>> = {}

  for (const target of targets) {
    if (!isAbsolute(target.rootDir) || !isAbsolute(target.installDir)) {
      hardConflicts.push(`legacy relative target path is unsafe to upgrade: ${target.installDir}`)
      continue
    }
    const installDir = await canonicalizeExistingPath(target.installDir)
    if (!(await pathExists(installDir))) {
      hardConflicts.push(`installed target is missing: ${target.installDir}`)
      continue
    }
    const result = await readInstalledSkillMetadata(installDir)
    if (result.status !== 'valid') {
      hardConflicts.push(`metadata-invalid at ${target.installDir}: ${result.status === 'missing' ? 'missing' : result.reason}`)
      continue
    }
    if (!sameInstalledSkillSource(result.metadata, item)) {
      hardConflicts.push(`source-conflict at ${target.installDir}`)
      continue
    }
    if (!result.metadata.files) {
      baselineMissing = true
    } else {
      const snapshot = await snapshotSkillDirectory(installDir)
      currentFiles[target.installDir] = snapshot.files
      for (const path of diffSkillFiles(result.metadata.files, snapshot.files)) {
        changedFiles.add(`${target.installDir}:${path}`)
      }
    }
    if (result.metadata.version !== item.version || result.metadata.fingerprint !== item.fingerprint) {
      metadataCurrent = false
    }
  }

  return {
    hardConflicts,
    changedFiles: [...changedFiles].sort(),
    baselineMissing,
    metadataCurrent,
    currentFiles
  }
}

function matchesTargetFilters(target: InventoryTarget, agents?: string[], dir?: string): boolean {
  if (agents?.length && !agents.includes(target.agent)) return false
  if (!dir) return true
  const filterPath = resolve(dir)
  const installPath = resolve(target.installDir)
  const rootPath = resolve(target.rootDir)
  return isSameOrWithin(filterPath, installPath) || isSameOrWithin(filterPath, rootPath)
}

function isSameOrWithin(parent: string, candidate: string): boolean {
  const rel = relative(parent, candidate)
  return rel === '' || (!rel.startsWith('..') && !rel.startsWith('/') && !rel.startsWith('\\'))
}

function normalizeRegistry(registry: string): string {
  return registry.replace(/\/+$/, '')
}
