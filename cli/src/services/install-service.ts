import { mkdir, mkdtemp, rename, rm, writeFile } from 'node:fs/promises'
import { join, relative, resolve } from 'node:path'
import { SkillHubClient } from '../clients/skillhub-client'
import { InventoryStore, InventoryVersionConflictError } from '../stores/inventory-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { extractZip } from '../platform/archive'
import { readBoundedResponseBody } from '../platform/download'
import { canonicalizeExistingPath, pathExists } from '../platform/paths'
import { diffSkillFiles, snapshotSkillDirectory } from './skill-fingerprint'
import {
  readInstalledSkillMetadata,
  sameInstalledSkillSource,
  type InstalledSkillIdentity
} from './installed-skill-metadata'
import type { AgentCandidate } from '../agents/types'
import type { ResolveResponse } from '../clients/skillhub-client'
import type { Inventory } from '../stores/inventory-store'
import { acquireSkillTargetLock } from './skill-target-lock'

export interface InstallOptions {
  registry: string
  token?: string | undefined
  namespace: string
  slug: string
  version?: string | undefined
  targets: AgentCandidate[]
  force: boolean
  home?: string | undefined
  resolved?: ResolveResponse | undefined
  expectedTargetFiles?: Record<string, Record<string, string>> | undefined
  allowTargetDrift?: boolean | undefined
  requireExistingTargets?: boolean | undefined
  /** Internal test seam for lock lifecycle failures; production uses acquireSkillTargetLock. */
  acquireTargetLock?: typeof acquireSkillTargetLock
}

export interface InstallResult {
  installed: Array<{ agent: string; dir: string }>
  warnings?: string[]
}

interface StagedInstall {
  target: AgentCandidate
  skillDir: string
  canonicalSkillDir: string
  tempDir: string
  installedAt: string
  backupDir: string | null
  movedIntoPlace: boolean
}

async function preflightInstallTargets(
  targets: AgentCandidate[],
  identity: InstalledSkillIdentity,
  force: boolean,
  inventory: Inventory
): Promise<Array<{ target: AgentCandidate; skillDir: string; canonicalSkillDir: string }>> {
  const seenSkillDirs = new Set<string>()
  const preparedTargets: Array<{ target: AgentCandidate; skillDir: string; canonicalSkillDir: string }> = []

  for (const target of targets) {
    const rootDir = resolve(target.rootDir)
    const canonicalRootDir = await canonicalizeExistingPath(rootDir)
    const canonicalSkillDir = join(canonicalRootDir, identity.slug)
    if (seenSkillDirs.has(canonicalSkillDir)) {
      throw new CliError(`multiple install targets resolve to ${canonicalSkillDir}`, EXIT.usage, {
        path: canonicalSkillDir,
        next: 'select only one target for this directory'
      })
    }
    seenSkillDirs.add(canonicalSkillDir)

    // Use the canonical path only as an internal identity. Persist the resolved
    // user path so macOS aliases and Windows short names remain stable in CLI output.
    const resolvedTarget = { ...target, rootDir }
    const skillDir = join(rootDir, identity.slug)
    const exists = await pathExists(skillDir)
    if (exists && !force) {
      throw new CliError(`skill already installed at ${skillDir}`, EXIT.filesystem, {
        path: skillDir,
        next: 'pass --force to replace a same-source installation'
      })
    }
    if (exists) {
      await assertReplaceableInstallation(skillDir, skillDir, canonicalSkillDir, identity, inventory)
    }
    preparedTargets.push({ target: resolvedTarget, skillDir, canonicalSkillDir })
  }

  return preparedTargets
}

export async function installSkill(options: InstallOptions): Promise<InstallResult> {
  const store = new InventoryStore(options.home)
  const inventory = await store.read()
  const preparedTargets = await preflightInstallTargets(options.targets, {
    registry: options.registry,
    namespace: options.namespace,
    slug: options.slug
  }, options.force, inventory)
  const client = new SkillHubClient(options.registry, options.token)
  const resolved = options.resolved ?? await client.resolve(options.namespace, options.slug, options.version)
  const response = await client.download(options.namespace, options.slug, resolved.version)
  const buffer = await readBoundedResponseBody(response)

  const staged: StagedInstall[] = []
  try {
    for (const { target, skillDir, canonicalSkillDir } of preparedTargets) {
      await mkdir(target.rootDir, { recursive: true })
      const tempDir = await mkdtemp(join(target.rootDir, `.${options.slug}.install-`))
      try {
        await extractZip(buffer, tempDir)

        const installedAt = new Date().toISOString()
        const snapshot = await snapshotSkillDirectory(tempDir)
        const metaDir = join(tempDir, '.skillhub')
        await mkdir(metaDir, { recursive: true })
        await writeFile(join(metaDir, 'metadata.json'), JSON.stringify({
          schemaVersion: 1,
          registry: options.registry,
          namespace: options.namespace,
          slug: options.slug,
          version: resolved.version,
          versionId: resolved.versionId,
          fingerprint: resolved.fingerprint,
          files: snapshot.files,
          source: 'skillhub',
          agent: target.agent,
          installedAt
        }, null, 2))
        staged.push({ target, skillDir, canonicalSkillDir, tempDir, installedAt, backupDir: null, movedIntoPlace: false })
      } catch (error) {
        await rm(tempDir, { recursive: true, force: true }).catch(() => {})
        throw error
      }
    }

    const releases: Array<() => Promise<void>> = []
    const warnings: string[] = []
    const acquireTargetLock = options.acquireTargetLock ?? acquireSkillTargetLock
    try {
      for (const item of [...staged].sort((left, right) => left.skillDir.localeCompare(right.skillDir))) {
        releases.push(await acquireTargetLock(item.target.rootDir, options.slug))
      }

      const lockedInventory = await store.read()
      await assertNoPartialVersionChange(lockedInventory, staged, {
        registry: options.registry,
        namespace: options.namespace,
        slug: options.slug
      }, resolved)

      for (const item of staged) {
        const targetExists = await pathExists(item.skillDir)
        if (!targetExists && options.requireExistingTargets) {
          throw new CliError(`installed target disappeared before upgrade commit: ${item.skillDir}`, EXIT.validation, {
            path: item.skillDir,
            next: 'reinstall the Skill explicitly before upgrading it'
          })
        }
        if (targetExists) {
          if (!options.force) {
            throw new CliError(`skill already installed at ${item.skillDir}`, EXIT.filesystem, {
              path: item.skillDir,
              next: 'pass --force to overwrite'
            })
          }
          const backupDir = `${item.skillDir}.skillhub-backup-${process.pid}-${Date.now()}`
          await rename(item.skillDir, backupDir)
          item.backupDir = backupDir
          await assertReplaceableInstallation(item.backupDir, item.skillDir, item.canonicalSkillDir, {
            registry: options.registry,
            namespace: options.namespace,
            slug: options.slug
          }, lockedInventory)
          const expectedFiles = options.expectedTargetFiles?.[item.skillDir]
          if (expectedFiles && !options.allowTargetDrift) {
            const currentSnapshot = await snapshotSkillDirectory(item.backupDir)
            const changedFiles = diffSkillFiles(expectedFiles, currentSnapshot.files)
            if (changedFiles.length > 0) {
              throw new CliError(`local changes detected after upgrade planning at ${item.skillDir}`, EXIT.validation, {
                path: item.skillDir,
                changedFiles,
                next: 'review the local changes and retry with --force only if replacement is intended'
              })
            }
          }
        }
        await rename(item.tempDir, item.skillDir)
        item.movedIntoPlace = true
      }

      try {
        const replacedInstallDirs = await findEquivalentInventoryInstallDirs(
          lockedInventory,
          new Set(staged.map(item => item.canonicalSkillDir))
        )
        await store.replaceTargetsAtInstallDirs(
          options.registry,
          options.namespace,
          options.slug,
          resolved.version,
          staged.map(item => ({
            agent: item.target.agent,
            rootDir: item.target.rootDir,
            installDir: item.skillDir,
            installedAt: item.installedAt
          })),
          resolved.fingerprint,
          replacedInstallDirs
        )
      } catch (error) {
        if (error instanceof InventoryVersionConflictError) {
          throw new CliError(error.message, EXIT.validation, {
            coordinate: `@${options.namespace}/${options.slug}`,
            retainedTargets: error.retainedTargets.map(target => ({ agent: target.agent, dir: target.installDir })),
            next: 'select all installed targets for the upgrade'
          })
        }
        throw error
      }

      for (const item of staged) {
        if (item.backupDir) await rm(item.backupDir, { recursive: true, force: true }).catch(() => {})
      }
    } catch (error) {
      const rollbackFailures: Array<{ operation: string; path: string; error: string }> = []
      for (const item of [...staged].reverse()) {
        if (item.movedIntoPlace) {
          try {
            await rm(item.skillDir, { recursive: true, force: true })
            item.movedIntoPlace = false
          } catch (rollbackError) {
            rollbackFailures.push({
              operation: 'remove replacement',
              path: item.skillDir,
              error: describeError(rollbackError)
            })
          }
        }
        if (item.backupDir) {
          const backupDir = item.backupDir
          try {
            await rename(backupDir, item.skillDir)
            item.backupDir = null
          } catch (rollbackError) {
            rollbackFailures.push({
              operation: 'restore backup',
              path: backupDir,
              error: describeError(rollbackError)
            })
          }
        }
      }
      if (rollbackFailures.length > 0) {
        throw new CliError('installation failed and rollback was incomplete', EXIT.filesystem, {
          originalError: describeError(error),
          rollbackFailures,
          retainedBackups: staged.flatMap(item => item.backupDir ? [item.backupDir] : []),
          next: 'restore the retained backup directories before retrying'
        })
      }
      throw error
    } finally {
      for (const release of releases.reverse()) {
        try {
          await release()
        } catch (error) {
          warnings.push(`target lock cleanup failed: ${describeError(error)}`)
        }
      }
    }

    return {
      installed: staged.map(item => ({ agent: item.target.agent, dir: item.skillDir })),
      warnings
    }
  } finally {
    for (const item of staged) {
      if (!item.movedIntoPlace) await rm(item.tempDir, { recursive: true, force: true }).catch(() => {})
    }
  }
}

function describeError(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

async function assertNoPartialVersionChange(
  inventory: Inventory,
  staged: StagedInstall[],
  identity: InstalledSkillIdentity,
  resolved: ResolveResponse
): Promise<void> {
  const item = inventory.items.find(candidate => sameInstalledSkillSource(candidate, identity))
  if (!item) return

  const selectedInstallDirs = new Set(staged.map(candidate => candidate.canonicalSkillDir))
  const retainedTargets: typeof item.targets = []
  for (const target of item.targets) {
    if (!selectedInstallDirs.has(await canonicalInventoryInstallDir(target))) retainedTargets.push(target)
  }
  if (retainedTargets.length === 0) return
  if (item.version === resolved.version && item.fingerprint === resolved.fingerprint) return

  throw new CliError('partial-target install would create inconsistent versions', EXIT.validation, {
    coordinate: `@${identity.namespace}/${identity.slug}`,
    retainedTargets: retainedTargets.map(target => ({ agent: target.agent, dir: target.installDir })),
    next: 'select all installed targets for the upgrade'
  })
}

async function assertReplaceableInstallation(
  metadataDir: string,
  inventoryInstallDir: string,
  canonicalInstallDir: string,
  identity: InstalledSkillIdentity,
  inventory: Inventory
): Promise<void> {
  const metadataResult = await readInstalledSkillMetadata(metadataDir)
  if (metadataResult.status !== 'valid') {
    throw new CliError(`cannot verify SkillHub ownership of ${inventoryInstallDir}`, EXIT.filesystem, {
      path: inventoryInstallDir,
      reason: metadataResult.status === 'missing' ? 'installation metadata is missing' : metadataResult.reason,
      next: 'move or remove the existing directory before installing'
    })
  }

  const inventoryOwners: Inventory['items'] = []
  for (const item of inventory.items) {
    for (const target of item.targets) {
      if (await canonicalInventoryInstallDir(target) === canonicalInstallDir) {
        inventoryOwners.push(item)
        break
      }
    }
  }
  if (!sameInstalledSkillSource(metadataResult.metadata, identity) ||
      inventoryOwners.some(owner => !sameInstalledSkillSource(owner, identity))) {
    throw new CliError(`source conflict at ${inventoryInstallDir}`, EXIT.filesystem, {
      path: inventoryInstallDir,
      expected: identity,
      actual: {
        registry: metadataResult.metadata.registry,
        namespace: metadataResult.metadata.namespace,
        slug: metadataResult.metadata.slug
      },
      next: 'choose another target directory or remove the conflicting skill explicitly'
    })
  }
}

async function findEquivalentInventoryInstallDirs(
  inventory: Inventory,
  canonicalInstallDirs: Set<string>
): Promise<string[]> {
  const matches: string[] = []
  for (const item of inventory.items) {
    for (const target of item.targets) {
      if (canonicalInstallDirs.has(await canonicalInventoryInstallDir(target))) {
        matches.push(target.installDir)
      }
    }
  }
  return matches
}

async function canonicalInventoryInstallDir(target: { rootDir: string; installDir: string }): Promise<string> {
  const resolvedRoot = resolve(target.rootDir)
  const canonicalRoot = await canonicalizeExistingPath(resolvedRoot)
  return resolve(canonicalRoot, relative(resolvedRoot, resolve(target.installDir)))
}
