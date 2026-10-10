import { mkdtemp, rename, rm } from 'node:fs/promises'
import { createHash, randomUUID } from 'node:crypto'
import { dirname, join, resolve } from 'node:path'
import { tmpdir } from 'node:os'
import { lock } from 'proper-lockfile'
import { SkillHubClient, type SuiteDetail, type SuiteInstallPlan } from '../clients/skillhub-client'
import {
  InventoryStore,
  installedBy,
  installedSuites,
  targetInstalledBy,
  type Inventory,
  type InventoryItem,
  type InventorySuite,
  type InventoryTarget
} from '../stores/inventory-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { installSkill } from './install-service'
import { pathExists, userStateDir } from '../platform/paths'
import { snapshotSkillDirectory } from './skill-fingerprint'
import { acquireSkillTargetLock, ensurePrivateLockDir } from './skill-target-lock'
import type { AgentCandidate } from '../agents/types'

const SUITE_CAPABILITY = 'skill-suite-v1'

export interface SuiteInstallOptions {
  registry: string
  token?: string | undefined
  namespace: string
  slug: string
  version?: string | undefined
  targets: AgentCandidate[]
  force: boolean
  home?: string | undefined
  client?: SkillHubClient | undefined
  /** Internal seam used to verify atomic rollback after a filesystem commit failure. */
  renameOperation?: typeof rename | undefined
  /** Internal seam used to verify state observed immediately after target locking. */
  afterTargetLocksAcquired?: (() => Promise<void>) | undefined
}

export interface SuiteInstallResult {
  plan: SuiteInstallPlan
  installed: Array<{ namespace: string; slug: string; agent: string; dir: string }>
  reused: Array<{ namespace: string; slug: string; agent: string; dir: string }>
}

export interface SuiteCheckResult {
  suite: InventorySuite
  remoteVersion?: string
  current: boolean
  installedVersionAvailable: boolean
  blockingReasons: string[]
  members: Array<{
    namespace: string
    slug: string
    version: string
    dir: string
    status: 'ok' | 'missing' | 'modified'
  }>
}

export interface SuiteRemoveResult {
  removed: string[]
  preserved: Array<{ dir: string; reason: 'shared' | 'modified' | 'missing' }>
}

export interface SuiteRemoveOptions {
  registry: string
  namespace: string
  slug: string
  home?: string | undefined
  /** Internal seam used to verify state observed immediately after target locking. */
  afterTargetLocksAcquired?: (() => Promise<void>) | undefined
}

export interface SuiteUpgradePlan {
  current: InventorySuite
  remote: SuiteDetail
  targets: AgentCandidate[]
  changes: Array<{
    coordinate: string
    action: 'add' | 'remove' | 'change'
    fromVersion?: string
    toVersion?: string
  }>
}

interface PreparedTarget {
  member: SuiteInstallPlan['members'][number]
  target: AgentCandidate
  installDir: string
  stagedDir: string
  replace: boolean
  reuse: boolean
  backupDir: string | undefined
  committed: boolean
}

interface RetiredTarget {
  item: InventoryItem
  target: InventoryTarget
  backupDir: string
  fingerprint: string
  moved: boolean
}

export function suiteSource(namespace: string, slug: string, version: string): string {
  return `suite:@${namespace}/${slug}@${version}`
}

export async function assertSuiteCapability(client: SkillHubClient): Promise<void> {
  const metadata = await client.serverMetadata()
  if (!metadata.capabilities?.includes(SUITE_CAPABILITY)) {
    throw new CliError('registry does not support Skill Suites', EXIT.validation, {
      capability: SUITE_CAPABILITY,
      next: 'upgrade the SkillHub server before using suite commands'
    })
  }
}

/**
 * Installs every exact member as one local transaction. Downloads and fingerprint checks finish
 * before any live Skill directory is replaced; commit failures restore all captured backups.
 */
export async function installSuite(options: SuiteInstallOptions): Promise<SuiteInstallResult> {
  const client = options.client ?? new SkillHubClient(options.registry, options.token)
  const renameOperation = options.renameOperation ?? rename
  await assertSuiteCapability(client)
  // The same client key survives the single HTTP retry; the Server owns the operation ID.
  const idempotencyKey = randomUUID()
  const plan = await client.suiteInstallPlan(
    options.namespace,
    options.slug,
    options.version,
    idempotencyKey
  )
  assertNoTargetCollisions(plan)

  return installSuiteWithPlan(options, client, renameOperation, plan)
}

async function installSuiteWithPlan(
  options: SuiteInstallOptions,
  client: SkillHubClient,
  renameOperation: typeof rename,
  plan: SuiteInstallPlan,
  expectedCurrentSuite?: InventorySuite,
  allowVersionReplacement = options.force
): Promise<SuiteInstallResult> {
  const releaseSuiteLock = await acquireSuiteOperationLock(
    options.home, options.registry, plan.namespace, plan.slug)
  try {
    return await installSuiteTransaction(
      options, client, renameOperation, plan, expectedCurrentSuite, allowVersionReplacement)
  } finally {
    await releaseSuiteLock().catch(() => {})
  }
}

async function installSuiteTransaction(
  options: SuiteInstallOptions,
  client: SkillHubClient,
  renameOperation: typeof rename,
  plan: SuiteInstallPlan,
  expectedCurrentSuite?: InventorySuite,
  allowVersionReplacement = options.force
): Promise<SuiteInstallResult> {
  const store = new InventoryStore(options.home)
  const before = await store.read()
  const previousSuite = installedSuites(before).find(candidate =>
    candidate.registry === options.registry && candidate.namespace === plan.namespace && candidate.slug === plan.slug)
  if (expectedCurrentSuite) {
    assertSuiteSnapshotUnchanged(expectedCurrentSuite, previousSuite)
  }
  const source = suiteSource(plan.namespace, plan.slug, plan.version)
  const stageHome = await mkdtemp(join(tmpdir(), 'skillhub-suite-inventory-'))
  const stageToken = `${process.pid}-${Date.now()}`
  const prepared: PreparedTarget[] = []
  const retired = await prepareRetiredTargets(before, previousSuite, plan, stageToken)

  try {
    await preflightExistingTargets(
      before, options.registry, plan, options.targets, options.force, allowVersionReplacement)

    for (const member of plan.members) {
      const stagingTargets = options.targets.map((target, index) => ({
        ...target,
        rootDir: join(resolve(target.rootDir), `.skillhub-suite-stage-${stageToken}-${index}`)
      }))
      await installSkill({
        registry: options.registry,
        token: options.token,
        namespace: member.namespace,
        slug: member.slug,
        version: member.version,
        targets: stagingTargets,
        force: false,
        home: stageHome,
        client,
        resolved: {
          namespace: member.namespace,
          slug: member.slug,
          version: member.version,
          versionId: member.skillVersionId,
          fingerprint: member.fingerprint,
          downloadUrl: member.downloadUrl
        }
      })

      for (let index = 0; index < options.targets.length; index += 1) {
        const target = options.targets[index]!
        const installDir = join(resolve(target.rootDir), member.slug)
        const stagedDir = join(stagingTargets[index]!.rootDir, member.slug)
        const reuse = await isReusable(before, options.registry, member, installDir)
        prepared.push({
          member,
          target: { ...target, rootDir: resolve(target.rootDir) },
          installDir,
          stagedDir,
          replace: await pathExists(installDir) && !reuse,
          reuse,
          committed: false,
          backupDir: undefined
        })
      }
    }

    const releases: Array<() => Promise<void>> = []
    try {
      const lockTargets = [
        ...prepared.map(item => ({ rootDir: item.target.rootDir, slug: item.member.slug })),
        ...retired.map(item => ({ rootDir: item.target.rootDir, slug: item.item.slug }))
      ].sort((a, b) => join(a.rootDir, a.slug).localeCompare(join(b.rootDir, b.slug)))
      const lockedPaths = new Set<string>()
      for (const target of lockTargets) {
        const path = join(resolve(target.rootDir), target.slug)
        if (lockedPaths.has(path)) continue
        lockedPaths.add(path)
        releases.push(await acquireSkillTargetLock(target.rootDir, target.slug))
      }
      await options.afterTargetLocksAcquired?.()
      // Recheck after target locks so a concurrent direct install cannot invalidate preflight.
      const lockedInventory = await store.read()
      const lockedPreviousSuite = installedSuites(lockedInventory).find(candidate =>
        candidate.registry === options.registry && candidate.namespace === plan.namespace && candidate.slug === plan.slug)
      assertSuiteSnapshotUnchanged(previousSuite, lockedPreviousSuite)
      await preflightExistingTargets(
        lockedInventory, options.registry, plan, options.targets, options.force, allowVersionReplacement)
      for (const item of prepared) {
        item.reuse = await isReusable(lockedInventory, options.registry, item.member, item.installDir)
        item.replace = await pathExists(item.installDir) && !item.reuse
      }
      const lockedRetired = await prepareRetiredTargets(
        lockedInventory, lockedPreviousSuite, plan, stageToken)
      retired.splice(0, retired.length, ...lockedRetired.filter(item =>
        lockedPaths.has(resolve(item.target.installDir))))

      for (const item of prepared) {
        if (item.reuse) {
          await rm(item.stagedDir, { recursive: true, force: true })
          continue
        }
        if (item.replace) {
          item.backupDir = `${item.installDir}.skillhub-suite-backup-${stageToken}`
          await renameOperation(item.installDir, item.backupDir)
        }
        await renameOperation(item.stagedDir, item.installDir)
        item.committed = true
      }
      for (const item of retired) {
        if (await pathExists(item.target.installDir)) {
          await renameOperation(item.target.installDir, item.backupDir)
          item.moved = true
          if ((await snapshotSkillDirectory(item.backupDir)).fingerprint !== item.fingerprint) {
            throw new CliError(`retired Suite member changed before commit: ${item.target.installDir}`, EXIT.validation, {
              path: item.target.installDir,
              next: 'restore the retained directory and retry the Suite upgrade'
            })
          }
        }
      }

      try {
        await store.mutateAtomic(inventory => commitInventory(
          inventory, options.registry, plan, source, prepared, retired))
      } catch (error) {
        await rollbackTransaction(prepared, retired, error, renameOperation)
        throw error
      }

      for (const item of prepared) {
        if (item.backupDir) {
          await rm(item.backupDir, { recursive: true, force: true }).catch(() => {})
          item.backupDir = undefined
        }
      }
      for (const item of retired) {
        await rm(item.backupDir, { recursive: true, force: true }).catch(() => {})
        item.moved = false
      }
    } catch (error) {
      if (prepared.some(item => item.committed || item.backupDir) || retired.some(item => item.moved)) {
        await rollbackTransaction(prepared, retired, error, renameOperation)
      }
      throw error
    } finally {
      for (const release of releases.reverse()) await release().catch(() => {})
    }

    return {
      plan,
      installed: prepared.filter(item => !item.reuse).map(item => ({
        namespace: item.member.namespace,
        slug: item.member.slug,
        agent: item.target.agent,
        dir: item.installDir
      })),
      reused: prepared.filter(item => item.reuse).map(item => ({
        namespace: item.member.namespace,
        slug: item.member.slug,
        agent: item.target.agent,
        dir: item.installDir
      }))
    }
  } finally {
    await rm(stageHome, { recursive: true, force: true }).catch(() => {})
    for (const item of prepared) {
      await rm(dirname(item.stagedDir), { recursive: true, force: true }).catch(() => {})
    }
  }
}

export async function checkSuite(options: {
  registry: string
  token?: string | undefined
  namespace: string
  slug: string
  home?: string | undefined
  client?: SkillHubClient | undefined
}): Promise<SuiteCheckResult> {
  const client = options.client ?? new SkillHubClient(options.registry, options.token)
  await assertSuiteCapability(client)
  const inventory = await new InventoryStore(options.home).read()
  const suite = findInstalledSuite(inventory, options.registry, options.namespace, options.slug)
  const installedRemote = await client.suiteDetail(options.namespace, options.slug, suite.version)
  let latestRemote: SuiteDetail | undefined
  try {
    latestRemote = await client.suiteDetail(options.namespace, options.slug)
  } catch (error) {
    if (!(error instanceof CliError) || error.details.status !== 404) throw error
  }
  const members: SuiteCheckResult['members'] = []
  for (const member of suite.members) {
    for (const installDir of member.installDirs) {
      let status: SuiteCheckResult['members'][number]['status'] = 'missing'
      if (await pathExists(installDir)) {
        status = (await snapshotSkillDirectory(installDir)).fingerprint === member.fingerprint
          ? 'ok'
          : 'modified'
      }
      members.push({
        namespace: member.namespace,
        slug: member.slug,
        version: member.version,
        dir: installDir,
        status
      })
    }
  }
  return {
    suite,
    ...(latestRemote ? { remoteVersion: latestRemote.version } : {}),
    current: latestRemote?.version === suite.version
      && installedRemote.available
      && members.every(member => member.status === 'ok'),
    installedVersionAvailable: installedRemote.available,
    blockingReasons: installedRemote.members
      .flatMap(member => member.blockingReason ? [member.blockingReason] : []),
    members
  }
}

export async function removeSuite(options: SuiteRemoveOptions): Promise<SuiteRemoveResult> {
  const releaseSuiteLock = await acquireSuiteOperationLock(
    options.home, options.registry, options.namespace, options.slug)
  try {
    return await removeSuiteTransaction(options)
  } finally {
    await releaseSuiteLock().catch(() => {})
  }
}

async function removeSuiteTransaction(options: SuiteRemoveOptions): Promise<SuiteRemoveResult> {
  const store = new InventoryStore(options.home)
  const inventory = await store.read()
  const suite = findInstalledSuite(inventory, options.registry, options.namespace, options.slug)
  const source = suiteSource(suite.namespace, suite.slug, suite.version)
  const candidates: Array<{
    item: InventoryItem
    target: InventoryTarget
    fingerprint: string
    backupDir: string
  }> = []
  const removable: typeof candidates = []
  const preserved: SuiteRemoveResult['preserved'] = []
  const token = `${process.pid}-${Date.now()}`

  for (const member of suite.members) {
    const item = inventory.items.find(candidate =>
      candidate.registry === options.registry && candidate.namespace === member.namespace && candidate.slug === member.slug)
    if (!item) continue
    for (const installDir of member.installDirs) {
      const target = item.targets.find(candidate => resolve(candidate.installDir) === resolve(installDir))
      if (!target) continue
      candidates.push({
        item,
        target,
        fingerprint: member.fingerprint,
        backupDir: `${installDir}.skillhub-suite-remove-${token}`
      })
    }
  }

  const releases: Array<() => Promise<void>> = []
  const moved: typeof removable = []
  try {
    for (const candidate of [...candidates].sort((a, b) => a.target.installDir.localeCompare(b.target.installDir))) {
      releases.push(await acquireSkillTargetLock(candidate.target.rootDir, candidate.item.slug))
    }
    await options.afterTargetLocksAcquired?.()
    const lockedInventory = await store.read()
    const lockedSuite = findInstalledSuite(
      lockedInventory, options.registry, options.namespace, options.slug)
    assertSuiteSnapshotUnchanged(suite, lockedSuite)
    for (const candidate of candidates) {
      const current = lockedInventory.items.find(item =>
        item.registry === candidate.item.registry && item.namespace === candidate.item.namespace &&
        item.slug === candidate.item.slug)
      const target = current?.targets.find(item =>
        resolve(item.installDir) === resolve(candidate.target.installDir))
      if (!current || !target || !(await pathExists(candidate.target.installDir))) {
        preserved.push({ dir: candidate.target.installDir, reason: 'missing' })
      } else if (targetInstalledBy(current, target).some(candidateSource => candidateSource !== source)) {
        preserved.push({ dir: candidate.target.installDir, reason: 'shared' })
      } else if ((await snapshotSkillDirectory(candidate.target.installDir)).fingerprint !== candidate.fingerprint) {
        preserved.push({ dir: candidate.target.installDir, reason: 'modified' })
      } else {
        removable.push({ ...candidate, item: current, target })
      }
    }
    for (const candidate of removable) {
      await rename(candidate.target.installDir, candidate.backupDir)
      moved.push(candidate)
      if ((await snapshotSkillDirectory(candidate.backupDir)).fingerprint !== candidate.fingerprint) {
        throw new CliError(`Suite member changed before removal: ${candidate.target.installDir}`, EXIT.validation, {
          path: candidate.target.installDir,
          next: 'restore the retained directory and run `skillhub suite check` before retrying'
        })
      }
    }
    await store.mutateAtomic(current => {
      current.suites = installedSuites(current).filter(candidate =>
        candidate.registry !== options.registry || candidate.namespace !== options.namespace || candidate.slug !== options.slug)
      for (const item of current.items) {
        if (item.registry !== options.registry) continue
        const deletedDirs = new Set(removable
          .filter(candidate => candidate.item.registry === item.registry &&
            candidate.item.namespace === item.namespace && candidate.item.slug === item.slug)
          .map(candidate => resolve(candidate.target.installDir)))
        item.targets = item.targets
          .filter(target => !deletedDirs.has(resolve(target.installDir)))
          .map(target => {
            const remainingSources = targetInstalledBy(item, target)
              .filter(candidate => candidate !== source)
            return { ...target, installedBy: remainingSources.length > 0 ? remainingSources : ['direct'] }
          })
        item.installedBy = Array.from(new Set(item.targets.flatMap(target => target.installedBy ?? [])))
      }
      current.items = current.items.filter(item => item.targets.length > 0)
    })
  } catch (error) {
    for (const candidate of moved.reverse()) {
      await rename(candidate.backupDir, candidate.target.installDir).catch(() => {})
    }
    throw error
  } finally {
    for (const release of releases.reverse()) await release().catch(() => {})
  }

  for (const candidate of removable) {
    await rm(candidate.backupDir, { recursive: true, force: true }).catch(() => {})
  }
  return { removed: removable.map(candidate => candidate.target.installDir), preserved }
}

export async function planSuiteUpgrade(options: {
  registry: string
  token?: string | undefined
  namespace: string
  slug: string
  home?: string | undefined
  client?: SkillHubClient | undefined
}): Promise<SuiteUpgradePlan> {
  const client = options.client ?? new SkillHubClient(options.registry, options.token)
  await assertSuiteCapability(client)
  const inventory = await new InventoryStore(options.home).read()
  const current = findInstalledSuite(inventory, options.registry, options.namespace, options.slug)
  const remote = await client.suiteDetail(options.namespace, options.slug)
  if (!remote.available) {
    throw new CliError(`Suite @${options.namespace}/${options.slug}@${remote.version} is unavailable`, EXIT.validation, {
      blockedMembers: remote.members.filter(member => member.blockingReason).map(member => ({
        coordinate: `@${member.namespace}/${member.slug}@${member.version}`,
        reason: member.blockingReason
      }))
    })
  }

  const currentMembers = new Map(current.members.map(member => [`${member.namespace}\u0000${member.slug}`, member]))
  const remoteMembers = new Map(remote.members.map(member => [`${member.namespace}\u0000${member.slug}`, member]))
  const changes: SuiteUpgradePlan['changes'] = []
  for (const [key, member] of remoteMembers) {
    const existing = currentMembers.get(key)
    if (!existing) {
      changes.push({ coordinate: `@${member.namespace}/${member.slug}`, action: 'add', toVersion: member.version })
    } else if (existing.version !== member.version || existing.fingerprint !== member.fingerprint) {
      changes.push({
        coordinate: `@${member.namespace}/${member.slug}`,
        action: 'change',
        fromVersion: existing.version,
        toVersion: member.version
      })
    }
  }
  for (const [key, member] of currentMembers) {
    if (!remoteMembers.has(key)) {
      changes.push({ coordinate: `@${member.namespace}/${member.slug}`, action: 'remove', fromVersion: member.version })
    }
  }
  return { current, remote, targets: installedSuiteTargets(inventory, current), changes }
}

export async function upgradeSuite(options: {
  registry: string
  token?: string | undefined
  namespace: string
  slug: string
  force?: boolean | undefined
  home?: string | undefined
  client?: SkillHubClient | undefined
}): Promise<{ upgrade: SuiteUpgradePlan; result?: SuiteInstallResult }> {
  const client = options.client ?? new SkillHubClient(options.registry, options.token)
  const upgrade = await planSuiteUpgrade({ ...options, client })
  if (upgrade.current.version === upgrade.remote.version && upgrade.changes.length === 0) {
    return { upgrade }
  }
  const installPlan = await client.suiteInstallPlan(
    options.namespace,
    options.slug,
    upgrade.remote.version,
    randomUUID()
  )
  assertNoTargetCollisions(installPlan)
  const result = await installSuiteWithPlan({
    ...options,
    version: upgrade.remote.version,
    targets: upgrade.targets,
    force: Boolean(options.force)
  }, client, rename, installPlan, upgrade.current, true)
  return { upgrade, result }
}

function findInstalledSuite(
  inventory: Inventory,
  registry: string,
  namespace: string,
  slug: string
): InventorySuite {
  const suite = installedSuites(inventory).find(candidate =>
    candidate.registry === registry && candidate.namespace === namespace && candidate.slug === slug)
  if (!suite) {
    throw new CliError(`Suite @${namespace}/${slug} is not installed`, EXIT.validation, {
      next: `run \`skillhub suite install @${namespace}/${slug}\``
    })
  }
  return suite
}

function installedSuiteTargets(inventory: Inventory, suite: InventorySuite): AgentCandidate[] {
  const dirs = new Set(suite.members.flatMap(member => member.installDirs).map(installDir => dirname(resolve(installDir))))
  const targets = new Map<string, AgentCandidate>()
  for (const item of inventory.items) {
    for (const target of item.targets) {
      if (!dirs.has(resolve(target.rootDir))) continue
      targets.set(resolve(target.rootDir), {
        agent: target.agent,
        rootDir: resolve(target.rootDir),
        scope: 'user',
        source: 'explicit'
      })
    }
  }
  if (targets.size === 0) {
    throw new CliError('installed Suite has no recoverable target directories', EXIT.validation, {
      next: 'remove the stale Suite inventory entry and install it again'
    })
  }
  return [...targets.values()]
}

function assertNoTargetCollisions(plan: SuiteInstallPlan): void {
  const seen = new Map<string, string>()
  for (const member of plan.members) {
    const coordinate = `@${member.namespace}/${member.slug}`
    const existing = seen.get(member.slug)
    if (existing && existing !== coordinate) {
      throw new CliError(`suite members ${existing} and ${coordinate} use the same local directory`, EXIT.validation, {
        slug: member.slug,
        next: 'publish a Suite version without colliding member slugs'
      })
    }
    seen.set(member.slug, coordinate)
  }
}

async function preflightExistingTargets(
  inventory: Inventory,
  registry: string,
  plan: SuiteInstallPlan,
  targets: AgentCandidate[],
  force: boolean,
  allowVersionReplacement: boolean
): Promise<void> {
  for (const member of plan.members) {
    const selectedDirs = new Set(targets.map(target => join(resolve(target.rootDir), member.slug)))
    const sameItem = inventory.items.find(item =>
      item.registry === registry &&
      item.namespace === member.namespace && item.slug === member.slug)
    if (sameItem && sameItem.version !== member.version) {
      const retained = sameItem.targets.filter(target => !selectedDirs.has(resolve(target.installDir)))
      if (retained.length > 0) {
        throw new CliError(`partial Suite install would split versions for @${member.namespace}/${member.slug}`, EXIT.validation, {
          retainedTargets: retained.map(target => target.installDir),
          next: 'select every installed target or keep the existing Suite version'
        })
      }
    }

    for (const target of targets) {
      const installDir = join(resolve(target.rootDir), member.slug)
      const owner = inventory.items.find(item =>
        item.targets.some(existing => resolve(existing.installDir) === installDir))
      if (!owner && await pathExists(installDir)) {
        throw new CliError(`unmanaged directory already exists at ${installDir}`, EXIT.validation, {
          path: installDir,
          next: 'move the directory or import it with `skillhub doctor` before installing the Suite'
        })
      }
      if (owner && (owner.registry !== registry || owner.namespace !== member.namespace || owner.slug !== member.slug)) {
        throw new CliError(`install target is owned by @${owner.namespace}/${owner.slug}`, EXIT.validation, {
          path: installDir,
          next: 'choose another target or remove the conflicting Skill explicitly'
        })
      }
      const currentSuitePrefix = `suite:@${plan.namespace}/${plan.slug}@`
      const ownerTarget = owner?.targets.find(existing => resolve(existing.installDir) === installDir)
      if (owner && ownerTarget && !force && await pathExists(installDir)
        && (await snapshotSkillDirectory(installDir)).fingerprint !== owner.fingerprint) {
        throw new CliError(`local changes detected at ${installDir}`, EXIT.validation, {
          path: installDir,
          next: 'pass --force only if replacing these local changes is intended'
        })
      }
      if (owner && ownerTarget && owner.version !== member.version && targetInstalledBy(owner, ownerTarget).some(source =>
        source.startsWith('suite:') && !source.startsWith(currentSuitePrefix))) {
        throw new CliError(`shared Suite member @${member.namespace}/${member.slug} cannot change version in place`, EXIT.validation, {
          path: installDir,
          next: 'install the Suite into another target or upgrade the sharing Suite first'
        })
      }
      if (owner && owner.version !== member.version && !allowVersionReplacement) {
        throw new CliError(`different Skill version already installed at ${installDir}`, EXIT.validation, {
          currentVersion: owner.version,
          requestedVersion: member.version,
          next: 'pass --force only if replacing this same Skill is intended'
        })
      }
    }
  }
}

async function isReusable(
  inventory: Inventory,
  registry: string,
  member: SuiteInstallPlan['members'][number],
  installDir: string
): Promise<boolean> {
  const item = inventory.items.find(candidate =>
    candidate.registry === registry && candidate.namespace === member.namespace && candidate.slug === member.slug &&
    candidate.version === member.version && candidate.fingerprint === member.fingerprint &&
    candidate.targets.some(target => resolve(target.installDir) === installDir))
  if (!item || !(await pathExists(installDir))) return false
  return (await snapshotSkillDirectory(installDir)).fingerprint === member.fingerprint
}

function commitInventory(
  inventory: Inventory,
  registry: string,
  plan: SuiteInstallPlan,
  source: string,
  prepared: PreparedTarget[],
  retired: RetiredTarget[]
): void {
  const suitePrefix = `suite:@${plan.namespace}/${plan.slug}@`
  for (const item of inventory.items) {
    if (item.registry === registry) {
      item.targets = item.targets.map(target => ({
        ...target,
        installedBy: targetInstalledBy(item, target)
          .filter(candidate => !candidate.startsWith(suitePrefix))
      }))
      item.installedBy = Array.from(new Set(item.targets.flatMap(target => target.installedBy ?? [])))
    }
  }
  for (const member of plan.members) {
    let item = inventory.items.find(candidate =>
      candidate.registry === registry && candidate.namespace === member.namespace && candidate.slug === member.slug)
    if (!item) {
      item = {
        registry,
        namespace: member.namespace,
        slug: member.slug,
        version: member.version,
        fingerprint: member.fingerprint,
        installedBy: [source],
        targets: []
      }
      inventory.items.push(item)
    }
    item.version = member.version
    item.fingerprint = member.fingerprint
    item.installedBy = Array.from(new Set([...installedBy(item), source]))
    for (const preparedTarget of prepared.filter(candidate => candidate.member === member)) {
      const target: InventoryTarget = {
        agent: preparedTarget.target.agent,
        rootDir: preparedTarget.target.rootDir,
        installDir: preparedTarget.installDir,
        installedAt: new Date().toISOString(),
        installedBy: [source]
      }
      const index = item.targets.findIndex(existing => resolve(existing.installDir) === preparedTarget.installDir)
      if (index >= 0) {
        target.installedBy = Array.from(new Set([
          ...targetInstalledBy(item, item.targets[index]!),
          source
        ]))
        item.targets[index] = target
      }
      else item.targets.push(target)
    }
    item.installedBy = Array.from(new Set(item.targets.flatMap(target => target.installedBy ?? [])))
  }

  const suite: InventorySuite = {
    registry,
    namespace: plan.namespace,
    slug: plan.slug,
    version: plan.version,
    fingerprint: plan.fingerprint,
    members: plan.members.map(member => ({
      namespace: member.namespace,
      slug: member.slug,
      version: member.version,
      fingerprint: member.fingerprint,
      installDirs: prepared.filter(item => item.member === member).map(item => item.installDir)
    }))
  }
  inventory.suites = installedSuites(inventory).filter(candidate =>
    candidate.registry !== registry || candidate.namespace !== plan.namespace || candidate.slug !== plan.slug)
  inventory.suites.push(suite)

  const retiredDirs = new Set(retired.map(item => resolve(item.target.installDir)))
  const preparedDirs = new Set(prepared.map(item => resolve(item.installDir)))
  for (const item of inventory.items) {
    item.targets = item.targets
      .filter(target => !retiredDirs.has(resolve(target.installDir)))
      .map(target => {
        const installDir = resolve(target.installDir)
        if ((target.installedBy?.length ?? 0) > 0 || preparedDirs.has(installDir)) return target
        // A retired directory that became shared or locally modified while waiting for locks is
        // preserved as user-owned instead of becoming eligible for a later automatic deletion.
        return { ...target, installedBy: ['direct'] }
      })
    item.installedBy = Array.from(new Set(item.targets.flatMap(target => target.installedBy ?? [])))
  }
  inventory.items = inventory.items.filter(item => item.targets.length > 0)
}

async function rollbackTransaction(
  prepared: PreparedTarget[],
  retired: RetiredTarget[],
  originalError: unknown,
  renameOperation: typeof rename
): Promise<void> {
  const failures: Array<{ path: string; error: string }> = []
  for (const item of [...retired].reverse()) {
    if (!item.moved) continue
    try {
      await renameOperation(item.backupDir, item.target.installDir)
      item.moved = false
    } catch (error) {
      failures.push({ path: item.backupDir, error: describe(error) })
    }
  }
  for (const item of [...prepared].reverse()) {
    try {
      if (item.committed) await rm(item.installDir, { recursive: true, force: true })
      if (item.backupDir) await renameOperation(item.backupDir, item.installDir)
      item.committed = false
      item.backupDir = undefined
    } catch (error) {
      failures.push({ path: item.backupDir ?? item.installDir, error: describe(error) })
    }
  }
  if (failures.length > 0) {
    throw new CliError('Suite installation failed and rollback was incomplete', EXIT.filesystem, {
      originalError: describe(originalError),
      rollbackFailures: failures,
      next: 'restore the retained backup directories before retrying'
    })
  }
}

async function prepareRetiredTargets(
  inventory: Inventory,
  previous: InventorySuite | undefined,
  next: SuiteInstallPlan,
  token: string
): Promise<RetiredTarget[]> {
  if (!previous) return []
  const nextCoordinates = new Set(next.members.map(member => `${member.namespace}\u0000${member.slug}`))
  const oldSource = suiteSource(previous.namespace, previous.slug, previous.version)
  const retired: RetiredTarget[] = []
  for (const member of previous.members) {
    if (nextCoordinates.has(`${member.namespace}\u0000${member.slug}`)) continue
    const item = inventory.items.find(candidate =>
      candidate.registry === previous.registry && candidate.namespace === member.namespace && candidate.slug === member.slug)
    if (!item) continue
    for (const installDir of member.installDirs) {
      const target = item.targets.find(candidate => resolve(candidate.installDir) === resolve(installDir))
      if (!target || !(await pathExists(installDir))) continue
      if (targetInstalledBy(item, target).some(source => source !== oldSource)) continue
      if ((await snapshotSkillDirectory(installDir)).fingerprint !== member.fingerprint) continue
      retired.push({
        item,
        target,
        backupDir: `${installDir}.skillhub-suite-retired-${token}`,
        fingerprint: member.fingerprint,
        moved: false
      })
    }
  }
  return retired
}

function assertSuiteSnapshotUnchanged(
  before: InventorySuite | undefined,
  locked: InventorySuite | undefined
): void {
  if (suiteSnapshot(before) === suiteSnapshot(locked)) return
  throw new CliError('installed Suite changed while waiting for target locks', EXIT.validation, {
    next: 'run `skillhub suite check` and retry'
  })
}

function suiteSnapshot(suite: InventorySuite | undefined): string {
  if (!suite) return ''
  const members = suite.members.map(member => ({
    namespace: member.namespace,
    slug: member.slug,
    version: member.version,
    fingerprint: member.fingerprint,
    installDirs: member.installDirs.map(installDir => resolve(installDir)).sort()
  })).sort((left, right) =>
    `${left.namespace}\0${left.slug}`.localeCompare(`${right.namespace}\0${right.slug}`))
  return JSON.stringify({
    registry: suite.registry,
    namespace: suite.namespace,
    slug: suite.slug,
    version: suite.version,
    fingerprint: suite.fingerprint,
    members
  })
}

/** Serializes local install, upgrade, and remove operations for one Suite inventory identity. */
async function acquireSuiteOperationLock(
  home: string | undefined,
  registry: string,
  namespace: string,
  slug: string
): Promise<() => Promise<void>> {
  const uid = typeof process.getuid === 'function' ? process.getuid() : 'user'
  const lockDir = join(tmpdir(), `skillhub-cli-suite-locks-${uid}`)
  await ensurePrivateLockDir(lockDir)
  const digest = createHash('sha256')
    .update(`${userStateDir(home)}\0${registry}\0${namespace}\0${slug}`)
    .digest('hex')
  const lockPath = join(lockDir, `${digest}.lock`)
  try {
    return await lock(lockPath, {
      lockfilePath: lockPath,
      realpath: false,
      stale: 30_000,
      update: 10_000,
      retries: 0
    })
  } catch (error) {
    if (error instanceof Error && 'code' in error && error.code === 'ELOCKED') {
      throw new CliError(`Suite operation is busy: @${namespace}/${slug}`, EXIT.filesystem, {
        next: 'wait for the other SkillHub CLI process to finish and retry'
      })
    }
    throw error
  }
}

function describe(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
