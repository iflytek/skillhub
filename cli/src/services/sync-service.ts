import { readdir, readFile, rename, rm, stat } from 'node:fs/promises'
import { basename, join } from 'node:path'
import { SkillHubClient, type NamespaceSyncItem } from '../clients/skillhub-client'
import { installSkill } from './install-service'
import { diffSkillFiles, snapshotSkillDirectory } from './skill-fingerprint'
import { InventoryStore } from '../stores/inventory-store'
import { SyncWorkspaceStore, type NamespaceSyncState } from '../stores/sync-workspace-store'
import { createZip, isZipFile } from '../platform/archive'
import { pathExists } from '../platform/paths'
import { compareSkillVersions } from './skill-version-order'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

export type SyncStatus = 'up-to-date' | 'update-available' | 'local-changed' | 'blocked' | 'orphaned' | 'not-installed'

export interface SkillSyncMetadata {
  registry: string
  namespace: string
  slug: string
  version: string
  fingerprint: string
  files?: Record<string, string>
  source?: string
}

export interface SyncStatusEntry {
  namespace: string
  slug: string
  status: SyncStatus
  localVersion?: string
  remoteVersion?: string
  changedFiles: string[]
  reason?: string
}

export interface PullResult {
  namespace: string
  rootDir: string
  entries: SyncStatusEntry[]
  actions: Array<{ slug: string; action: 'installed' | 'updated' | 'pruned' }>
  failures: Array<{ slug: string; message: string }>
  warnings: Array<{ slug: string; message: string }>
}

export interface PushResultItem {
  path: string
  slug?: string
  version?: string
  status?: string
  reviewStatus?: string
  action: 'validated' | 'uploaded' | 'submitted-review' | 'failed'
  errors?: string[]
  warnings?: string[]
}

export async function listAllNamespaceSkills(
  client: SkillHubClient,
  namespace: string
): Promise<NamespaceSyncItem[]> {
  const items: NamespaceSyncItem[] = []
  let cursor: string | undefined
  do {
    const page = await client.listNamespaceSkills(namespace, cursor, 100)
    items.push(...page.items)
    cursor = page.nextCursor ?? undefined
  } while (cursor)
  return items
}

export async function inspectNamespaceWorkspace(options: {
  client: SkillHubClient
  registry: string
  namespace: string
  rootDir: string
  remoteItems?: NamespaceSyncItem[]
}): Promise<{ entries: SyncStatusEntry[]; remoteItems: NamespaceSyncItem[] }> {
  const remoteItems = options.remoteItems ?? await listAllNamespaceSkills(options.client, options.namespace)
  const managed = await scanManagedSkills(options.rootDir, options.registry, options.namespace)
  const entries: SyncStatusEntry[] = []
  const remoteSlugs = new Set(remoteItems.map(item => item.slug))

  for (const remote of remoteItems) {
    const skillDir = join(options.rootDir, remote.slug)
    const metadata = managed.get(remote.slug)
    if (!(await pathExists(skillDir))) {
      entries.push(baseEntry(remote, 'not-installed'))
      continue
    }
    if (!metadata) {
      entries.push({ ...baseEntry(remote, 'local-changed'), reason: 'directory is not managed by SkillHub' })
      continue
    }

    const snapshot = await snapshotSkillDirectory(skillDir)
    const changedFiles = snapshot.fingerprint === metadata.fingerprint
      ? []
      : diffSkillFiles(metadata.files, snapshot.files)
    const versionOrder = compareSkillVersions(metadata.version, remote.version)
    if (versionOrder === 'remote-older') {
      entries.push({
        ...baseEntry(remote, 'blocked'),
        localVersion: metadata.version,
        changedFiles,
        reason: 'remote version is older than the installed version; local files were kept'
      })
      continue
    }
    if (versionOrder === 'unknown') {
      entries.push({
        ...baseEntry(remote, 'blocked'),
        localVersion: metadata.version,
        changedFiles,
        reason: 'cannot determine version order; use explicit install after verifying the release'
      })
      continue
    }
    if (versionOrder === 'same' && metadata.fingerprint !== remote.fingerprint) {
      entries.push({
        ...baseEntry(remote, 'blocked'),
        localVersion: metadata.version,
        changedFiles,
        reason: 'remote content changed without a newer version; use explicit install after verifying the release'
      })
      continue
    }
    if (snapshot.fingerprint !== metadata.fingerprint) {
      entries.push({
        ...baseEntry(remote, 'local-changed'),
        localVersion: metadata.version,
        changedFiles
      })
      continue
    }
    if (versionOrder === 'remote-newer') {
      entries.push({
        ...baseEntry(remote, 'update-available'),
        localVersion: metadata.version
      })
      continue
    }
    entries.push({ ...baseEntry(remote, 'up-to-date'), localVersion: metadata.version })
  }

  for (const [slug, metadata] of managed) {
    if (!remoteSlugs.has(slug)) {
      const snapshot = await snapshotSkillDirectory(join(options.rootDir, slug))
      const orphan: SyncStatusEntry = {
        namespace: options.namespace,
        slug,
        status: 'orphaned',
        localVersion: metadata.version,
        changedFiles: diffSkillFiles(metadata.files, snapshot.files)
      }
      if (snapshot.fingerprint !== metadata.fingerprint) orphan.reason = 'local changes detected'
      entries.push(orphan)
    }
  }

  entries.sort((left, right) => left.slug.localeCompare(right.slug))
  return { entries, remoteItems }
}

export async function pullNamespace(options: {
  client: SkillHubClient
  registry: string
  token: string
  namespace: string
  rootDir: string
  check: boolean
  prune: boolean
  force: boolean
  remoteItems?: NamespaceSyncItem[]
  selectedSlugs?: readonly string[]
  installSkillFn?: typeof installSkill
}): Promise<PullResult> {
  if (!options.check && (!options.selectedSlugs || options.selectedSlugs.length === 0)) {
    throw new CliError('mutating namespace pull requires at least one selected skill', EXIT.usage)
  }
  const inspected = await inspectNamespaceWorkspace({
    ...options,
    ...(options.remoteItems ? { remoteItems: options.remoteItems } : {})
  })
  const selectedSlugs = options.selectedSlugs ? new Set(options.selectedSlugs) : undefined
  const isSelected = (entry: SyncStatusEntry): boolean => !selectedSlugs || selectedSlugs.has(entry.slug)
  const result: PullResult = {
    namespace: options.namespace,
    rootDir: options.rootDir,
    entries: inspected.entries,
    actions: [],
    failures: inspected.entries
      .filter(entry => entry.status === 'blocked' && isSelected(entry))
      .map(entry => ({ slug: entry.slug, message: entry.reason ?? 'automatic sync is blocked' })),
    warnings: []
  }
  if (options.check) return result

  const remoteBySlug = new Map(inspected.remoteItems.map(item => [item.slug, item]))
  for (const entry of inspected.entries) {
    if (!isSelected(entry)) continue
    if (entry.status === 'up-to-date' || entry.status === 'orphaned') continue
    if (entry.status === 'blocked') continue
    if (entry.status === 'local-changed' && !options.force) {
      result.failures.push({ slug: entry.slug, message: entry.reason ?? 'local changes detected; pass --force to overwrite' })
      continue
    }
    const remote = remoteBySlug.get(entry.slug)
    if (!remote) continue
    try {
      const installed = await (options.installSkillFn ?? installSkill)({
        registry: options.registry,
        token: options.token,
        namespace: options.namespace,
        slug: remote.slug,
        version: remote.version,
        resolved: {
          namespace: remote.namespace,
          slug: remote.slug,
          version: remote.version,
          versionId: remote.versionId,
          fingerprint: remote.fingerprint,
          downloadUrl: remote.downloadUrl
        },
        targets: [{ agent: 'workspace', rootDir: options.rootDir, scope: 'project', source: 'explicit' }],
        force: entry.status !== 'not-installed' || options.force
      })
      result.actions.push({ slug: entry.slug, action: entry.status === 'not-installed' ? 'installed' : 'updated' })
      result.warnings.push(...(installed.warnings ?? []).map(message => ({ slug: entry.slug, message })))
    } catch (error) {
      result.failures.push({ slug: entry.slug, message: error instanceof Error ? error.message : 'install failed' })
    }
  }

  if (options.prune) {
    for (const entry of inspected.entries.filter(item => item.status === 'orphaned' && isSelected(item))) {
      if (entry.reason && !options.force) {
        result.failures.push({ slug: entry.slug, message: 'orphan has local changes; pass --force to prune' })
        continue
      }
      const installDir = join(options.rootDir, entry.slug)
      const backupDir = `${installDir}.skillhub-prune-${process.pid}-${Date.now()}`
      try {
        await rename(installDir, backupDir)
        try {
          await new InventoryStore().removeTargetsByInstallDir(installDir)
        } catch (error) {
          await rename(backupDir, installDir).catch(() => {})
          throw error
        }
        await rm(backupDir, { recursive: true, force: true }).catch(() => {})
        result.actions.push({ slug: entry.slug, action: 'pruned' })
      } catch (error) {
        result.failures.push({
          slug: entry.slug,
          message: error instanceof Error ? error.message : 'prune failed'
        })
      }
    }
  }

  if (result.failures.length === 0) {
    const managed = await scanManagedSkills(options.rootDir, options.registry, options.namespace)
    const state: NamespaceSyncState = {
      registry: options.registry,
      namespace: options.namespace,
      lastSyncAt: new Date().toISOString(),
      skills: Object.fromEntries([...managed.entries()]
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([slug, metadata]) => [slug, {
          version: metadata.version,
          fingerprint: metadata.fingerprint
        }]))
    }
    await new SyncWorkspaceStore(options.rootDir).write(state)
  }

  return result
}

export async function pushSkills(options: {
  client: SkillHubClient
  namespace: string
  paths: string[]
  visibility: 'PUBLIC' | 'NAMESPACE_ONLY' | 'PRIVATE'
  dryRun: boolean
  submitReview: boolean
}): Promise<PushResultItem[]> {
  const results: PushResultItem[] = []
  for (const path of options.paths) {
    try {
      const archive = await prepareArchive(path)
      const validation = await options.client.validatePublish(
        options.namespace, archive.blob, options.visibility, archive.fileName, true)
      if (!validation.valid) {
        const failed: PushResultItem = {
          path,
          action: 'failed',
          errors: validation.errors,
          warnings: validation.warnings
        }
        if (validation.resolvedSlug) failed.slug = validation.resolvedSlug
        if (validation.resolvedVersion) failed.version = validation.resolvedVersion
        results.push(failed)
        continue
      }
      if (options.dryRun) {
        const validated: PushResultItem = {
          path,
          action: 'validated',
          warnings: validation.warnings
        }
        if (validation.resolvedSlug) validated.slug = validation.resolvedSlug
        if (validation.resolvedVersion) validated.version = validation.resolvedVersion
        results.push(validated)
        continue
      }

      const published = await options.client.publish(
        options.namespace, archive.blob, options.visibility, archive.fileName, true)
      let action: PushResultItem['action'] = 'uploaded'
      const status = published.status
      let reviewStatus: string | undefined
      if (options.submitReview && published.status === 'PENDING_REVIEW') {
        action = 'submitted-review'
      } else if (options.submitReview && published.status === 'UPLOADED') {
        if (options.visibility === 'PRIVATE') {
          throw new Error('--submit-review requires public or namespace-only visibility')
        }
        const review = await options.client.submitReview(
          options.namespace,
          published.slug,
          published.version,
          options.visibility
        )
        action = 'submitted-review'
        reviewStatus = review.status
      }
      results.push({
        path,
        slug: published.slug,
        version: published.version,
        status,
        action,
        ...(reviewStatus ? { reviewStatus } : {})
      })
    } catch (error) {
      results.push({ path, action: 'failed', errors: [error instanceof Error ? error.message : 'push failed'] })
    }
  }
  return results
}

export async function discoverSkillDirectories(rootDir: string): Promise<string[]> {
  if (!(await pathExists(rootDir))) return []
  const entries = await readdir(rootDir, { withFileTypes: true })
  const paths: string[] = []
  for (const entry of entries) {
    if (!entry.isDirectory() || entry.name === '.skillhub') continue
    const path = join(rootDir, entry.name)
    if (await pathExists(join(path, 'SKILL.md'))) paths.push(path)
  }
  return paths.sort((left, right) => left.localeCompare(right))
}

async function prepareArchive(path: string): Promise<{ blob: Blob; fileName: string }> {
  const pathStat = await stat(path)
  if (pathStat.isDirectory()) {
    return {
      blob: await createZip(path, { exclude: relativePath => relativePath === '.skillhub' || relativePath.startsWith('.skillhub/') }),
      fileName: `${basename(path)}.zip`
    }
  }
  if (pathStat.isFile() && await isZipFile(path)) {
    return { blob: new Blob([await readFile(path)], { type: 'application/zip' }), fileName: basename(path) }
  }
  throw new Error(`path must be a skill directory or zip archive: ${path}`)
}

async function scanManagedSkills(
  rootDir: string,
  registry: string,
  namespace: string
): Promise<Map<string, SkillSyncMetadata>> {
  const managed = new Map<string, SkillSyncMetadata>()
  if (!(await pathExists(rootDir))) return managed
  const entries = await readdir(rootDir, { withFileTypes: true })
  for (const entry of entries) {
    if (!entry.isDirectory() || entry.name === '.skillhub') continue
    const metadataPath = join(rootDir, entry.name, '.skillhub', 'metadata.json')
    if (!(await pathExists(metadataPath))) continue
    try {
      const metadata = JSON.parse(await readFile(metadataPath, 'utf8')) as SkillSyncMetadata
      if (metadata.source === 'skillhub'
          && normalizeRegistry(metadata.registry) === normalizeRegistry(registry)
          && metadata.namespace === namespace
          && metadata.slug === entry.name) {
        managed.set(entry.name, metadata)
      }
    } catch {
      // Corrupt metadata is treated as an unmanaged local directory.
    }
  }
  return managed
}

function baseEntry(remote: NamespaceSyncItem, status: SyncStatus): SyncStatusEntry {
  return {
    namespace: remote.namespace,
    slug: remote.slug,
    status,
    remoteVersion: remote.version,
    changedFiles: []
  }
}

function normalizeRegistry(registry: string): string {
  return registry.replace(/\/+$/, '')
}
