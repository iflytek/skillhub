import { mkdir, mkdtemp, open, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { SkillHubClient } from '../clients/skillhub-client'
import { InventoryStore } from '../stores/inventory-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { extractZip } from '../platform/archive'
import { readBoundedResponseBody } from '../platform/download'
import { canonicalizeExistingPath, pathExists } from '../platform/paths'
import { snapshotSkillDirectory } from './skill-fingerprint'
import {
  readInstalledSkillMetadata,
  sameInstalledSkillSource,
  type InstalledSkillIdentity
} from './installed-skill-metadata'
import type { AgentCandidate } from '../agents/types'
import type { ResolveResponse } from '../clients/skillhub-client'
import type { Inventory } from '../stores/inventory-store'

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
}

interface StagedInstall {
  target: AgentCandidate
  skillDir: string
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
): Promise<Array<{ target: AgentCandidate; skillDir: string }>> {
  const seenSkillDirs = new Set<string>()
  const preparedTargets: Array<{ target: AgentCandidate; skillDir: string }> = []

  for (const target of targets) {
    const canonicalRootDir = await canonicalizeExistingPath(target.rootDir)
    const canonicalSkillDir = join(canonicalRootDir, identity.slug)
    if (seenSkillDirs.has(canonicalSkillDir)) {
      throw new CliError(`multiple install targets resolve to ${canonicalSkillDir}`, EXIT.usage, {
        path: canonicalSkillDir,
        next: 'select only one target for this directory'
      })
    }
    seenSkillDirs.add(canonicalSkillDir)

    const skillDir = join(target.rootDir, identity.slug)
    const exists = await pathExists(skillDir)
    if (exists && !force) {
      throw new CliError(`skill already installed at ${skillDir}`, EXIT.filesystem, {
        path: skillDir,
        next: 'pass --force to replace a same-source installation'
      })
    }
    if (exists) {
      await assertReplaceableInstallation(skillDir, skillDir, identity, inventory)
    }
    preparedTargets.push({ target, skillDir })
  }

  return preparedTargets
}

export async function installSkill(options: InstallOptions): Promise<{ installed: Array<{ agent: string; dir: string }> }> {
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
    for (const { target, skillDir } of preparedTargets) {
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
        staged.push({ target, skillDir, tempDir, installedAt, backupDir: null, movedIntoPlace: false })
      } catch (error) {
        await rm(tempDir, { recursive: true, force: true }).catch(() => {})
        throw error
      }
    }

    const releases: Array<() => Promise<void>> = []
    try {
      for (const item of [...staged].sort((left, right) => left.skillDir.localeCompare(right.skillDir))) {
        releases.push(await acquireInstallTargetLock(item.target.rootDir, options.slug))
      }

      const lockedInventory = await store.read()
      assertNoPartialVersionChange(lockedInventory, staged, {
        registry: options.registry,
        namespace: options.namespace,
        slug: options.slug
      }, resolved)

      for (const item of staged) {
        if (await pathExists(item.skillDir)) {
          if (!options.force) {
            throw new CliError(`skill already installed at ${item.skillDir}`, EXIT.filesystem, {
              path: item.skillDir,
              next: 'pass --force to overwrite'
            })
          }
          item.backupDir = `${item.skillDir}.skillhub-backup-${process.pid}-${Date.now()}`
          await rename(item.skillDir, item.backupDir)
          await assertReplaceableInstallation(item.backupDir, item.skillDir, {
            registry: options.registry,
            namespace: options.namespace,
            slug: options.slug
          }, lockedInventory)
        }
        await rename(item.tempDir, item.skillDir)
        item.movedIntoPlace = true
      }

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
        resolved.fingerprint
      )

      for (const item of staged) {
        if (item.backupDir) await rm(item.backupDir, { recursive: true, force: true }).catch(() => {})
      }
    } catch (error) {
      for (const item of [...staged].reverse()) {
        if (item.movedIntoPlace) {
          await rm(item.skillDir, { recursive: true, force: true }).catch(() => {})
          item.movedIntoPlace = false
        }
        if (item.backupDir) await rename(item.backupDir, item.skillDir).catch(() => {})
      }
      throw error
    } finally {
      for (const release of releases.reverse()) await release()
    }

    return {
      installed: staged.map(item => ({ agent: item.target.agent, dir: item.skillDir }))
    }
  } finally {
    for (const item of staged) {
      if (!item.movedIntoPlace) await rm(item.tempDir, { recursive: true, force: true }).catch(() => {})
    }
  }
}

function assertNoPartialVersionChange(
  inventory: Inventory,
  staged: StagedInstall[],
  identity: InstalledSkillIdentity,
  resolved: ResolveResponse
): void {
  const item = inventory.items.find(candidate => sameInstalledSkillSource(candidate, identity))
  if (!item) return

  const selectedInstallDirs = new Set(staged.map(candidate => candidate.skillDir))
  const retainedTargets = item.targets.filter(target => !selectedInstallDirs.has(target.installDir))
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

  const inventoryOwners = inventory.items.filter(item =>
    item.targets.some(existing => existing.installDir === inventoryInstallDir))
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

async function acquireInstallTargetLock(rootDir: string, slug: string): Promise<() => Promise<void>> {
  const lockPath = join(rootDir, `.${slug}.skillhub-install.lock`)

  const createLock = async () => {
    const handle = await open(lockPath, 'wx')
    try {
      await handle.writeFile(JSON.stringify({ pid: process.pid, createdAt: new Date().toISOString() }))
      return handle
    } catch (error) {
      await handle.close().catch(() => {})
      await rm(lockPath, { force: true }).catch(() => {})
      throw error
    }
  }

  let handle: Awaited<ReturnType<typeof open>>
  try {
    handle = await createLock()
  } catch (error) {
    if (!(error instanceof Error && 'code' in error && error.code === 'EEXIST')) throw error

    let ownerIsRunning = true
    try {
      const lock = JSON.parse(await readFile(lockPath, 'utf-8')) as { pid?: unknown }
      if (typeof lock.pid !== 'number') throw new Error('invalid install lock')
      process.kill(lock.pid, 0)
    } catch (lockError) {
      if (lockError instanceof Error && 'code' in lockError && lockError.code === 'ESRCH') {
        ownerIsRunning = false
      }
    }

    if (ownerIsRunning) {
      throw new CliError(`install target is busy: ${join(rootDir, slug)}`, EXIT.filesystem, {
        path: join(rootDir, slug),
        next: 'wait for the other SkillHub CLI process to finish and retry'
      })
    }
    await rm(lockPath, { force: true })
    handle = await createLock()
  }

  return async () => {
    await handle.close().catch(() => {})
    await rm(lockPath, { force: true }).catch(() => {})
  }
}
