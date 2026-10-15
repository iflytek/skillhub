import { captureInstallInventory } from './inventory-cas'
import { randomUUID } from 'node:crypto'
import type { InstallMove, InstallTransactionObserver, InstallTransactionWarning } from './install-transaction'
import { mkdir, rename, rm, writeFile } from 'node:fs/promises'
import { join, relative, resolve } from 'node:path'
import { SkillHubClient } from '../clients/skillhub-client'
import { InventoryStore, InventoryVersionConflictError, InventoryCommitUnknownError } from '../stores/inventory-store'
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
import type { Inventory, InventoryItem } from '../stores/inventory-store'
import { acquireSkillTargetLock, orderSkillTargetLocks } from './skill-target-lock'

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
  client?: SkillHubClient | undefined
  /** Internal test seam for lock lifecycle failures; production uses acquireSkillTargetLock. */
  acquireTargetLock?: typeof acquireSkillTargetLock
  transactionObserver?: InstallTransactionObserver
  /** Internal adapter/test seam; the caller owns the trusted state directory. */
  inventoryStore?: InventoryStore
  /** Explicit plan baseline; null requires that the source coordinate is not installed. */
  expectedInventoryItem?: InventoryItem | null
  /** Native Host policy guard. Re-read effective policy; rejection must prevent the indicated mutation. */
  authorizeTargetMutation?: (paths: readonly string[], phase: 'staging' | 'replace' | 'cleanup' | 'rollback') => Promise<void>
  /** Host format validation after archive/fingerprint checks and before publication. */
  validateStagedSkill?: (directory: string) => Promise<void>
}

export interface InstallResult {
  installed: Array<{ agent: string; dir: string }>
  warnings?: string[]
  transactionWarnings?: InstallTransactionWarning[]
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
  const store = options.inventoryStore ?? new InventoryStore(options.home)
  const expectedItem = options.expectedInventoryItem === undefined ? undefined : structuredClone(options.expectedInventoryItem)
  const inventory = await store.read()
  const identity = { registry: options.registry, namespace: options.namespace, slug: options.slug }
  if (expectedItem !== undefined) {
    if (expectedItem && !sameInstalledSkillSource(expectedItem, identity)) throw inventoryChangedError()
    const expected = await captureInstallInventory({ items: expectedItem ? [expectedItem] : [] }, identity, new Set())
    if (await captureInstallInventory(inventory, identity, new Set()) !== expected) throw inventoryChangedError()
  }
  const preparedTargets = await preflightInstallTargets(options.targets, {
    registry: options.registry,
    namespace: options.namespace,
    slug: options.slug
  }, options.force, inventory)
  const canonicalDirs = new Set(preparedTargets.map(target => target.canonicalSkillDir))
  const baseline = await captureInstallInventory(inventory, identity, canonicalDirs)
  const assertInventoryCurrent = async (current: Inventory) => {
    if (await captureInstallInventory(current, identity, canonicalDirs) !== baseline) throw inventoryChangedError()
  }
  await options.transactionObserver?.beforeTargetMutation?.([...canonicalDirs])
  const client = options.client ?? new SkillHubClient(options.registry, options.token)
  const resolved = options.resolved ?? await client.resolve(options.namespace, options.slug, options.version)
  const response = resolved.downloadUrl
    ? await client.downloadFromUrl(resolved.downloadUrl)
    : await client.download(options.namespace, options.slug, resolved.version)
  const buffer = await readBoundedResponseBody(response)

  const staged: StagedInstall[] = []
  let preserveStaging = false
  try {
    for (const { target, skillDir, canonicalSkillDir } of preparedTargets) {
      await options.authorizeTargetMutation?.([target.rootDir], 'staging')
      const tempDir = join(target.rootDir, `.${options.slug}.install-${randomUUID()}`)
      await options.transactionObserver?.beforeStaging?.({ canonicalSkillDir, rootDir: target.rootDir, stagingDir: tempDir })
      await mkdir(target.rootDir, { recursive: true })
      // Non-recursive creation rejects collisions instead of adopting an existing directory.
      await mkdir(tempDir, { mode: 0o700 })
      try {
        await options.authorizeTargetMutation?.([tempDir], 'staging')
        await extractZip(buffer, tempDir)

        const installedAt = new Date().toISOString()
        const snapshot = await snapshotSkillDirectory(tempDir)
        if (snapshot.fingerprint !== resolved.fingerprint) {
          throw new CliError('downloaded skill fingerprint does not match the resolved release', EXIT.validation, {
            coordinate: `@${options.namespace}/${options.slug}`,
            expectedFingerprint: resolved.fingerprint,
            actualFingerprint: snapshot.fingerprint,
            next: 'retry the install after the registry release has been verified'
          })
        }
        await options.validateStagedSkill?.(tempDir)
        const metaDir = join(tempDir, '.skillhub')
        await options.authorizeTargetMutation?.([metaDir], 'staging')
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
        try {
          await options.authorizeTargetMutation?.([tempDir], 'cleanup')
          await rm(tempDir, { recursive: true, force: true })
        } catch { /* Retain staging if cleanup is denied or fails. */ }
        throw error
      }
    }

    const releases: Array<() => Promise<void>> = []
    const warnings: string[] = []
    const transactionWarnings: InstallTransactionWarning[] = []
    const observer = options.transactionObserver
    const warn = (warning: InstallTransactionWarning) => {
      transactionWarnings.push(warning)
      warnings.push(`${warning.stage}${warning.path ? ` at ${warning.path}` : ''}: ${warning.message}`)
    }
    let inventoryCommitted = false
    const acquireTargetLock = options.acquireTargetLock ?? acquireSkillTargetLock
    try {
      for (const target of await orderSkillTargetLocks(staged.map(item => item.target), options.slug)) {
        releases.push(await acquireTargetLock(target.rootDir, options.slug))
      }

      await observer?.beforeTargetMutation?.(staged.map(item => item.canonicalSkillDir))
      const lockedInventory = await store.read()
      // Preserve the existing first-install conflict result when another installer won the race.
      if (!options.force) {
        for (const item of staged) {
          if (await pathExists(item.skillDir)) {
            throw new CliError(`skill already installed at ${item.skillDir}`, EXIT.filesystem, {
              path: item.skillDir,
              next: 'pass --force to overwrite'
            })
          }
        }
      }
      await assertInventoryCurrent(lockedInventory)
      await assertNoPartialVersionChange(lockedInventory, staged, {
        registry: options.registry,
        namespace: options.namespace,
        slug: options.slug
      }, resolved)

      if (observer?.beforePreparation) {
        await store.withLockedSnapshot(async current => {
          await assertInventoryCurrent(current)
          await observer.beforePreparation!({ before: structuredClone(current),
            release: { registry: options.registry, namespace: options.namespace, slug: options.slug,
              version: resolved.version, fingerprint: resolved.fingerprint },
            targets: staged.map(item => ({ agent: item.target.agent, canonicalSkillDir: item.canonicalSkillDir,
              skillDir: item.skillDir, stagingDir: item.tempDir, installedAt: item.installedAt })) })
        })
      }

      for (const [index, item] of staged.entries()) {
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
          const backupDir = `${item.skillDir}.skillhub-backup-${randomUUID()}`
          const move: InstallMove = { phase: 'backup', index, agent: item.target.agent,
            canonicalSkillDir: item.canonicalSkillDir, stagingDir: item.tempDir, backupDir, from: item.skillDir, to: backupDir }
          await observer?.beforeMove?.({ ...move })
          await options.authorizeTargetMutation?.([move.from, move.to], 'replace')
          await rename(item.skillDir, backupDir)
          item.backupDir = backupDir
          await observer?.afterMove?.({ ...move })
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
        const move: InstallMove = { phase: 'install', index, agent: item.target.agent,
          canonicalSkillDir: item.canonicalSkillDir, stagingDir: item.tempDir, backupDir: item.backupDir, from: item.tempDir, to: item.skillDir }
        await observer?.beforeMove?.({ ...move })
        await options.authorizeTargetMutation?.([move.from, move.to], 'replace')
        await rename(item.tempDir, item.skillDir)
        item.movedIntoPlace = true
        await observer?.afterMove?.({ ...move })
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
          replacedInstallDirs,
          async (before, after) => {
            await assertInventoryCurrent(before)
            await observer?.beforeInventoryCommit?.({ before, after })
          }
        )
        inventoryCommitted = true
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

      try { await observer?.afterInventoryCommit?.() }
      catch (error) { warn({ stage: 'afterInventoryCommit', message: describeError(error) }) }
      for (const item of staged) {
        if (!item.backupDir) continue
        const path = item.backupDir
        let cleanupError: string | undefined
        try {
          await options.authorizeTargetMutation?.([path], 'cleanup')
          await rm(path, { recursive: true, force: true })
          item.backupDir = null
        } catch (error) {
          cleanupError = describeError(error)
          warn({ stage: 'backupCleanup', path, message: cleanupError })
        }
        try {
          await observer?.cleanupResult?.({ path, removed: cleanupError === undefined,
            ...(cleanupError === undefined ? {} : { error: cleanupError }) })
        } catch (error) { warn({ stage: 'cleanupResult', path, message: describeError(error) }) }
      }
    } catch (error) {
      if (error instanceof InventoryCommitUnknownError) {
        preserveStaging = true
        throw new CliError('installation commit outcome is unknown; files retained for recovery', EXIT.filesystem, {
          reason: 'INSTALL_COMMIT_UNKNOWN', recovery: 'unknown',
          targets: staged.map(item => item.skillDir),
          retainedBackups: staged.flatMap(item => item.backupDir ? [item.backupDir] : []),
          stagingPaths: staged.map(item => item.tempDir),
          next: 'verify the inventory and retained directories before retrying'
        })
      }
      if (inventoryCommitted) {
        warn({ stage: 'afterInventoryCommit', message: describeError(error) })
      } else {
        const rollbackFailures: Array<{ operation: string; path: string; error: string }> = []
        for (const item of [...staged].reverse()) {
          if (item.movedIntoPlace) {
            try {
              await options.authorizeTargetMutation?.([item.skillDir], 'rollback')
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
              await options.authorizeTargetMutation?.([backupDir, item.skillDir], 'rollback')
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
      }
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
      warnings,
      transactionWarnings
    }
  } finally {
    for (const item of staged) {
      if (!preserveStaging && !item.movedIntoPlace) {
        try {
          await options.authorizeTargetMutation?.([item.tempDir], 'cleanup')
          await rm(item.tempDir, { recursive: true, force: true })
        } catch { /* Recovery must retain paths that policy no longer permits removing. */ }
      }
    }
  }
}

function inventoryChangedError(): CliError {
  return new CliError('installed skill records changed before commit', EXIT.validation, {
    next: 'reload the installed skill and create a new plan before retrying'
  })
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
